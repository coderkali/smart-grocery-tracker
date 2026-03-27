package com.smartfinvo.modules.ai.infrastructure.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * GroceryTools — Java Functions that GPT-4o can call during conversations.
 *
 * <p>In Spring AI 1.0.0-M5, tools are defined as standard Function beans and registered with the
 * ChatClient explicitly.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GroceryTools {

  // NOTE: These repositories will be injected in Step 26 (Grocery Module)
  // For now we use a simple in-memory simulation so the AI module
  // compiles and works end-to-end before Grocery module exists.

  // ── Tool 1 — Add item to grocery list ────────────────────────────────

  public record AddItemRequest(
      String listId, String itemName, Double quantity, String unit, String category) {}

  public record AddItemResult(boolean success, String itemId, String message) {}

  @Bean
  @Description(
      """
            Adds a single grocery item to the user's active grocery list.
            Call this when the user wants to add items via natural language
            or when adding recipe ingredients to their list.
            Always extract quantity and unit from the user's message.
            If unit is unclear, default to 'piece'.
            """)
  public Function<AddItemRequest, AddItemResult> addItemToGroceryList() {
    return request -> {
      log.info(
          "Tool called: addItemToGroceryList listId={} item={} qty={} unit={}",
          request.listId(),
          request.itemName(),
          request.quantity(),
          request.unit());

      // TODO Step 26 — replace with real GroceryItemRepository.save()

      return new AddItemResult(
          true,
          UUID.randomUUID().toString(), // simulated item ID
          String.format(
              "Added %s (%.1f %s) to your list",
              request.itemName(), request.quantity(), request.unit()));
    };
  }

  // ── Tool 2 — Remove item from grocery list ────────────────────────

  public record RemoveItemRequest(String listId, String itemName) {}

  public record RemoveItemResult(boolean success, String message) {}

  @Bean
  @Description(
      """
            Removes a grocery item from the list by name.
            Call this when user says things like 'remove milk' or 'delete eggs from my list'.
            Search case-insensitively — 'Milk' and 'milk' are the same.
            """)
  public Function<RemoveItemRequest, RemoveItemResult> removeItemFromGroceryList() {
    return request -> {
      log.info(
          "Tool called: removeItemFromGroceryList listId={} item={}",
          request.listId(),
          request.itemName());

      // TODO Step 26 — replace with real repository call

      return new RemoveItemResult(
          true, String.format("Removed %s from your list", request.itemName()));
    };
  }

  // ── Tool 3 — Get current list items ──────────────────────────────────

  public record GetListItemsRequest(String listId) {}

        public record ListItem(
                        String name,
                        Double quantity,
                        String unit,
                        String category) {
        }

  public record ListItemsResult(List<ListItem> items, int totalCount) {}

  @Bean
  @Description(
      """
            Fetches all items currently in the user's grocery list.
            Call this when you need to know what's already in the list
            before making suggestions or checking for duplicates.
            Also call this before adding recipe ingredients to avoid duplicates.
            """)
  public Function<GetListItemsRequest, ListItemsResult> getCurrentListItems() {
    return request -> {
      log.info("Tool called: getCurrentListItems listId={}", request.listId());

      // TODO Step 26 — replace with real repository call

                        // Simulated response for now
                        List<ListItem> items = new ArrayList<>();
                        items.add(new ListItem("milk", 2.0, "litre", "dairy"));
                        items.add(new ListItem("bread", 1.0, "piece", "bakery"));
                        items.add(new ListItem("eggs", 12.0, "piece", "dairy"));

      return new ListItemsResult(items, items.size());
    };
  }

  // ── Tool 4 — Get budget info ──────────────────────────────────────────

  public record GetBudgetRequest(String userId) {}

        public record BudgetInfo(
                        Double monthlyBudget,
                        Double spentSoFar,
                        Double remaining,
                        String period) {
        }

  @Bean
  @Description(
      """
            Returns the user's current budget status for this month.
            Call this when user asks about spending, budget, or
            when suggesting items to be mindful of their budget.
            Returns monthly budget, amount spent so far, and remaining.
            """)
  public Function<GetBudgetRequest, BudgetInfo> getBudgetInfo() {
    return request -> {
      log.info("Tool called: getBudgetInfo userId={}", request.userId());

      // TODO Step 32 — replace with real BudgetRepository call

                        // Simulated response for now
                        return new BudgetInfo(275.00, 180.50, 94.50, "February 2026");
                };
        }

  // ── Tool 5 — Search purchase history ─────────────────────────────

  public record SearchHistoryRequest(String userId, String itemName) {}

  public record PurchaseHistoryResult(
      boolean found, int purchaseCount, String lastPurchased, String frequency) {}

  @Bean
  @Description(
      """
            Checks if the user has bought a specific item before
            and how frequently. Use this when making suggestions
            to tell the user 'you usually buy this' with confidence.
            Returns purchase frequency and last purchase date.
            """)
  public Function<SearchHistoryRequest, PurchaseHistoryResult> searchPurchaseHistory() {
    return request -> {
      log.info(
          "Tool called: searchPurchaseHistory userId={} item={}",
          request.userId(),
          request.itemName());

      // TODO Step 30 — replace with real PurchaseHistoryRepository call

      // Simulated response for now
      return new PurchaseHistoryResult(true, 8, "2026-02-14", "weekly");
    };
  }
}
