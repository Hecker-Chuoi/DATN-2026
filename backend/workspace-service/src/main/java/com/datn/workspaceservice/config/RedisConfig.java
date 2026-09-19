package com.datn.workspaceservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.UUID;

@Configuration
@EnableCaching
public class RedisConfig {

    private ObjectMapper createRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());

        // Key & HashKey dùng chuỗi String
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value & HashValue dùng JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());

        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // Cache ánh xạ Keycloak sub -> users.id của IdentityUserLookup.
        //
        // PHẢI dùng serializer riêng, KHÔNG dùng cacheConfig chung ở trên: bộ
        // GenericJackson2JsonRedisSerializer kia bật activateDefaultTyping với
        // NON_FINAL, mà UUID là class FINAL nên Jackson không ghi kèm type info.
        // Kết quả là giá trị đọc lại từ Redis về dưới dạng String và gây
        // ClassCastException ngay lần cache hit đầu tiên (lần miss thì vẫn chạy,
        // nên lỗi này chỉ hiện ra ở request thứ hai).
        //
        // GenericToStringSerializer đi qua DefaultConversionService, vốn đã có sẵn
        // converter String <-> UUID, nên round-trip đúng kiểu.
        //
        // TTL dài hơn default vì ánh xạ này gần như bất biến trong một phiên; mỗi
        // lần miss là một lời gọi Feign sang identity-service.
        RedisCacheConfiguration userIdCacheConfig = cacheConfig
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericToStringSerializer<>(UUID.class)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .withCacheConfiguration("wsUserId", userIdCacheConfig)
                .build();
    }
}

