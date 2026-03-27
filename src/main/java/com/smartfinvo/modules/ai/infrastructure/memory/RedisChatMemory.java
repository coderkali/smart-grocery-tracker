package com.smartfinvo.modules.ai.infrastructure.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "chat_memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public RedisChatMemory(
            @Qualifier("chatMemoryRedisTemplate") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.redis.chat-memory.ttl-hours:24}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        try {
            // Load existing messages
            List<Map<String, String>> stored = loadRaw(key);

            // Append new messages
            for (Message message : messages) {
                stored.add(Map.of(
                    "role",    message.getMessageType().getValue(),
                    "content", message.getText()
                ));
            }

            // Save back with TTL refresh
            String json = objectMapper.writeValueAsString(stored);
            redisTemplate.opsForValue().set(key, json, ttlHours, TimeUnit.HOURS);

            log.debug("Saved {} messages to chat memory key: {}", messages.size(), key);
        } catch (Exception e) {
            log.error("Failed to save chat memory for key: {}", key, e);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = KEY_PREFIX + conversationId;
        try {
            List<Map<String, String>> stored = loadRaw(key);

            // Take last N messages
            List<Map<String, String>> recent = stored.size() > lastN
                ? stored.subList(stored.size() - lastN, stored.size())
                : stored;

            // Convert back to Spring AI Message objects
            return recent.stream()
                .map(this::toMessage)
                .toList();

        } catch (Exception e) {
            log.error("Failed to load chat memory for key: {}", key, e);
            return List.of();
        }
    }

    @Override
    public void clear(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.debug("Cleared chat memory for key: {}", key);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private List<Map<String, String>> loadRaw(String key) {
        try {
            String existing = redisTemplate.opsForValue().get(key);
            if (existing == null) return new ArrayList<>();
            return objectMapper.readValue(
                existing,
                new TypeReference<List<Map<String, String>>>() {}
            );
        } catch (Exception e) {
            log.warn("Could not parse existing chat memory for key: {}", key);
            return new ArrayList<>();
        }
    }

    private Message toMessage(Map<String, String> raw) {
        String role    = raw.getOrDefault("role", "user");
        String content = raw.getOrDefault("content", "");
        return switch (role.toLowerCase()) {
            case "assistant" -> new AssistantMessage(content);
            default          -> new UserMessage(content);
        };
    }
}