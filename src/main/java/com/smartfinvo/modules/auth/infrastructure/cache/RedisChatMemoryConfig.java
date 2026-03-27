package com.smartfinvo.modules.auth.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisChatMemoryConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${app.redis.chat-memory.database:1}")
    private int chatMemoryDatabase;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    // Separate connection factory pointing to DB 1
    @Bean("chatMemoryRedisConnectionFactory")
    public LettuceConnectionFactory chatMemoryRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(chatMemoryDatabase);  // DB 1
        // Set password if configured
        if (redisPassword != null && !redisPassword.isBlank()) {
            config.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(config);
    }

    // Separate StringRedisTemplate using DB 1 connection
    @Bean("chatMemoryRedisTemplate")
    public StringRedisTemplate chatMemoryRedisTemplate() {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(chatMemoryRedisConnectionFactory());
        return template;
    }
}