package com.example.bybitbotai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model przechowujący wartości wskaźników technicznych
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalIndicators {
    // Podstawowe informacje
    private String symbol;
    private double currentPrice;
    private Instant timestamp;

    // Średnie kroczące
    private double sma20;
    private double sma50;
    private double sma200;
    private double ema20;
    private double ema50;

    // RSI
    private double rsi;
    private boolean rsiOverbought;
    private boolean rsiOversold;

    // Bollinger Bands
    private double bollingerUpper;
    private double bollingerMiddle;
    private double bollingerLower;
    private double bollingerWidth;

    // MACD
    private double macdLine;
    private double signalLine;
    private double macdHistogram;
    private boolean macdCrossover;
    private boolean macdCrossunder;

    // Stochastic Oscillator
    private double stochasticK;
    private double stochasticD;
    private boolean stochasticOverbought;
    private boolean stochasticOversold;

    // Wolumen
    private double volume;
    private double volumeAvg;
    private double volumeRatio;
    private boolean highVolume;

    /**
     * Zwraca opis tekstowy wskaźników w formacie dla Claude
     */
    public String getIndicatorsDescription() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Symbol: %s, Cena: %.2f\n\n", symbol, currentPrice));

        // Średnie kroczące
        sb.append("ŚREDNIE KROCZĄCE:\n");
        sb.append(String.format("SMA20: %.2f (%.2f%% od ceny)\n", sma20, 100 * (currentPrice - sma20) / sma20));
        sb.append(String.format("SMA50: %.2f (%.2f%% od ceny)\n", sma50, 100 * (currentPrice - sma50) / sma50));
        sb.append(String.format("SMA200: %.2f (%.2f%% od ceny)\n", sma200, 100 * (currentPrice - sma200) / sma200));
        sb.append(String.format("EMA20: %.2f\n", ema20));
        sb.append(String.format("EMA50: %.2f\n\n", ema50));

        // RSI
        sb.append("RSI:\n");
        sb.append(String.format("RSI(14): %.2f", rsi));
        if (rsiOverbought) {
            sb.append(" [WYKUPIENIE]");
        } else if (rsiOversold) {
            sb.append(" [WYPRZEDANIE]");
        }
        sb.append("\n\n");

        // Bollinger Bands
        sb.append("BOLLINGER BANDS:\n");
        sb.append(String.format("Górne: %.2f\n", bollingerUpper));
        sb.append(String.format("Środkowe: %.2f\n", bollingerMiddle));
        sb.append(String.format("Dolne: %.2f\n", bollingerLower));
        sb.append(String.format("Szerokość: %.2f\n\n", bollingerWidth));

        // MACD
        sb.append("MACD:\n");
        sb.append(String.format("Linia MACD: %.4f\n", macdLine));
        sb.append(String.format("Linia sygnału: %.4f\n", signalLine));
        sb.append(String.format("Histogram: %.4f\n", macdHistogram));
        if (macdCrossover) {
            sb.append("Sygnał: MACD przecina linię sygnału od dołu (sygnał kupna)\n");
        } else if (macdCrossunder) {
            sb.append("Sygnał: MACD przecina linię sygnału od góry (sygnał sprzedaży)\n");
        }
        sb.append("\n");

        // Stochastic Oscillator
        sb.append("STOCHASTIC OSCILLATOR:\n");
        sb.append(String.format("%%K: %.2f\n", stochasticK));
        sb.append(String.format("%%D: %.2f\n", stochasticD));
        if (stochasticOverbought) {
            sb.append("Sygnał: Wykupienie (>80)\n");
        } else if (stochasticOversold) {
            sb.append("Sygnał: Wyprzedanie (<20)\n");
        }
        sb.append("\n");

        // Wolumen
        sb.append("WOLUMEN:\n");
        sb.append(String.format("Aktualny: %.2f\n", volume));
        sb.append(String.format("Średni (20 okresów): %.2f\n", volumeAvg));
        sb.append(String.format("Stosunek: %.2f", volumeRatio));
        if (highVolume) {
            sb.append(" [WYSOKI WOLUMEN]");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Zwraca listę sygnałów dla aktualnych wskaźników
     */
    public String getSignalsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("SYGNAŁY HANDLOWE:\n");

        // Trendy ze średnich kroczących
        if (currentPrice > sma50 && sma50 > sma200) {
            sb.append("✓ Trend wzrostowy (cena > SMA50 > SMA200)\n");
        } else if (currentPrice < sma50 && sma50 < sma200) {
            sb.append("✓ Trend spadkowy (cena < SMA50 < SMA200)\n");
        } else {
            sb.append("- Brak wyraźnego trendu z SMA\n");
        }

        // Crossover SMA
        if (Math.abs(sma20 - sma50) / sma50 < 0.005) {
            if (sma20 > sma50) {
                sb.append("✓ SMA20 przecina SMA50 od dołu (sygnał kupna)\n");
            } else {
                sb.append("✓ SMA20 przecina SMA50 od góry (sygnał sprzedaży)\n");
            }
        }

        // RSI
        if (rsiOverbought) {
            sb.append("✓ RSI wskazuje na wykupienie (>70) - możliwy spadek\n");
        } else if (rsiOversold) {
            sb.append("✓ RSI wskazuje na wyprzedanie (<30) - możliwy wzrost\n");
        }

        // Bollinger Bands
        if (currentPrice > bollingerUpper) {
            sb.append("✓ Cena powyżej górnego pasma Bollingera - możliwy spadek\n");
        } else if (currentPrice < bollingerLower) {
            sb.append("✓ Cena poniżej dolnego pasma Bollingera - możliwy wzrost\n");
        }

        // MACD
        if (macdCrossover) {
            sb.append("✓ MACD przecina linię sygnału od dołu - sygnał kupna\n");
        } else if (macdCrossunder) {
            sb.append("✓ MACD przecina linię sygnału od góry - sygnał sprzedaży\n");
        }

        // Stochastic
        if (stochasticOverbought) {
            sb.append("✓ Stochastic wskazuje na wykupienie (>80) - możliwy spadek\n");
        } else if (stochasticOversold) {
            sb.append("✓ Stochastic wskazuje na wyprzedanie (<20) - możliwy wzrost\n");
        }

        // Wolumen
        if (highVolume) {
            sb.append("✓ Wysoki wolumen - zwiększona aktywność rynku\n");
        }

        return sb.toString();
    }
}