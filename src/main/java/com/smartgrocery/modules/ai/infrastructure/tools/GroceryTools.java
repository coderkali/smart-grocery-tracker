package com.smartgrocery.modules.ai.infrastructure.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GroceryTools — Java methods that GPT-4o can call during conversations.
 *
 * Spring AI reads the @Tool annotation and tells GPT-4o:
 * "You have these functions available. Call them when appropriate."
 *
 * GPT-4o decides WHEN to call them based on user intent.
 * Spring AI handles the actual invocation — we just write normal Java.
 *
 * Tools available:
 * 1. addItemToGroceryList — adds one item (NLP search, recipe ingredients)
 * 2. removeItemFromList — removes an item by name
 * 3. getCurrentListItems — GPT-4o reads what's already in the list
 * 4. getBudgetInfo — GPT-4o reads current budget status
 * 5. searchItemInHistory — checks if user bought this item before
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroceryTools {

        // NOTE: These repositories will be injected in Step 26 (Grocery Module)
        // For now we use a simple in-memory simulation so the AI module
        // compiles and works end-to-end before Grocery module exists.
        // Replace with real repository calls in Step 26.

        // ── Tool 1 — Add item to grocery list ────────────────────────────────

        @Tool(description = """
                        Adds a single grocery item to the user's active grocery list.
                        Call this when the user wants to add items via natural language
                        or when adding recipe ingredients to their list.
                        Always extract quantity and unit from the user's message.
                        If unit is unclear, default to 'piece'.
                        """)
        public AddItemResult addItemToGroceryList(
                        @ToolParam(description = "The grocery list ID to add the item to") String listId,

                        @ToolParam(description = "Name of the grocery item e.g. milk, eggs, chicken breast") String itemName,

                        @ToolParam(description = "Numeric quantity e.g. 2.0, 1.5, 12.0") Double quantity,

                        @ToolParam(description = "Unit of measurement: litre, kg, gram, piece, pack, dozen, bottle, can") String unit,

                        @ToolParam(description = "Category: dairy, produce, meat, bakery, frozen, beverages, snacks, household, other") String category) {
                log.info("Tool called: addItemToGroceryList listId={} item={} qty={} {}",
                                listId, itemName, quantity, unit);

                // TODO Step 26 — replace with real GroceryItemRepository.save()

                return new AddItemResult(
                                true,
                                UUID.randomUUID().toString(), // simulated item ID
                                "Added %s (%.1f %s) to your list".formatted(itemName, quantity, unit));
        }

        public record AddItemResult(
                        boolean success,
                        String itemId,
                        String message) {
        }

        // ── Tool 2 — Remove item from grocery list ────────────────────────────

        @Tool(description = """
                        Removes a grocery item from the list by name.
                        Call this when user says things like 'remove milk' or 'delete eggs from my list'.
                        Search case-insensitively — 'Milk' and 'milk' are the same.
                        """)
        public RemoveItemResult removeItemFromGroceryList(
                        @ToolParam(description = "The grocery list ID") String listId,

                        @ToolParam(description = "Name of the item to remove") String itemName) {
                log.info("Tool called: removeItemFromGroceryList listId={} item={}", listId, itemName);

                // TODO Step 26 — replace with real repository call

                return new RemoveItemResult(
                                true,
                                "Removed %s from your list".formatted(itemName));
        }

        public record RemoveItemResult(
                        boolean success,
                        String message) {
        }

        // ── Tool 3 — Get current list items ──────────────────────────────────

        @Tool(description = """
                        Fetches all items currently in the user's grocery list.
                        Call this when you need to know what's already in the list
                        before making suggestions or checking for duplicates.
                        Also call this before adding recipe ingredients to avoid duplicates.
                        """)
        public ListItemsResult getCurrentListItems(
                        @ToolParam(description = "The grocery list ID") String listId) {
                log.info("Tool called: getCurrentListItems listId={}", listId);

                // TODO Step 26 — replace with real repository call

                // Simulated response for now
                List<ListItem> items = new ArrayList<>();
                items.add(new ListItem("milk", 2.0, "litre", "dairy"));
                items.add(new ListItem("bread", 1.0, "piece", "bakery"));
                items.add(new ListItem("eggs", 12.0, "piece", "dairy"));

                return new ListItemsResult(items, items.size());
        }

        public record ListItem(
                        String name,
                        Double quantity,
                        String unit,
                        String category) {
        }

        public record ListItemsResult(
                        List<ListItem> items,
                        int totalCount) {
        }

        // ── Tool 4 — Get budget info ──────────────────────────────────────────

        @Tool(description = """
                        Returns the user's current budget status for this month.
                        Call this when user asks about spending, budget, or
                        when suggesting items to be mindful of their budget.
                        Returns monthly budget, amount spent so far, and remaining.
                        """)
        public BudgetInfo getBudgetInfo(
                        @ToolParam(description = "The user's ID") String userId) {
                log.info("Tool called: getBudgetInfo userId={}", userId);

                // TODO Step 32 — replace with real BudgetRepository call

                // Simulated response for now
                return new BudgetInfo(275.00, 180.50, 94.50, "February 2026");
        }

        public record BudgetInfo(
                        Double monthlyBudget,
                        Double spentSoFar,
                        Double remaining,
                        String period) {
        }

        // ── Tool 5 — Search purchase history ─────────────────────────────────

        @Tool(description = """
                        Checks if the user has bought a specific item before
                        and how frequently. Use this when making suggestions
                        to tell the user 'you usually buy this' with confidence.
                        Returns purchase frequency and last purchase date.
                        """)
        public PurchaseHistoryResult searchPurchaseHistory(
                        @ToolParam(description = "The user's ID") String userId,

                        @ToolParam(description = "Item name to search for") String itemName) {
                log.info("Tool called: searchPurchaseHistory userId={} item={}", userId, itemName);

                // TODO Step 30 — replace with real PurchaseHistoryRepository call

                // Simulated response for now
                return new PurchaseHistoryResult(
                                true,
                                8,
                                "2026-02-14",
                                "weekly");
        }

        public record PurchaseHistoryResult(
                        boolean found,
                        int purchaseCount,
                        String lastPurchased,
                        String frequency // "weekly", "monthly", "occasionally"
        ) {
        }
}