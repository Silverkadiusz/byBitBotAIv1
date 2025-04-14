package com.example.bybitbotai.service;

import com.example.bybitbotai.config.ApiConfig.TradingConfig;
import com.example.bybitbotai.model.OrderResult;
import com.example.bybitbotai.model.TechnicalIndicators;
import com.example.bybitbotai.model.TradingDecision;
import com.example.bybitbotai.util.SoundUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class TradingService {

    private final BybitService bybitService;
    private final ClaudeService claudeService;
    private final TechnicalIndicatorsService technicalIndicatorsService;
    private final TradingConfig tradingConfig;

    // Przechowywanie ostatniej decyzji tradingowej
    private final AtomicReference<TradingDecision> lastDecision = new AtomicReference<>();
    private final AtomicReference<OrderResult> lastOrder = new AtomicReference<>();

    @Autowired
    public TradingService(BybitService bybitService, ClaudeService claudeService,
                          TechnicalIndicatorsService technicalIndicatorsService, TradingConfig tradingConfig) {
        this.bybitService = bybitService;
        this.claudeService = claudeService;
        this.technicalIndicatorsService = technicalIndicatorsService;
        this.tradingConfig = tradingConfig;
    }

    /**
     * Wykonuje pełny cykl tradingowy:
     * 1. Pobiera aktualną cenę
     * 2. Oblicza wskaźniki techniczne
     * 3. Pyta Claude o decyzję
     * 4. Wykonuje transakcję (jeśli potrzeba)
     *
     * @return Wynik wykonania transakcji lub null, jeśli nie wykonano transakcji
     */
    public OrderResult executeTradingCycle() {
        try {
            double currentPrice = bybitService.getCurrentPrice(
                    tradingConfig.getSymbol(), tradingConfig.getCategory());

            String formattedPrice = formatPrice(currentPrice);
            String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("╔════════════════════════════════════════════════════════════════════════════╗\n");
            logBuilder.append("║                       ANALIZA TECHNICZNA KRYPTOWALUTY                      ║\n");
            logBuilder.append("╠════════════════════════════════════════════════════════════════════════════╣\n");

            logBuilder.append("║ Symbol: ").append(padRight(tradingConfig.getSymbol(), 67)).append("║\n");
            logBuilder.append("║ Czas:   ").append(padRight(timeStamp, 67)).append("║\n");
            logBuilder.append("║                                                                            ║\n");
            logBuilder.append("║ CENA:   ").append(padRight(formattedPrice + " USDT", 60)).append("║\n");

            TradingDecision lastDec = lastDecision.get();
            if (lastDec != null && lastDec.getCurrentPrice() > 0) {
                double previousPrice = lastDec.getCurrentPrice();
                double priceDiff = currentPrice - previousPrice;
                double percentChange = (priceDiff / previousPrice) * 100;

                String changeStr = String.format("%+.2f (%+.2f%%)", priceDiff, percentChange);
                String changeIndicator = priceDiff > 0 ? "↑" : (priceDiff < 0 ? "↓" : "→");

                logBuilder.append("║ Zmiana: ").append(padRight(changeStr, 59)).append(changeIndicator).append("║\n");
            }
            logBuilder.append("║                                                                            ║\n");

            TechnicalIndicators indicators = technicalIndicatorsService.calculateAllIndicators(
                    tradingConfig.getSymbol(), tradingConfig.getCategory(), 200);

            if (indicators != null) {
                logBuilder.append("║ WSKAŹNIKI TECHNICZNE:                                                      ║\n");
                logBuilder.append("║ SMA20/50/200: ")
                        .append(padRight(String.format("%.2f", indicators.getSma20()), 7))
                        .append("/")
                        .append(padRight(String.format("%.2f", indicators.getSma50()), 7))
                        .append("/")
                        .append(padRight(String.format("%.2f", indicators.getSma200()), 34))
                        .append("║\n");

                logBuilder.append("║ RSI(14): ")
                        .append(padRight(String.format("%.2f %s",
                                indicators.getRsi(),
                                indicators.isRsiOverbought() ? "[WYKUPIENIE]" :
                                        (indicators.isRsiOversold() ? "[WYPRZEDANIE]" : "")), 62))
                        .append("║\n");

                logBuilder.append("║ MACD: ")
                        .append(padRight(String.format("%.4f / %.4f %s",
                                indicators.getMacdLine(),
                                indicators.getSignalLine(),
                                indicators.isMacdCrossover() ? "[SYGNAŁ KUPNA]" :
                                        (indicators.isMacdCrossunder() ? "[SYGNAŁ SPRZEDAŻY]" : "")), 63))
                        .append("║\n");

                logBuilder.append("║ Bollinger: ")
                        .append(padRight(String.format("%.2f / %.2f / %.2f",
                                indicators.getBollingerLower(),
                                indicators.getBollingerMiddle(),
                                indicators.getBollingerUpper()), 59))
                        .append("║\n");

                logBuilder.append("║ Stochastic: ")
                        .append(padRight(String.format("K:%.2f D:%.2f %s",
                                indicators.getStochasticK(),
                                indicators.getStochasticD(),
                                indicators.isStochasticOverbought() ? "[WYKUPIENIE]" :
                                        (indicators.isStochasticOversold() ? "[WYPRZEDANIE]" : "")), 57))
                        .append("║\n");

                logBuilder.append("║ Wolumen: ")
                        .append(padRight(String.format("%.2f (%.2fx śr.) %s",
                                indicators.getVolume(),
                                indicators.getVolumeRatio(),
                                indicators.isHighVolume() ? "[WYSOKI]" : ""), 61))
                        .append("║\n");
            } else {
                logBuilder.append("║ Nie udało się obliczyć wskaźników technicznych                           ║\n");
            }
            logBuilder.append("║                                                                            ║\n");



            TradingDecision decision;
            if (indicators != null) {
                decision = claudeService.getTradingDecision(
                        tradingConfig.getSymbol(), tradingConfig.getCategory(), currentPrice);
            } else {
                decision = claudeService.getTradingDecision(
                        tradingConfig.getSymbol(), tradingConfig.getCategory(), currentPrice);
            }
            lastDecision.set(decision);

            String ansiColor;
            String resetColor = "\u001B[0m";

            if (decision.getAction() == TradingDecision.Action.BUY) {
                ansiColor = "\u001B[32m";
                SoundUtils.playSound("success");
            } else if (decision.getAction() == TradingDecision.Action.SELL) {
                ansiColor = "\u001B[31m";
                SoundUtils.playSound("alert");
            } else {
                ansiColor = "\u001B[33m";
                SoundUtils.playSound("notification");
            }

            String decisionWithColor = ansiColor + decision.getAction().toString() + resetColor;

            logBuilder.append("║ DECYZJA AI: ")
                    .append(padRight(decisionWithColor, 61))
                    .append("║\n");
            logBuilder.append("╚════════════════════════════════════════════════════════════════════════════╝");

            log.info(logBuilder.toString());

            /*if (decision.isActionable()) {
                if (shouldExecuteOrder(decision)) {
                    log.info("Wykonywanie transakcji: {}", decision.getAction());

                    OrderResult result = bybitService.executeOrder(
                            decision.getAction(),
                            tradingConfig.getSymbol(),
                            tradingConfig.getCategory(),
                            tradingConfig.getOrderType(),
                            tradingConfig.getAmount(),
                            currentPrice,
                            tradingConfig.isDemo()
                    );

                    lastOrder.set(result);
                    log.info("Wynik transakcji: {}", result);
                    return result;
                } else {
                    log.info("Pomijanie transakcji ze względu na zbyt krótki odstęp od ostatniej podobnej");
                }
            } else {
                log.info("Brak transakcji, decyzja: {}", decision.getAction());
            }

             */

            return null;
        } catch (Exception e) {
            StringBuilder errorBuilder = new StringBuilder();
            errorBuilder.append("╔════════════════════════════════════════════════════════════════════════════╗\n");
            errorBuilder.append("║                                    BŁĄD                                    ║\n");
            errorBuilder.append("╠════════════════════════════════════════════════════════════════════════════╣\n");
            errorBuilder.append("║ ").append(padRight(e.getMessage(), 76)).append("║\n");
            errorBuilder.append("╚════════════════════════════════════════════════════════════════════════════╝");
            log.error(errorBuilder.toString());
            return null;
        }
    }




    /**
     * Sprawdza, czy należy wykonać zlecenie na podstawie historii zleceń
     * Zapobiega zbyt częstym transakcjom tego samego typu
     *
     * @param decision Aktualna decyzja
     * @return true jeśli zlecenie powinno być wykonane
     */
    private boolean shouldExecuteOrder(TradingDecision decision) {
        OrderResult lastOrderResult = lastOrder.get();

        if (lastOrderResult == null) {
            return true;
        }

        boolean isSameActionAsLast =
                (decision.getAction() == TradingDecision.Action.BUY && "Buy".equals(lastOrderResult.getSide())) ||
                        (decision.getAction() == TradingDecision.Action.SELL && "Sell".equals(lastOrderResult.getSide()));

        if (!isSameActionAsLast) {
            return true;
        }

        // Sprawdzenie, czy minęło wystarczająco dużo czasu od ostatniego zlecenia tego samego typu

        LocalDateTime lastOrderTime = lastOrderResult.getTimestamp();
        Duration timeSinceLastOrder = Duration.between(lastOrderTime, LocalDateTime.now());

        long minTimeInMinutes = 60; // 1 godzina

        return timeSinceLastOrder.toMinutes() >= minTimeInMinutes;
    }

    /**
     * Pobiera ostatnią decyzję tradingową
     */
    public TradingDecision getLastDecision() {
        return lastDecision.get();
    }

    /**
     * Pobiera ostatnie wykonane zlecenie
     */
    public OrderResult getLastOrder() {
        return lastOrder.get();
    }

    /**
     * Metoda pomocnicza do formatowania ceny z odpowiednią liczbą miejsc po przecinku
     */
    private String formatPrice(double price) {

        if (price >= 1000) {
            return String.format("%,.2f", price);
        } else if (price >= 1) {
            return String.format("%.2f", price);
        } else {
            return String.format("%.8f", price);
        }
    }

    /**
     * Metoda pomocnicza do formatowania tekstu z prawym paddingiem
     */
    private String padRight(String s, int n) {
        if (s == null) {
            s = "";
        }
        if (s.length() > n) {
            return s.substring(0, n);
        }
        return String.format("%-" + n + "s", s);
    }
}