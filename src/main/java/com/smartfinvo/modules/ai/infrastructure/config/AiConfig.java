package com.smartfinvo.modules.ai.infrastructure.config;

import com.smartfinvo.modules.ai.infrastructure.memory.RedisChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

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

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    // ── Bean 2 — ChatMemory (In-Memory) ───────────────────────────────────────
    //
    // What it does:
    // - Stores the last N messages of a conversation in Memory
    // - Each conversation identified by sessionId
    // - AiService injects this into ChatClient for recipe chat
    //
    @Bean
    public ChatMemory chatMemory(RedisChatMemory redisChatMemory) {
        return redisChatMemory;
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
        log.info("Initialising ChatClient with memory advisor and functions");

        return ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful AI assistant for the Smart Grocery Tracker app. " +
                        "Help users with grocery shopping, expense tracking, and smart recommendations.")
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory))
                .defaultFunctions(
                        "addItemToGroceryList",
                        "removeItemFromGroceryList",
                        "getCurrentListItems",
                        "getBudgetInfo",
                        "searchPurchaseHistory")
                .build();
    }

    /**
     * Explicit DataSource for pgvector + JdbcTemplate.
     * Spring Boot backs off DataSource auto-configuration
     * when R2DBC is present — so we define it manually here.
     */
    @Bean
    public DataSource dataSource() {
        log.info("Initialising JDBC DataSource for pgvector url={}", datasourceUrl);
        return DataSourceBuilder.create()
                .url(datasourceUrl)
                .username(datasourceUsername)
                .password(datasourcePassword)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        log.info("Initialising JdbcTemplate for pgvector");
        return new JdbcTemplate(dataSource);
    }
}