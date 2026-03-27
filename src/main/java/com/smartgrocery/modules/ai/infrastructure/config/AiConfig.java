package com.smartgrocery.modules.ai.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AiConfig — wires together all Spring AI infrastructure.
 *
 * Three beans created here:
 *
 * 1. VectorStore — pgvector backed. Stores purchase history
 * and spending data as embeddings for RAG.
 *
 * 2. ChatMemory — In-Memory fallback. Stores conversation turns
 * so recipe chat remembers context.
 *
 * 3. Both are injected into AiService in Step 20.
 */
@Slf4j
@Configuration
public class AiConfig {

        @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}")
        private int dimensions;

        // ── Bean 1 — VectorStore (pgvector) ──────────────────────────────────
        //
        // What it does:
        // - Stores text as float vectors (arrays of 1536 numbers)
        // - Each vector represents the MEANING of the text
        // - "milk, eggs, bread" and "dairy products for breakfast"
        // will have similar vectors even though words differ
        // - Used for: finding similar past shopping lists (RAG)
        //
        // JdbcTemplate: pgvector uses JDBC not R2DBC
        // EmbeddingModel: Spring AI auto-configures this from OpenAI config
        //
        @Bean
        public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                        EmbeddingModel embeddingModel) {
                log.info("Initialising PgVectorStore dimensions={}", dimensions);

                return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                                .dimensions(dimensions)
                                // Table name in PostgreSQL — matches V3 migration
                                .vectorTableName("vector_store")
                                // HNSW = Hierarchical Navigable Small World
                                // Fast approximate nearest neighbour search
                                // Finds similar vectors in milliseconds even with millions of rows
                                .indexType(PgVectorStore.PgIndexType.HNSW)
                                // Cosine similarity — measures angle between vectors
                                // Best for semantic similarity (meaning-based search)
                                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                                // Don't auto-create schema — Flyway manages it
                                .initializeSchema(false)
                                .build();
        }

        // ── Bean 2 — ChatMemory (In-Memory) ───────────────────────────────────────
        //
        // What it does:
        // - Stores the last N messages of a conversation in Memory
        // - Each conversation identified by sessionId
        // - AiService injects this into ChatClient for recipe chat
        //
        @Bean
        public ChatMemory chatMemory() {
                log.info("Initialising In-Memory ChatMemory (fallback since Redis store failed)");
                return new InMemoryChatMemory();
        }

        // ── Bean 3 — ChatClient (OpenAI) ──────────────────────────────────────
        //
        // What it does:
        // - High-level client for calling OpenAI ChatGPT
        // - Auto-injected with ChatMemory via MessageChatMemoryAdvisor
        // - Automatically retrieves & stores conversation history
        // - Sets system prompt for consistent behavior
        //
        // Advisor Pattern:
        // MessageChatMemoryAdvisor automatically:
        // 1. Before call: Gets previous messages from Memory
        // 2. Adds them to the prompt
        // 3. After call: Stores new message + response in Memory
        // This gives stateful conversation without manual code
        //
        @Bean
        public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
                log.info("Initialising ChatClient with memory advisor");

                return ChatClient.builder(chatModel)
                                .defaultSystem("You are a helpful AI assistant for the Smart Grocery Tracker app. " +
                                                "Help users with grocery shopping, expense tracking, and smart recommendations.")
                                .defaultAdvisors(
                                                new MessageChatMemoryAdvisor(chatMemory))
                                .build();
        }
}