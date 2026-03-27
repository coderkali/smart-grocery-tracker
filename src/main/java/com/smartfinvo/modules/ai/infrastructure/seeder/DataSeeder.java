package com.smartfinvo.modules.ai.infrastructure.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * DataSeeder — seeds real purchase history into pgvector for Feature 2 testing.
 *
 * Runs ONLY on local profile — never in production.
 * Implements ApplicationRunner so it runs AFTER Spring context is fully ready.
 *
 * Seeds two types of documents:
 *   1. Grocery purchase history  → powers Feature 2 (Smart Suggestions)
 *   2. Spending patterns         → powers Feature 4 (Budget Analysis)
 *
 * Data sourced from real February 2026 household expenses.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final VectorStore vectorStore;

    // Test userId — matches what we use in curl testing
    private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Override
    public void run(ApplicationArguments args) {
        log.info("DataSeeder starting — checking if seeding needed...");

        // Check if already seeded — avoid duplicate OpenAI calls on every restart
        SearchRequest checkRequest = SearchRequest.builder()
                .query("walmart grocery")
                .topK(1)
                .filterExpression(
                        new FilterExpressionBuilder()
                                .eq("userId", TEST_USER_ID)
                                .build()
                )
                .build();

        List<Document> existing = vectorStore.similaritySearch(checkRequest);

        if (!existing.isEmpty()) {
            log.info("DataSeeder skipping — data already seeded ({} docs found)", existing.size());
            return;
        }

        log.info("DataSeeder seeding fresh data...");
        seedGroceryPurchaseHistory();
        seedSpendingPatterns();
        log.info("DataSeeder complete — vector store populated with real Feb 2026 data");
    }

    // ── Grocery Purchase History ──────────────────────────────────────────
    // Each document = one real grocery shopping trip from Feb 2026
    // Content describes what was bought in natural language
    // pgvector converts this to 1536-dim embedding
    // Feature 2 finds similar past trips via cosine similarity

    private void seedGroceryPurchaseHistory() {
        log.info("Seeding grocery purchase history...");

        List<Document> purchases = List.of(

                // Week 1 — Feb 1: Walmart weekend grocery
                new Document(
                        "Weekly grocery shopping at Walmart. Bought: rice, dal, cooking oil, " +
                                "onions, tomatoes, vegetables, bread, milk, eggs, snacks for family. " +
                                "Typical weekend grocery run for Indian household. Total $70.22.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-01",
                                "store", "Walmart",
                                "amount", "70.22",
                                "week", "1"
                        )
                ),

                // Week 1 — Feb 1: Whole Foods supplemental
                new Document(
                        "Supplemental grocery at Whole Foods. Bought: organic vegetables, " +
                                "Greek yogurt, fresh fruits, whole grain bread. Total $12.68.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-01",
                                "store", "Whole Foods",
                                "amount", "12.68",
                                "week", "1"
                        )
                ),

                // Week 1 — Feb 2: Chicken/Meat purchase
                new Document(
                        "Meat purchase at Famous Meats. Bought: chicken breast 2lb, " +
                                "chicken thighs, halal chicken. Regular weekly chicken purchase. Total $10.40.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-02",
                                "store", "Famous Meats",
                                "amount", "10.40",
                                "week", "1"
                        )
                ),

                // Week 1 — Feb 6: Costco bulk buy
                new Document(
                        "Bulk purchase at Costco. Bought: coconut water 24 pack, " +
                                "protein bars, almonds bulk, olive oil large bottle. Total $32.00.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-06",
                                "store", "Costco",
                                "amount", "32.00",
                                "week", "1"
                        )
                ),

                // Week 1 — Feb 8: Chicken again
                new Document(
                        "Meat purchase at Famous Meats. Bought: whole chicken, chicken legs, " +
                                "minced meat. Indian cooking ingredients. Total $20.97.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-08",
                                "store", "Famous Meats",
                                "amount", "20.97",
                                "week", "1"
                        )
                ),

                // Week 1 — Feb 8: Indiaco Indian grocery
                new Document(
                        "Indian grocery shopping at Indiaco. Bought: basmati rice 10lb, " +
                                "toor dal, chana dal, spices, masala, atta flour, curry leaves, " +
                                "coriander, green chillies, ginger garlic paste. Total $21.56.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-08",
                                "store", "Indiaco",
                                "amount", "21.56",
                                "week", "1"
                        )
                ),

                // Week 1 — Feb 9: Whole Foods again
                new Document(
                        "Grocery at Whole Foods. Bought: fresh produce, organic milk, " +
                                "avocados, berries, salad greens, hummus. Total $25.40.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-09",
                                "store", "Whole Foods",
                                "amount", "25.40",
                                "week", "1"
                        )
                ),

                // Week 2 — Feb 13: Patel Brothers main grocery
                new Document(
                        "Weekly Indian grocery at Patel Brothers. Bought: basmati rice 20lb, " +
                                "multiple dals (moong, masoor, toor), paneer, Indian spices, " +
                                "besan flour, poha, vermicelli, Indian snacks, coconut milk, " +
                                "tamarind, jaggery, ghee. Main weekly Indian grocery run. Total $72.53.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-13",
                                "store", "Patel Brothers",
                                "amount", "72.53",
                                "week", "2"
                        )
                ),

                // Week 2 — Feb 15: Patel Brothers pooja items
                new Document(
                        "Patel Brothers purchase for pooja and household. Bought: agarbatti, " +
                                "camphor, flowers, coconut, banana leaves, milk for pooja, " +
                                "prasad items, rose water. Total $20.92.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-15",
                                "store", "Patel Brothers",
                                "amount", "20.92",
                                "week", "2"
                        )
                ),

                // Week 3 — Feb 21: Patel Brothers weekly grocery
                new Document(
                        "Weekly grocery at Patel Brothers. Bought: rice, vegetables, " +
                                "fresh coriander, mint, curry leaves, tomatoes, onions, potatoes, " +
                                "yogurt, paneer, Indian ready meals, snacks. Total $61.24.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-21",
                                "store", "Patel Brothers",
                                "amount", "61.24",
                                "week", "3"
                        )
                ),

                // Week 3 — Feb 21: Walmart weekly grocery
                new Document(
                        "Weekly grocery at Walmart. Bought: milk 1 gallon, eggs dozen, " +
                                "bread, butter, cheese, orange juice, cereal, pasta, tomato sauce, " +
                                "frozen vegetables, yogurt, snacks, baby items, household essentials. " +
                                "Total $76.00.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-21",
                                "store", "Walmart",
                                "amount", "76.00",
                                "week", "3"
                        )
                ),

                // Week 4 — Feb 25: Target grocery
                new Document(
                        "Evening grocery run at Target. Bought: milk, bread, eggs, " +
                                "fresh fruits, vegetables, snacks, juice boxes for kids, " +
                                "cleaning supplies, paper towels. Total $41.65.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "purchase",
                                "date", "2026-02-25",
                                "store", "Target",
                                "amount", "41.65",
                                "week", "4"
                        )
                )
        );

        vectorStore.add(purchases);
        log.info("Seeded {} grocery purchase documents", purchases.size());
    }

    // ── Spending Patterns ─────────────────────────────────────────────────
    // Summary documents describing spending by category
    // Powers Feature 4 — Budget Analysis

    private void seedSpendingPatterns() {
        log.info("Seeding spending pattern documents...");

        List<Document> patterns = List.of(

                // Overall February spending summary
                new Document(
                        "February 2026 total spending summary. " +
                                "Groceries and shopping: $427.49 across Walmart, Patel Brothers, " +
                                "Indiaco, Whole Foods, Costco, Target. " +
                                "Food and dining out: $264.46 at restaurants, fast food, coffee. " +
                                "Utilities: $384.00 including gas, electricity, water. " +
                                "Clothing: $288.68. Amazon shopping: $165.62. " +
                                "Transport (Metra, parking, toll): $203.96. " +
                                "Total monthly spend: $6,081.50.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "spending",
                                "period", "2026-02",
                                "category", "monthly_summary"
                        )
                ),

                // Grocery breakdown by store
                new Document(
                        "February 2026 grocery spending by store. " +
                                "Walmart: $146.22 across 2 trips. " +
                                "Patel Brothers: $154.69 across 3 trips. " +
                                "Indiaco: $21.56 one trip Indian groceries. " +
                                "Whole Foods: $38.08 across 2 trips. " +
                                "Costco: $32.00 bulk items. " +
                                "Target: $41.65 one trip. " +
                                "Famous Meats chicken: $31.37 across 2 visits. " +
                                "Total grocery: $427.49 of $500 monthly budget.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "spending",
                                "period", "2026-02",
                                "category", "groceries"
                        )
                ),

                // Frequency patterns
                new Document(
                        "February 2026 shopping frequency patterns. " +
                                "Grocery shopping every week — 4 weeks covered. " +
                                "Patel Brothers: 3 visits average $51.56 per visit. " +
                                "Walmart: 2 visits average $73.11 per visit. " +
                                "Chicken purchased every week from Famous Meats. " +
                                "Milk and eggs purchased weekly. " +
                                "Indian spices restocked every 2 weeks from Patel Brothers or Indiaco. " +
                                "Coconut water bulk bought monthly from Costco.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "spending",
                                "period", "2026-02",
                                "category", "frequency_patterns"
                        )
                ),

                // Dining out pattern
                new Document(
                        "February 2026 dining out spending $264.46 total. " +
                                "Chipotle: $13.52. McDonald's: $37.38 multiple visits. " +
                                "Starbucks: $16.76. Zaika Restaurant family dinner: $60.55. " +
                                "Mall food court multiple visits: $40+. " +
                                "Friday evenings regular family dinner outing observed. " +
                                "Dining out 3-4 times per week average.",
                        Map.of(
                                "userId", TEST_USER_ID,
                                "type", "spending",
                                "period", "2026-02",
                                "category", "dining"
                        )
                )
        );

        vectorStore.add(patterns);
        log.info("Seeded {} spending pattern documents", patterns.size());
    }
}
