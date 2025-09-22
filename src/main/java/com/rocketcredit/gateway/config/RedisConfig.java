package com.rocketcredit.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

  @Bean
  public LettuceConnectionFactory redisConnectionFactory(
      @Value("${REDIS_URL:redis://redis:6379}") String url) {
    String clean = url.replace("redis://", "");
    String host = clean;
    int port = 6379;
    int db = 0;

    int slash = clean.indexOf('/');
    if (slash >= 0) {
      String dbStr = clean.substring(slash + 1);
      if (!dbStr.isBlank()) db = Integer.parseInt(dbStr.trim());
      host = clean.substring(0, slash);
    }
    int colon = host.indexOf(':');
    if (colon >= 0) {
      port = Integer.parseInt(host.substring(colon + 1));
      host = host.substring(0, colon);
    }

    RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
    cfg.setDatabase(db);
    return new LettuceConnectionFactory(cfg);
  }

  @Bean
  public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory cf) {
    return new StringRedisTemplate(cf);
  }

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.registerModule(new org.openapitools.jackson.nullable.JsonNullableModule());
    om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return om;
  }
}
