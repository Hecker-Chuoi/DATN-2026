package com.datn.notification.cluster;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Chỉ khi chạy nhiều instance: đăng ký nghe kênh đẩy WebSocket trên Redis. */
@Configuration
@ConditionalOnProperty(name = "app.cluster.redis-enabled", havingValue = "true")
public class RedisClusterConfig {

    @Bean
    public RedisMessageListenerContainer notificationPushListenerContainer(RedisConnectionFactory connectionFactory,
                                                                          RedisPushRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new ChannelTopic(RedisPushRelay.CHANNEL));
        return container;
    }
}
