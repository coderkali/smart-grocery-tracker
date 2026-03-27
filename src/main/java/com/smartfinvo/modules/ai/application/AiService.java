package com.smartfinvo.modules.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfinvo.modules.ai.api.AiModulePort;
import com.smartfinvo.modules.ai.domain.AiConversation;
import com.smartfinvo.modules.ai.infrastructure.config.AiPrompts;
import com.smartfinvo.modules.ai.infrastructure.persistence.AiConversationRepository;
import com.smartfinvo.modules.ai.infrastructure.tools.GroceryTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService implements AiModulePort {

  private final ChatModel chatModel;
  private final ChatMemory chatMemory;
  private final VectorStore vectorStore;
  private final GroceryTools groceryTools;
  private final AiConversationRepository aiConversationRepository;
  private final ObjectMapper objectMapper;

  @Override
  public Mono<NlpSearchResult> naturalLanguageSearch(NlpSearchCommand cmd) {
    log.info(
        "NLP search userId={} listId={} message='{}'",
        cmd.userId(),
        cmd.listId(),
        cmd.userMessage());

    return Mono.fromCallable(
            () -> {
              ChatClient client =
                  ChatClient.builder(chatModel)
                      .defaultSystem(AiPrompts.NLP_SEARCH_SYSTEM.formatted(cmd.listId()))
                      .build();

              String aiResponse =
                  client
                      .prompt()
                      .user(cmd.userMessage())
                      .functions(
                          "addItemToGroceryList",
                          "removeItemFromGroceryList",
                          "getCurrentListItems",
                          "getBudgetInfo",
                          "searchPurchaseHistory")
                      .call()
                      .content();
              log.info("NLP search complete userId={} response='{}'", cmd.userId(), aiResponse);

              return aiResponse;
            })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            aiResponse ->
                saveConversation(
                        cmd.userId(),
                        cmd.sessionId(),
                        "NLP_SEARCH",
                        cmd.userMessage(),
                        aiResponse,
                        null)
                    // Step 6 — Return result to controller
                    .thenReturn(
                        new NlpSearchResult(
                            aiResponse,
                            List.of(), // items parsed — populate in Step 21
                            0 // tokens — populate in Step 21
                            )));
  }

  private Mono<Void> saveConversation(
      UUID userId,
      String sessionId,
      String feature,
      String userMessage,
      String aiResponse,
      Integer tokensUsed) {
    AiConversation userTurn =
        AiConversation.builder()
            .userId(userId)
            .sessionId(sessionId)
            .feature(feature)
            .role("USER")
            .content(userMessage)
            .tokensUsed(null) // we only track tokens for the AI response
            .build();

    AiConversation aiTurn =
        AiConversation.builder()
            .userId(userId)
            .sessionId(sessionId)
            .feature(feature)
            .role("ASSISTANT")
            .content(aiResponse)
            .tokensUsed(tokensUsed)
            .build();

    return aiConversationRepository
        .save(userTurn)
        .then(aiConversationRepository.save(aiTurn))
        .then();
  }

    public Mono<SuggestionResult> getSmartSuggestions(SuggestionCommand command) {
        return Mono.fromCallable(() -> {

            // Step 1 — Search pgvector
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("weekly grocery shopping household essentials Indian family")
                    .topK(5)
                    .filterExpression(b.eq("userId", command.userId().toString()).build())
                    .build();

            List<Document> similarPurchases = vectorStore.similaritySearch(searchRequest);

            // Step 2 — Build context
            String purchaseContext = similarPurchases.isEmpty()
                    ? "No purchase history found."
                    : similarPurchases.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining("\n\n"));

            String currentItemsText = command.currentItems().isEmpty()
                    ? "Nothing added yet"
                    : String.join(", ", command.currentItems());

            // Step 3 — Call GPT-4o
            String systemPrompt = AiPrompts.SUGGESTION_SYSTEM.formatted(
                    purchaseContext, currentItemsText, command.maxSuggestions()
            );

            String response = ChatClient.create(chatModel)
                    .prompt()
                    .system(systemPrompt)
                    .user("What should I add to my shopping list?")
                    .call()
                    .content();

            // Step 4 — Parse JSON response into SuggestedItem list
            List<SuggestedItem> suggestions = parseSuggestions(response);

            // Step 5 — Save conversation
            saveConversation(command.userId(), command.listId().toString(),
                    "SUGGESTION", "user", "Get smart suggestions", 0);
            saveConversation(command.userId(), command.listId().toString(),
                    "SUGGESTION", "assistant", response, 0);

            return new SuggestionResult(suggestions, response, 0);

        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── JSON Parser ───────────────────────────────────────────────────────
    private List<SuggestedItem> parseSuggestions(String json) {
        try {
            // Strip markdown fences if GPT-4o adds them despite instructions
            String clean = json
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readValue(
                    clean,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, SuggestedItem.class
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to parse suggestions JSON, returning empty list. Response was: {}", json);
            return List.of();
        }
    }

    @Override
    public Flux<String> getRecipeSuggestion(RecipeCommand command) {
        return Mono.fromCallable(() -> {

                    // Step 1 — Search pgvector for past purchases
                    // Used to infer what ingredients user likely has at home
                    FilterExpressionBuilder b = new FilterExpressionBuilder();
                    SearchRequest searchRequest = SearchRequest.builder()
                            .query("grocery ingredients cooking items purchased")
                            .topK(5)
                            .filterExpression(
                                    b.eq("userId", command.userId().toString()).build()
                            )
                            .build();

                    List<Document> pastPurchases = vectorStore.similaritySearch(searchRequest);

                    String purchaseContext = pastPurchases.isEmpty()
                            ? "No purchase history found."
                            : pastPurchases.stream()
                            .map(Document::getContent)
                            .collect(Collectors.joining("\n\n"));

                    String currentItemsText = command.currentItems().isEmpty()
                            ? "Nothing in list yet"
                            : String.join(", ", command.currentItems());

                    return AiPrompts.RECIPE_SYSTEM.formatted(purchaseContext, currentItemsText);

                })
                .flatMapMany(systemPrompt ->

                        // Step 2 — Stream response from GPT-4o with chat memory
                        // MessageChatMemoryAdvisor automatically:
                        //   - loads previous turns from Redis DB 1 before sending
                        //   - saves new turns to Redis DB 1 after response
                        ChatClient.create(chatModel)
                                .prompt()
                                .system(systemPrompt)
                                .user(command.userMessage())
                                .advisors(new MessageChatMemoryAdvisor(
                                        chatMemory,           // our RedisChatMemory bean
                                        command.sessionId(),  // conversation key in Redis
                                        20                    // remember last 20 messages
                                ))
                                .stream()
                                .content()

                )
                .doOnNext(chunk ->
                        // Step 3 — Log each chunk for debugging
                        log.debug("Recipe stream chunk: {}", chunk)
                )
                .doOnComplete(() ->
                        // Step 4 — Save USER turn to PostgreSQL audit trail
                        // Note: assistant turn is too large to capture from stream easily
                        // We save user message only for audit
                        saveConversation(
                                command.userId(),
                                command.sessionId(),
                                "RECIPE",
                                "user",
                                command.userMessage(),
                                0
                        )
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<BudgetAnalysisResult> analyzeBudget(BudgetAnalysisCommand command) {
        return Mono.fromCallable(() -> {

            // Step 1 — Search pgvector for spending data
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("spending summary grocery budget monthly expenses")
                    .topK(6)
                    .filterExpression(
                            b.eq("userId", command.userId().toString()).build()
                    )
                    .build();

            List<Document> spendingDocs = vectorStore.similaritySearch(searchRequest);

            String spendingContext = spendingDocs.isEmpty()
                    ? "No spending data found."
                    : spendingDocs.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining("\n\n"));

            String monthlyBudget = command.monthlyBudget() != null
                    ? command.monthlyBudget().toPlainString()
                    : "Not set";

            // Step 2 — Ask GPT-4o to analyse spending
            String systemPrompt = AiPrompts.BUDGET_ANALYSIS_SYSTEM.formatted(
                    spendingContext,
                    monthlyBudget,
                    command.period()
            );

            String analysisResponse = ChatClient.create(chatModel)
                    .prompt()
                    .system(systemPrompt)
                    .user("Analyse my spending and give me insights")
                    .call()
                    .content();

            // Step 3 — Evaluator pass — verify AI didn't hallucinate numbers
            String evaluatorPrompt = AiPrompts.BUDGET_EVALUATOR_SYSTEM.formatted(
                    spendingContext,
                    analysisResponse
            );

            String evaluatorResponse = ChatClient.create(chatModel)
                    .prompt()
                    .system(evaluatorPrompt)
                    .user("Verify this analysis")
                    .call()
                    .content();

            // Step 4 — Parse evaluator result
            boolean evaluatorPassed = parseEvaluatorResult(evaluatorResponse);

            // Step 5 — Parse analysis into structured result
            BudgetAnalysisResult result = parseAnalysisResult(
                    analysisResponse,
                    evaluatorPassed,
                    command.monthlyBudget()
            );

            // Step 6 — Save to conversation history
            saveConversation(command.userId(), command.period(),
                    "BUDGET", "user", "Analyse my budget", 0);
            saveConversation(command.userId(), command.period(),
                    "BUDGET", "assistant", analysisResponse, 0);

            return result;

        }).subscribeOn(Schedulers.boundedElastic());
    }

// ── Evaluator Parser ──────────────────────────────────────────────────

    private boolean parseEvaluatorResult(String json) {
        try {
            String clean = json.replaceAll("```json", "").replaceAll("```", "").trim();
            Map<String, Object> result = objectMapper.readValue(clean, Map.class);
            boolean passed = (boolean) result.getOrDefault("passed", false);
            if (!passed) {
                List<String> issues = (List<String>) result.getOrDefault("issues", List.of());
                log.warn("Evaluator FAILED — issues: {}", issues);
            }
            return passed;
        } catch (Exception e) {
            log.error("Failed to parse evaluator response: {}", json, e);
            return false;
        }
    }

// ── Analysis Parser ───────────────────────────────────────────────────

    private BudgetAnalysisResult parseAnalysisResult(
            String json,
            boolean evaluatorPassed,
            BigDecimal monthlyBudget) {
        try {
            String clean = json.replaceAll("```json", "").replaceAll("```", "").trim();
            Map<String, Object> parsed = objectMapper.readValue(clean, Map.class);

            String summary = (String) parsed.getOrDefault("summary", "No summary available");
            double totalSpentRaw = ((Number) parsed.getOrDefault("totalSpent", 0)).doubleValue();
            BigDecimal totalSpent = BigDecimal.valueOf(totalSpentRaw);

            // Calculate budget variance
            BigDecimal budgetVariance = monthlyBudget != null
                    ? totalSpent.subtract(monthlyBudget)
                    : BigDecimal.ZERO;

            // Parse insights
            List<Map<String, String>> rawInsights =
                    (List<Map<String, String>>) parsed.getOrDefault("insights", List.of());

            List<Insight> insights = rawInsights.stream()
                    .map(i -> new Insight(
                            i.getOrDefault("category", "General"),
                            i.getOrDefault("finding", ""),
                            i.getOrDefault("suggestion", ""),
                            parseInsightType(i.getOrDefault("type", "PATTERN"))
                    ))
                    .toList();

            return new BudgetAnalysisResult(
                    summary,
                    insights,
                    totalSpent,
                    budgetVariance,
                    evaluatorPassed,
                    0
            );

        } catch (Exception e) {
            log.error("Failed to parse budget analysis response: {}", json, e);
            return new BudgetAnalysisResult(
                    "Failed to parse analysis. Please try again.",
                    List.of(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    false,
                    0
            );
        }
    }

    private InsightType parseInsightType(String type) {
        try {
            return InsightType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            return InsightType.PATTERN;
        }
    }

  @Override
  public Flux<ConversationTurn> getConversationHistory(UUID userId, String sessionId) {
    return null;
  }
}
