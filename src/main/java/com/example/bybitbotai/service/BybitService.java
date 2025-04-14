package com.example.bybitbotai.service;

import com.example.bybitbotai.config.ApiConfig.BybitApiConfig;
import com.example.bybitbotai.dto.bybit.BybitOrderRequest;
import com.example.bybitbotai.dto.bybit.BybitTickerResponse;
import com.example.bybitbotai.model.OrderResult;
import com.example.bybitbotai.model.TradingDecision.Action;
import com.example.bybitbotai.util.ApiSignatureUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class BybitService {

    private final RestTemplate restTemplate;
    private final BybitApiConfig bybitConfig;
    private final ApiSignatureUtil signatureUtil;

    @Autowired
    public BybitService(RestTemplate restTemplate, BybitApiConfig bybitConfig, ApiSignatureUtil signatureUtil) {
        this.restTemplate = restTemplate;
        this.bybitConfig = bybitConfig;
        this.signatureUtil = signatureUtil;
    }

    /**
     * Pobiera aktualną cenę kryptowaluty z API Bybit
     *
     * @param symbol Symbol kryptowaluty, np. "BTCUSDC"
     * @param category Kategoria, np. "spot"
     * @return Aktualna cena
     */
    public double getCurrentPrice(String symbol, String category) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(bybitConfig.getUrl() + "/v5/market/tickers")
                    .queryParam("category", category)
                    .queryParam("symbol", symbol)
                    .build()
                    .toUriString();

            log.debug("Pobieranie ceny dla {}, URL: {}", symbol, url);

            ResponseEntity<BybitTickerResponse> response = restTemplate.getForEntity(url, BybitTickerResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                BybitTickerResponse tickerResponse = response.getBody();

                if (tickerResponse.getRetCode() != 0) {
                    log.error("Błąd API Bybit: {} - {}", tickerResponse.getRetCode(), tickerResponse.getRetMsg());
                    throw new RuntimeException("Błąd API Bybit: " + tickerResponse.getRetMsg());
                }

                for (BybitTickerResponse.TickerData ticker : tickerResponse.getResult().getList()) {
                    if (symbol.equals(ticker.getSymbol())) {
                        double price = Double.parseDouble(ticker.getLastPrice());
                        log.info("Aktualna cena {}: {} USDC", symbol, price);
                        return price;
                    }
                }

                log.warn("Nie znaleziono ceny dla symbolu {} w odpowiedzi", symbol);
                throw new RuntimeException("Nie znaleziono ceny dla symbolu " + symbol);
            } else {
                log.error("Nieudane żądanie API: {}", response.getStatusCode());
                throw new RuntimeException("Nieudane żądanie API Bybit: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Błąd podczas pobierania ceny dla {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Błąd podczas pobierania ceny: " + e.getMessage(), e);
        }
    }

    /**
     * Wykonuje zlecenie na giełdzie Bybit
     *
     * @param action Akcja (BUY/SELL)
     * @param symbol Symbol kryptowaluty
     * @param category Kategoria
     * @param orderType Typ zlecenia
     * @param amount Ilość
     * @param currentPrice Aktualna cena (dla logowania)
     * @param demoMode Tryb demonstracyjny (bez rzeczywistych transakcji)
     * @return Wynik zlecenia
     */
    public OrderResult executeOrder(Action action, String symbol, String category, String orderType,
                                    String amount, double currentPrice, boolean demoMode) {
        if (demoMode) {
            log.info("TRYB DEMO: Symulacja zlecenia {} dla {} po cenie {}",
                    action, symbol, currentPrice);

            return OrderResult.builder()
                    .orderId("demo-order-" + System.currentTimeMillis())
                    .symbol(symbol)
                    .side(action == Action.BUY ? "Buy" : "Sell")
                    .orderType(orderType)
                    .price(currentPrice)
                    .quantity(amount)
                    .timestamp(LocalDateTime.now())
                    .status("DEMO")
                    .success(true)
                    .build();
        }

        try {
            // Budowanie żądania zlecenia
            BybitOrderRequest orderRequest = BybitOrderRequest.builder()
                    .category(category)
                    .symbol(symbol)
                    .side(action == Action.BUY ? "Buy" : "Sell")
                    .orderType(orderType)
                    .qty(amount)
                    .build();

            // Konwersja DTO na mapę dla podpisu
            Map<String, Object> orderParams = new HashMap<>();
            orderParams.put("category", orderRequest.getCategory());
            orderParams.put("symbol", orderRequest.getSymbol());
            orderParams.put("side", orderRequest.getSide());
            orderParams.put("orderType", orderRequest.getOrderType());
            orderParams.put("qty", orderRequest.getQty());

            // Sortowanie parametrów
            Map<String, Object> sortedParams = signatureUtil.sortParams(orderParams);

            // Generowanie nagłówków uwierzytelniających
            String timestamp = signatureUtil.generateTimestamp();
            String signature = signatureUtil.generateSignature(
                    bybitConfig.getKey(), bybitConfig.getSecret(), timestamp, sortedParams);

            // Ustawienie nagłówków
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-BAPI-API-KEY", bybitConfig.getKey());
            headers.set("X-BAPI-SIGN", signature);
            headers.set("X-BAPI-SIGN-TYPE", "2");
            headers.set("X-BAPI-TIMESTAMP", timestamp);
            headers.set("X-BAPI-RECV-WINDOW", "5000");

            HttpEntity<BybitOrderRequest> requestEntity = new HttpEntity<>(orderRequest, headers);

            // Wysłanie żądania
            String url = bybitConfig.getUrl() + "/v5/order/create";
            log.info("Wysyłanie zlecenia {} dla {} po cenie {}", action, symbol, currentPrice);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, Map.class);

            // Przetwarzanie odpowiedzi
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                Integer retCode = (Integer) responseBody.get("retCode");

                if (retCode != null && retCode == 0) {
                    Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
                    String orderId = (String) result.get("orderId");

                    log.info("Zlecenie wykonane pomyślnie: ID={}", orderId);

                    return OrderResult.builder()
                            .orderId(orderId)
                            .symbol(symbol)
                            .side(action == Action.BUY ? "Buy" : "Sell")
                            .orderType(orderType)
                            .price(currentPrice)
                            .quantity(amount)
                            .timestamp(LocalDateTime.now())
                            .status("SUCCESS")
                            .success(true)
                            .build();
                } else {
                    String retMsg = (String) responseBody.get("retMsg");
                    log.error("Błąd wykonania zlecenia: {} - {}", retCode, retMsg);
                    return OrderResult.error("Błąd API Bybit: " + retMsg);
                }
            }

            log.error("Błąd wykonania zlecenia: brak odpowiedzi");
            return OrderResult.error("Brak odpowiedzi z API Bybit");

        } catch (Exception e) {
            log.error("Błąd podczas wykonywania zlecenia: {}", e.getMessage(), e);
            return OrderResult.error("Błąd: " + e.getMessage());
        }
    }
}