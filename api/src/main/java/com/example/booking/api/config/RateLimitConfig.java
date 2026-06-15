package com.example.booking.api.config;

import com.example.booking.api.auth.filter.LoginRateLimitFilter;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RateLimitConfig {

    @Bean
    public ProxyManager<String> rateLimitProxyManager(LettuceConnectionFactory connectionFactory) {
        RedisClient redisClient = (RedisClient) connectionFactory.getNativeClient();
        StatefulRedisConnection<String, byte[]> connection =
                redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(connection).build();
    }

    @Bean
    public LoginRateLimitFilter loginRateLimitFilter(ProxyManager<String> rateLimitProxyManager,
                                                     ObjectMapper objectMapper) {
        return new LoginRateLimitFilter(rateLimitProxyManager, objectMapper);
    }

}
