package com.example.bybitbotai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import lombok.Getter;
import lombok.Setter;

@Configuration
public class ApiConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Getter
    @Setter
    @Configuration
    @ConfigurationProperties(prefix = "bybit.api")
    public static class BybitApiConfig {
        private String url;
        private String key;
        private String secret;
    }

    @Getter
    @Setter
    @Configuration
    @ConfigurationProperties(prefix = "claude.api")
    public static class ClaudeApiConfig {
        private String url;
        private String key;
        private String version;
        private String model;
    }

    @Getter
    @Setter
    @Configuration
    @ConfigurationProperties(prefix = "trading")
    public static class TradingConfig {
        private String symbol;
        private String category;
        private String orderType;
        private String amount;
        private int intervalMinutes;
        private int intervalSec;
        private boolean demo;
    }
}