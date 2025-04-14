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

            // Pobierz wskaźniki techniczne
            TechnicalIndicators indicators = technicalIndicatorsService.calculateAllIndicators(
                    symbol, category, 200);

            if (indicators == null) {
                log.warn("Nie udało się obliczyć wskaźników technicznych, używam tylko ceny");
                return getTradingDecisionPriceOnly(symbol, currentPrice);
            }

            // Przygotowanie zapytania dla Claude
            String systemPrompt = "Jesteś doświadczonym traderem kryptowalut specjalizującym się w analizie technicznej. " +
                    "Analizujesz wskaźniki techniczne, aby podejmować decyzje handlowe dla kryptowalut. " +
                    "Odpowiadaj tylko jednym słowem: 'Buy' (kup), 'Sell' (sprzedaj), lub 'Wait' (czekaj). " +
                    "Analizuj wskaźniki techniczne i podejmij najlepszą decyzję inwestycyjną. Nie dodawaj wyjaśnień.";

            String userPrompt = "Na podstawie poniższych wskaźników technicznych dla " + symbol +
                    ", zdecyduj czy powinienem kupić, sprzedać czy czekać. Odpowiedz tylko jednym słowem (Buy/Sell/Wait).\n\n" +
                    indicators.getIndicatorsDescription() + "\n" +
                    indicators.getSignalsSummary();

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

            // Przygotowanie zapytania dla Claude
            String systemPrompt = "Jesteś asystentem tradingowym kryptowalut. " +
                    "Odpowiadaj tylko jednym słowem: 'Buy' (kup), 'Sell' (sprzedaj), lub 'Wait' (czekaj). " +
                    "Analizuj obecną cenę i podejmij najlepszą decyzję inwestycyjną. Nie dodawaj wyjaśnień.";

            String userPrompt = "Aktualna cena " + symbol + " to " + currentPrice + " USDT. " +
                    "Na podstawie bieżących warunków rynkowych i ceny, czy powinienem kupić, sprzedać czy czekać? " +
                    "Odpowiedz tylko jednym słowem.";

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