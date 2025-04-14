package com.example.bybitbotai.service;

import com.example.bybitbotai.config.ApiConfig.ClaudeApiConfig;
import com.example.bybitbotai.dto.claude.ClaudeRequest;
import com.example.bybitbotai.dto.claude.ClaudeResponse;
import com.example.bybitbotai.model.TechnicalIndicators;
import com.example.bybitbotai.model.TradingDecision;
import com.example.bybitbotai.model.TradingDecision.Action;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Service
@Slf4j
public class ClaudeService {

    private final RestTemplate restTemplate;
    private final ClaudeApiConfig claudeConfig;
    private final TechnicalIndicatorsService technicalIndicatorsService;

    @Autowired
    public ClaudeService(RestTemplate restTemplate, ClaudeApiConfig claudeConfig,
                         TechnicalIndicatorsService technicalIndicatorsService) {
        this.restTemplate = restTemplate;
        this.claudeConfig = claudeConfig;
        this.technicalIndicatorsService = technicalIndicatorsService;
    }

    /**
     * Pyta Claude AI o decyzję tradingową na podstawie aktualnej ceny i wskaźników technicznych
     *
     * @param symbol Symbol kryptowaluty
     * @param category Kategoria rynku (spot, futures)
     * @param currentPrice Aktualna cena
     * @return Decyzja tradingowa
     */
    public TradingDecision getTradingDecision(String symbol, String category, double currentPrice) {
        try {
            log.info("Pytanie Claude o decyzję tradingową dla {} przy cenie {}", symbol, currentPrice);

            TechnicalIndicators indicators = technicalIndicatorsService.calculateAllIndicators(
                    symbol, category, 200);

            if (indicators == null) {
                log.warn("Nie udało się obliczyć wskaźników technicznych, używam tylko ceny");
                return getTradingDecisionPriceOnly(symbol, currentPrice);
            }

            String systemPrompt = "Jesteś traderem kryptowalut specjalizującym się w aktywnym day tradingu i scalp tradingu. " +
                    "Odpowiadaj wyłącznie słowami: 'Buy', 'Sell', lub 'Wait'. " +
                    "Twoim celem jest znajdowanie i wykorzystywanie nawet najmniejszych okazji rynkowych: " +
                    "- Reaguj na wyprzedanie (RSI < 40, Stochastic < 30) rekomendacją Buy " +
                    "- Reaguj na wykupienie (RSI > 60, Stochastic > 70) rekomendacją Sell " +
                    "- Zwracaj uwagę na przecięcia średnich kroczących i linie MACD " +
                    "- Preferuj aktywne działanie nad czekanie " +
                    "- Bierz pod uwagę każdy możliwy sygnał techniczny " +
                    "Odpowiadaj tylko jednym słowem.";

            String userPrompt = "Bazując na poniższych wskaźnikach technicznych dla " + symbol +
                    ", zdecyduj czy należy kupić, sprzedać czy czekać. " +
                    "Wskaźniki mogą zawierać sygnały, które normalnie byłyby zbyt słabe, " +
                    "ale dla agresywnego tradera są wartościową okazją rynkową. " +
                    "Nawet jeśli sygnały są mieszane, wybierz akcję, która ma największe prawdopodobieństwo zysku. " +
                    "Odpowiedz tylko: Buy, Sell lub Wait.\n\n" +
                    indicators.getIndicatorsDescription() + "\n" +
                    indicators.getSignalsSummary() +
                    "\nCzekanie (Wait) wybieraj tylko w ostateczności, gdy absolutnie nie ma żadnych sygnałów.";

            ClaudeRequest request = ClaudeRequest.createSimpleRequest(
                    claudeConfig.getModel(), systemPrompt, userPrompt, 100);

            // Przygotowanie nagłówków
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("x-api-key", claudeConfig.getKey());
            headers.set("anthropic-version", claudeConfig.getVersion());

            HttpEntity<ClaudeRequest> requestEntity = new HttpEntity<>(request, headers);

            // Wysłanie zapytania do API Claude
            ResponseEntity<ClaudeResponse> response = restTemplate.exchange(
                    claudeConfig.getUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    ClaudeResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ClaudeResponse claudeResponse = response.getBody();

                // Pobranie decyzji z odpowiedzi
                String decision = claudeResponse.getFirstResponsePart().trim().toLowerCase();
                log.info("Claude sugeruje: {}", decision);

                // Mapowanie odpowiedzi na akcję
                Action action;
                if (decision.contains("buy") || decision.contains("kup")) {
                    action = Action.BUY;
                } else if (decision.contains("sell") || decision.contains("sprzedaj")) {
                    action = Action.SELL;
                } else {
                    action = Action.WAIT;
                }

                return TradingDecision.builder()
                        .action(action)
                        .symbol(symbol)
                        .currentPrice(currentPrice)
                        .timestamp(LocalDateTime.now())
                        .reasoning(decision)
                        .build();
            } else {
                log.error("Nieudane żądanie API Claude: {}", response.getStatusCode());
                throw new RuntimeException("Nieudane żądanie API Claude: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Błąd podczas uzyskiwania decyzji z Claude: {}", e.getMessage(), e);

            // W przypadku błędu, zwracamy decyzję czekania
            return TradingDecision.builder()
                    .action(Action.WAIT)
                    .symbol(symbol)
                    .currentPrice(currentPrice)
                    .timestamp(LocalDateTime.now())
                    .reasoning("Błąd komunikacji z Claude: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Fallback - pyta Claude AI o decyzję tradingową tylko na podstawie aktualnej ceny
     */
    private TradingDecision getTradingDecisionPriceOnly(String symbol, double currentPrice) {
        try {
            log.info("Pytanie Claude o decyzję tradingową tylko na podstawie ceny dla {} ({})", symbol, currentPrice);

            String systemPrompt = "Jesteś agresywnym traderem kryptowalut, zawsze poszukującym okazji rynkowych. " +
                    "Odpowiadaj tylko jednym słowem: 'Buy' (kup), 'Sell' (sprzedaj), lub 'Wait' (czekaj). " +
                    "Preferujesz aktywny handel nad czekanie. Każdy mały sygnał to dla Ciebie potencjalna okazja. " +
                    "Buy gdy widzisz potencjał wzrostu. Sell gdy widzisz potencjał spadku. " +
                    "Wait tylko gdy absolutnie nie ma żadnych wskazówek. Nie dawaj wyjaśnień.";


            String userPrompt = "Aktualna cena " + symbol + " to " + currentPrice + " USDT. " +
                    "Rynek kryptowalut jest pełen okazji, nawet małe zmiany mogą przynieść zyski. " +
                    "Na podstawie tej ceny i Twojego doświadczenia, czy powinienem kupić, sprzedać czy czekać? " +
                    "Wskaż tylko: Buy, Sell lub Wait.";

            ClaudeRequest request = ClaudeRequest.createSimpleRequest(
                    claudeConfig.getModel(), systemPrompt, userPrompt, 100);

            // Przygotowanie nagłówków
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("x-api-key", claudeConfig.getKey());
            headers.set("anthropic-version", claudeConfig.getVersion());

            HttpEntity<ClaudeRequest> requestEntity = new HttpEntity<>(request, headers);

            // Wysłanie zapytania do API Claude
            ResponseEntity<ClaudeResponse> response = restTemplate.exchange(
                    claudeConfig.getUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    ClaudeResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ClaudeResponse claudeResponse = response.getBody();

                // Pobranie decyzji z odpowiedzi
                String decision = claudeResponse.getFirstResponsePart().trim().toLowerCase();
                log.info("Claude sugeruje (tylko cena): {}", decision);

                // Mapowanie odpowiedzi na akcję
                Action action;
                if (decision.contains("buy") || decision.contains("kup")) {
                    action = Action.BUY;
                } else if (decision.contains("sell") || decision.contains("sprzedaj")) {
                    action = Action.SELL;
                } else {
                    action = Action.WAIT;
                }

                return TradingDecision.builder()
                        .action(action)
                        .symbol(symbol)
                        .currentPrice(currentPrice)
                        .timestamp(LocalDateTime.now())
                        .reasoning(decision)
                        .build();
            } else {
                log.error("Nieudane żądanie API Claude: {}", response.getStatusCode());
                throw new RuntimeException("Nieudane żądanie API Claude: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Błąd podczas uzyskiwania decyzji z Claude: {}", e.getMessage(), e);

            // W przypadku błędu, zwracamy decyzję czekania
            return TradingDecision.builder()
                    .action(Action.WAIT)
                    .symbol(symbol)
                    .currentPrice(currentPrice)
                    .timestamp(LocalDateTime.now())
                    .reasoning("Błąd komunikacji z Claude: " + e.getMessage())
                    .build();
        }
    }
}