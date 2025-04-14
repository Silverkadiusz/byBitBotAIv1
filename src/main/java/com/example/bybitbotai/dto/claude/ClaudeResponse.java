package com.example.bybitbotai.dto.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeResponse {

    private String id;
    private String model;
    private List<Content> content;  // Zmiana: content jest teraz listą
    private String type;
    private String role;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("stop_sequence")
    private String stopSequence;

    private Usage usage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        private String type;
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;
    }

    /**
     * Metoda pomocnicza do pobrania pierwszej części odpowiedzi
     */
    public String getFirstResponsePart() {
        if (content != null && !content.isEmpty() && content.get(0).text != null) {
            return content.get(0).text;
        }
        return "";
    }
}