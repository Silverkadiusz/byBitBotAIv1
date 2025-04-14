package com.example.bybitbotai.dto.bybit;

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
public class BybitTickerResponse {

    private Integer retCode;
    private String retMsg;
    private Result result;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private List<TickerData> list;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TickerData {
        private String symbol;

        @JsonProperty("lastPrice")
        private String lastPrice;

        @JsonProperty("indexPrice")
        private String indexPrice;

        @JsonProperty("markPrice")
        private String markPrice;

        @JsonProperty("prevPrice24h")
        private String prevPrice24h;

        @JsonProperty("price24hPcnt")
        private String price24hPcnt;

        @JsonProperty("highPrice24h")
        private String highPrice24h;

        @JsonProperty("lowPrice24h")
        private String lowPrice24h;

        @JsonProperty("volume24h")
        private String volume24h;

        @JsonProperty("turnover24h")
        private String turnover24h;
    }
}