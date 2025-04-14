package com.example.bybitbotai.service;

import com.example.bybitbotai.config.ApiConfig.TradingConfig;
import com.example.bybitbotai.model.OrderResult;
import com.example.bybitbotai.model.TechnicalIndicators;
import com.example.bybitbotai.model.TradingDecision;

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
            // Ramka początkowa z tytułem
            log.info("╔════════════════════════════════════════════════════════════════════════════╗");
            log.info("║                       ANALIZA TECHNICZNA KRYPTOWALUTY                      ║");
            log.info("╠════════════════════════════════════════════════════════════════════════════╣");

            // Krok 1: Pobierz aktualną cenę kryptowaluty
            double currentPrice = bybitService.getCurrentPrice(
                    tradingConfig.getSymbol(), tradingConfig.getCategory());

            // Formatowanie danych
            String formattedPrice = formatPrice(currentPrice);
            String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Wyświetlenie podstawowych danych w ramce
            log.info("║ Symbol: {}", padRight(tradingConfig.getSymbol(), 67) + "║");
            log.info("║ Czas:   {}", padRight(timeStamp, 67) + "║");
            log.info("║                                                                            ║");
            log.info("║ CENA:   {} USDT", padRight(formattedPrice, 60) + "║");

            // Dodanie informacji o zmianie ceny, jeśli mamy poprzednią cenę
            TradingDecision lastDec = lastDecision.get();
            if (lastDec != null && lastDec.getCurrentPrice() > 0) {
                double previousPrice = lastDec.getCurrentPrice();
                double priceDiff = currentPrice - previousPrice;
                double percentChange = (priceDiff / previousPrice) * 100;

                String changeStr = String.format("%+.2f (%+.2f%%)", priceDiff, percentChange);
                String changeIndicator = priceDiff > 0 ? "↑" : (priceDiff < 0 ? "↓" : "→");

                log.info("║ Zmiana: {} {}", padRight(changeStr, 59) + changeIndicator + "║");
            }
            log.info("║                                                                            ║");

            // Krok 2: Pobierz wskaźniki techniczne
            TechnicalIndicators indicators = technicalIndicatorsService.calculateAllIndicators(
                    tradingConfig.getSymbol(), tradingConfig.getCategory(), 200);

            if (indicators != null) {
                // Wyświetl kluczowe wskaźniki
                log.info("║ WSKAŹNIKI TECHNICZNE:                                                      ║");
                log.info("║ SMA20/50/200: {}/{}/{}",
                        padRight(String.format("%.2f", indicators.getSma20()), 7),
                        padRight(String.format("%.2f", indicators.getSma50()), 7),
                        padRight(String.format("%.2f", indicators.getSma200()), 34) + "║");

                log.info("║ RSI(14): {}", padRight(String.format("%.2f %s",
                        indicators.getRsi(),
                        indicators.isRsiOverbought() ? "[WYKUPIENIE]" :
                                (indicators.isRsiOversold() ? "[WYPRZEDANIE]" : "")), 62) + "║");

                log.info("║ MACD: {}", padRight(String.format("%.4f / %.4f %s",
                        indicators.getMacdLine(),
                        indicators.getSignalLine(),
                        indicators.isMacdCrossover() ? "[SYGNAŁ KUPNA]" :
                                (indicators.isMacdCrossunder() ? "[SYGNAŁ SPRZEDAŻY]" : "")), 63) + "║");

                log.info("║ Bollinger: {}", padRight(String.format("%.2f / %.2f / %.2f",
                        indicators.getBollingerLower(),
                        indicators.getBollingerMiddle(),
                        indicators.getBollingerUpper()), 59) + "║");

                log.info("║ Stochastic: {}", padRight(String.format("K:%.2f D:%.2f %s",
                        indicators.getStochasticK(),
                        indicators.getStochasticD(),
                        indicators.isStochasticOverbought() ? "[WYKUPIENIE]" :
                                (indicators.isStochasticOversold() ? "[WYPRZEDANIE]" : "")), 57) + "║");

                log.info("║ Wolumen: {}", padRight(String.format("%.2f (%.2fx śr.) %s",
                        indicators.getVolume(),
                        indicators.getVolumeRatio(),
                        indicators.isHighVolume() ? "[WYSOKI]" : ""), 61) + "║");
            } else {
                log.info("║ Nie udało się obliczyć wskaźników technicznych                           ║");
            }
            log.info("║                                                                            ║");

            // Krok 3: Zapytaj Claude o decyzję tradingową
            TradingDecision decision;
            if (indicators != null) {
                decision = claudeService.getTradingDecision(
                        tradingConfig.getSymbol(), tradingConfig.getCategory(), currentPrice);
            } else {
                decision = claudeService.getTradingDecision(
                        tradingConfig.getSymbol(), tradingConfig.getCategory(), currentPrice);
            }

            lastDecision.set(decision);
            log.info("║ DECYZJA AI: {}", padRight(decision.getAction().toString(), 61) + "║");
            log.info("╚════════════════════════════════════════════════════════════════════════════╝");

            // Krok 4: Wykonaj transakcję na podstawie decyzji
           /* if (decision.isActionable()) {
                // Sprawdź, czy nie wykonujemy zbyt częstych transakcji tego samego typu
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
            log.error("╔════════════════════════════════════════════════════════════════════════════╗");
            log.error("║                                    BŁĄD                                    ║");
            log.error("╠════════════════════════════════════════════════════════════════════════════╣");
            log.error("║ {}", padRight(e.getMessage(), 76) + "║");
            log.error("╚════════════════════════════════════════════════════════════════════════════╝");
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

        // Jeśli nie było wcześniejszych zleceń, wykonaj to
        if (lastOrderResult == null) {
            return true;
        }

        // Sprawdź, czy ostatnie zlecenie było tego samego typu
        boolean isSameActionAsLast =
                (decision.getAction() == TradingDecision.Action.BUY && "Buy".equals(lastOrderResult.getSide())) ||
                        (decision.getAction() == TradingDecision.Action.SELL && "Sell".equals(lastOrderResult.getSide()));

        // Jeśli to inny typ akcji, wykonaj
        if (!isSameActionAsLast) {
            return true;
        }

        // Sprawdź, czy minęło wystarczająco dużo czasu od ostatniego zlecenia tego samego typu
        // (np. minimum 1 godzina między zleceniami tego samego typu)
        LocalDateTime lastOrderTime = lastOrderResult.getTimestamp();
        Duration timeSinceLastOrder = Duration.between(lastOrderTime, LocalDateTime.now());

        // Minimalny czas między zleceniami tego samego typu (w minutach)
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
        // Dla BTC wystarczy 2 miejsca po przecinku, ale dla innych kryptowalut
        // możemy chcieć wyświetlać więcej miejsc, np. dla tokenów o małej wartości
        if (price >= 1000) {
            return String.format("%,.2f", price); // Format z separatorem tysięcy
        } else if (price >= 1) {
            return String.format("%.2f", price);
        } else {
            return String.format("%.8f", price); // Więcej miejsc po przecinku dla małych wartości
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