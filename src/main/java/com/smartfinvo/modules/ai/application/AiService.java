package com.smartfinvo.modules.ai.application;

import com.smartfinvo.modules.ai.api.AiModulePort;
import com.smartfinvo.modules.ai.domain.AiConversation;
import com.smartfinvo.modules.ai.infrastructure.config.AiPrompts;
import com.smartfinvo.modules.ai.infrastructure.persistence.AiConversationRepository;
import com.smartfinvo.modules.ai.infrastructure.tools.GroceryTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
    return Mono.fromCallable(
            () -> {

              // Step 1 — Search pgvector for past purchases
              FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();
              SearchRequest searchRequest =
                  SearchRequest.builder()
                      .query("weekly grocery shopping household essentials Indian family")
                      .topK(5)
                      .filterExpression(filterExpressionBuilder.eq("userId", command.userId().toString()).build())
                      .build();

              List<Document> similarPurchases = vectorStore.similaritySearch(searchRequest);

              // Step 2 — Build context from past purchases
              String purchaseContext =
                  similarPurchases.stream()
                      .map(Document::getContent)
                      .collect(Collectors.joining("\n\n"));

              // Step 3 — Build what's already in the list
              String currentItemsText =
                  command.currentItems().isEmpty()
                      ? "Nothing added yet"
                      : String.join(", ", command.currentItems());

              // Step 4 — Ask GPT-4o for suggestions
              String systemPrompt =
                  AiPrompts.SUGGESTION_SYSTEM.formatted(
                      purchaseContext, currentItemsText, command.maxSuggestions());

              String response =
                  ChatClient.create(chatModel)
                      .prompt()
                      .system(systemPrompt)
                      .user("What should I add to my shopping list?")
                      .call()
                      .content();

              // Step 5 — Save conversation
              saveConversation(
                  command.userId(),
                  command.listId().toString(),
                  "SUGGESTION",
                  "user",
                  "Get smart suggestions",
                  0);
              saveConversation(
                  command.userId(),
                  command.listId().toString(),
                  "SUGGESTION",
                  "assistant",
                  response,
                  0);

              return new SuggestionResult(
                  List.of(
                      new SuggestedItem(
                          "See reasoning below", // name
                          "General", // category
                          0.8, // confidence
                          response // full AI response as reason for now
                          )),
                  response, // reasoning
                  0);
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public Flux<String> getRecipeSuggestion(RecipeCommand command) {
    return null;
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
