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
public class TradingDecision {

    public enum Action {
        BUY, SELL, WAIT
    }

    private Action action;
    private String symbol;
    private double currentPrice;
    private LocalDateTime timestamp;
    private String reasoning;

    public boolean isActionable() {
        return action == Action.BUY || action == Action.SELL;
    }
}