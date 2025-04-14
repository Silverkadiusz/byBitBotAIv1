package com.example.bybitbotai.dto.claude;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaudeRequest {

    private String model;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private String system;  // System prompt jako osobny parametr

    private List<Message> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;     // tylko "user" lub "assistant"
        private String content;  // treść wiadomości
    }

    // Metoda pomocnicza do tworzenia prostego zapytania
    public static ClaudeRequest createSimpleRequest(String model, String systemPrompt, String userPrompt, int maxTokens) {
        Message userMessage = Message.builder()
                .role("user")
                .content(userPrompt)
                .build();

        return ClaudeRequest.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .messages(List.of(userMessage))
                .build();
    }
}