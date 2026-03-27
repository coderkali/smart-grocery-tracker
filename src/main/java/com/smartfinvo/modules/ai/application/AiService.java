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

import java.util.List;
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
    return null;
  }

  @Override
  public Flux<ConversationTurn> getConversationHistory(UUID userId, String sessionId) {
    return null;
  }
}
