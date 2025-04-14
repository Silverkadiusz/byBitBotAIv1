package com.example.bybitbotai.dto.bybit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BybitOrderRequest {

    private String category;    // "spot", "linear" itd.
    private String symbol;      // np. "BTCUSDT"
    private String side;        // "Buy" lub "Sell"

    @JsonProperty("orderType")
    private String orderType;   // "Market", "Limit" itd.

    private String qty;         // ilość

    // Pola opcjonalne dla różnych typów zleceń
    private String price;       // cena dla zleceń typu Limit

    @JsonProperty("timeInForce")
    private String timeInForce; // "GTC", "IOC", "FOK", "PostOnly"

    @JsonProperty("reduceOnly")
    private Boolean reduceOnly;

    @JsonProperty("closeOnTrigger")
    private Boolean closeOnTrigger;
}