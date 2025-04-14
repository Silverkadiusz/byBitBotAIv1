package com.example.bybitbotai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResult {

    private String orderId;
    private String symbol;
    private String side; // Buy or Sell
    private String orderType;
    private double price;
    private String quantity;
    private LocalDateTime timestamp;
    private String status;
    private boolean success;
    private String errorMessage;

    public static OrderResult error(String errorMessage) {
        return OrderResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }
}