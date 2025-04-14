package com.example.bybitbotai.service;

import com.example.bybitbotai.model.TechnicalIndicators;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.bybitbotai.config.ApiConfig.BybitApiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serwis do obliczania wskaźników technicznych
 */
@Service
@Slf4j
public class TechnicalIndicatorsService {

    private final RestTemplate restTemplate;
    private final BybitApiConfig bybitConfig;
    private final ObjectMapper objectMapper;

    // Cache dla danych historycznych, aby zmniejszyć liczbę zapytań API
    private Map<String, List<PriceData>> priceDataCache = new HashMap<>();
    private Map<String, Instant> lastCacheUpdateTime = new HashMap<>();

    @Autowired
    public TechnicalIndicatorsService(RestTemplate restTemplate, BybitApiConfig bybitConfig) {
        this.restTemplate = restTemplate;
        this.bybitConfig = bybitConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Pobiera wszystkie wskaźniki techniczne dla danego symbolu
     */
    public TechnicalIndicators calculateAllIndicators(String symbol, String category, int lookbackPeriod) {
        try {
            // Pobierz dane historyczne
            List<PriceData> historicalData = getHistoricalData(symbol, category, lookbackPeriod);

            if (historicalData.isEmpty()) {
                log.error("Brak danych historycznych dla {}", symbol);
                return null;
            }

            // Bieżąca cena
            double currentPrice = historicalData.get(0).close;

            // Oblicz wskaźniki
            double sma20 = calculateSMA(historicalData, 20);
            double sma50 = calculateSMA(historicalData, 50);
            double sma200 = calculateSMA(historicalData, 200);

            double ema20 = calculateEMA(historicalData, 20);
            double ema50 = calculateEMA(historicalData, 50);

            RsiResult rsi = calculateRSI(historicalData, 14);

            BollingerBandsResult bbands = calculateBollingerBands(historicalData, 20, 2.0);

            MacdResult macd = calculateMACD(historicalData, 12, 26, 9);

            StochasticResult stoch = calculateStochastic(historicalData, 14, 3);

            // Oblicz wolumen
            double avgVolume = calculateAverageVolume(historicalData, 20);
            double currentVolume = historicalData.get(0).volume;
            double volumeRatio = currentVolume / avgVolume;

            // Stwórz i zwróć obiekt ze wszystkimi wskaźnikami
            return TechnicalIndicators.builder()
                    .symbol(symbol)
                    .currentPrice(currentPrice)
                    .timestamp(Instant.now())
                    .sma20(sma20)
                    .sma50(sma50)
                    .sma200(sma200)
                    .ema20(ema20)
                    .ema50(ema50)
                    .rsi(rsi.rsi)
                    .rsiOverbought(rsi.rsi > 70)
                    .rsiOversold(rsi.rsi < 30)
                    .bollingerUpper(bbands.upper)
                    .bollingerMiddle(bbands.middle)
                    .bollingerLower(bbands.lower)
                    .bollingerWidth((bbands.upper - bbands.lower) / bbands.middle)
                    .macdLine(macd.macdLine)
                    .signalLine(macd.signalLine)
                    .macdHistogram(macd.histogram)
                    .macdCrossover(macd.isCrossover)
                    .macdCrossunder(macd.isCrossunder)
                    .stochasticK(stoch.k)
                    .stochasticD(stoch.d)
                    .stochasticOverbought(stoch.k > 80)
                    .stochasticOversold(stoch.k < 20)
                    .volume(currentVolume)
                    .volumeAvg(avgVolume)
                    .volumeRatio(volumeRatio)
                    .highVolume(volumeRatio > 1.5)
                    .build();

        } catch (Exception e) {
            log.error("Błąd podczas obliczania wskaźników technicznych dla {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Pobiera dane historyczne z API Bybit
     */
    private List<PriceData> getHistoricalData(String symbol, String category, int limit) {
        try {
            String cacheKey = symbol + "-" + category;

            // Sprawdź, czy mamy dane w cache i czy nie są za stare
            if (priceDataCache.containsKey(cacheKey) && lastCacheUpdateTime.containsKey(cacheKey)) {
                Duration timeSinceLastUpdate = Duration.between(lastCacheUpdateTime.get(cacheKey), Instant.now());

                // Jeśli dane są nie starsze niż 5 minut, użyj cache
                if (timeSinceLastUpdate.toMinutes() < 5) {
                    return priceDataCache.get(cacheKey);
                }
            }

            // Pobierz dane z API
            String url = UriComponentsBuilder
                    .fromUriString(bybitConfig.getUrl() + "/v5/market/kline")
                    .queryParam("category", category)
                    .queryParam("symbol", symbol)
                    .queryParam("interval", "15") // 15-minutowe interwały
                    .queryParam("limit", Math.min(limit, 1000)) // Maksymalnie 1000 punktów danych
                    .build()
                    .toUriString();

            log.debug("Pobieranie danych historycznych dla {}, URL: {}", symbol, url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(response);

            if (rootNode.get("retCode").asInt() != 0) {
                log.error("Błąd API Bybit: {} - {}", rootNode.get("retCode"), rootNode.get("retMsg"));
                return Collections.emptyList();
            }

            // Parsuj dane
            JsonNode listNode = rootNode.get("result").get("list");
            List<PriceData> priceDataList = new ArrayList<>();

            for (JsonNode item : listNode) {
                PriceData priceData = new PriceData();
                priceData.timestamp = Instant.ofEpochMilli(Long.parseLong(item.get(0).asText()));
                priceData.open = Double.parseDouble(item.get(1).asText());
                priceData.high = Double.parseDouble(item.get(2).asText());
                priceData.low = Double.parseDouble(item.get(3).asText());
                priceData.close = Double.parseDouble(item.get(4).asText());
                priceData.volume = Double.parseDouble(item.get(5).asText());

                priceDataList.add(priceData);
            }

            // Odwróć listę, aby najnowsze dane były na początku
            Collections.reverse(priceDataList);

            // Zapisz w cache
            priceDataCache.put(cacheKey, priceDataList);
            lastCacheUpdateTime.put(cacheKey, Instant.now());

            return priceDataList;

        } catch (Exception e) {
            log.error("Błąd podczas pobierania danych historycznych dla {}: {}", symbol, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Oblicza średnią kroczącą prostą (SMA)
     */
    private double calculateSMA(List<PriceData> data, int period) {
        if (data.size() < period) {
            log.warn("Za mało danych do obliczenia SMA-{}: {} < {}", period, data.size(), period);
            return 0;
        }

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += data.get(i).close;
        }

        return sum / period;
    }

    /**
     * Oblicza średnią kroczącą wykładniczą (EMA)
     */
    private double calculateEMA(List<PriceData> data, int period) {
        if (data.size() < period) {
            log.warn("Za mało danych do obliczenia EMA-{}: {} < {}", period, data.size(), period);
            return 0;
        }

        double multiplier = 2.0 / (period + 1);
        double ema = data.get(period - 1).close; // Inicjalizacja EMA pierwszą ceną

        for (int i = period - 2; i >= 0; i--) {
            ema = data.get(i).close * multiplier + ema * (1 - multiplier);
        }

        return ema;
    }

    /**
     * Oblicza wskaźnik siły względnej (RSI)
     */
    private RsiResult calculateRSI(List<PriceData> data, int period) {
        if (data.size() < period + 1) {
            log.warn("Za mało danych do obliczenia RSI-{}: {} < {}", period, data.size(), period + 1);
            return new RsiResult(50, false, false); // Neutralny RSI
        }

        double gain = 0;
        double loss = 0;

        // Obliczenie pierwszej średniej zysków/strat
        for (int i = 0; i < period; i++) {
            double change = data.get(i).close - data.get(i + 1).close;
            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;

        // Obliczenie kolejnych wartości z wygładzaniem
        for (int i = period; i < Math.min(data.size() - 1, 2 * period); i++) {
            double change = data.get(i).close - data.get(i + 1).close;
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) - change) / period;
            }
        }

        if (avgLoss == 0) {
            return new RsiResult(100, true, false);
        }

        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));

        boolean overbought = rsi > 70;
        boolean oversold = rsi < 30;

        return new RsiResult(rsi, overbought, oversold);
    }

    /**
     * Oblicza Bollinger Bands
     */
    private BollingerBandsResult calculateBollingerBands(List<PriceData> data, int period, double stdDevMultiplier) {
        if (data.size() < period) {
            log.warn("Za mało danych do obliczenia Bollinger Bands: {} < {}", data.size(), period);
            double currentPrice = data.get(0).close;
            return new BollingerBandsResult(currentPrice, currentPrice, currentPrice); // Neutralne wartości
        }

        // Oblicz SMA
        double sma = calculateSMA(data, period);

        // Oblicz odchylenie standardowe
        double sum = 0;
        for (int i = 0; i < period; i++) {
            double deviation = data.get(i).close - sma;
            sum += deviation * deviation;
        }
        double stdDev = Math.sqrt(sum / period);

        // Oblicz górne i dolne pasmo
        double upper = sma + (stdDevMultiplier * stdDev);
        double lower = sma - (stdDevMultiplier * stdDev);

        return new BollingerBandsResult(upper, sma, lower);
    }

    /**
     * Oblicza wskaźnik MACD
     */
    private MacdResult calculateMACD(List<PriceData> data, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (data.size() < Math.max(fastPeriod, slowPeriod) + signalPeriod) {
            log.warn("Za mało danych do obliczenia MACD: {} < {}", data.size(), Math.max(fastPeriod, slowPeriod) + signalPeriod);
            return new MacdResult(0, 0, 0, false, false); // Neutralne wartości
        }

        // Oblicz EMA szybką i wolną
        double fastEMA = calculateEMA(data, fastPeriod);
        double slowEMA = calculateEMA(data, slowPeriod);

        // Oblicz linię MACD
        double macdLine = fastEMA - slowEMA;

        // Oblicz linię sygnału (EMA z linii MACD)
        // W uproszczeniu używamy średniej linii MACD za ostatnie signalPeriod dni
        double signalLineSum = 0;
        for (int i = 0; i < signalPeriod; i++) {
            double fastEMAi = calculateEMA(data.subList(i, data.size()), fastPeriod);
            double slowEMAi = calculateEMA(data.subList(i, data.size()), slowPeriod);
            signalLineSum += (fastEMAi - slowEMAi);
        }
        double signalLine = signalLineSum / signalPeriod;

        // Oblicz histogram
        double histogram = macdLine - signalLine;

        // Sprawdź przecięcia
        double prevFastEMA = calculateEMA(data.subList(1, data.size()), fastPeriod);
        double prevSlowEMA = calculateEMA(data.subList(1, data.size()), slowPeriod);
        double prevMacdLine = prevFastEMA - prevSlowEMA;

        double prevSignalLineSum = 0;
        for (int i = 1; i < signalPeriod + 1; i++) {
            double fastEMAi = calculateEMA(data.subList(i, data.size()), fastPeriod);
            double slowEMAi = calculateEMA(data.subList(i, data.size()), slowPeriod);
            prevSignalLineSum += (fastEMAi - slowEMAi);
        }
        double prevSignalLine = prevSignalLineSum / signalPeriod;

        boolean isCrossover = macdLine > signalLine && prevMacdLine <= prevSignalLine;
        boolean isCrossunder = macdLine < signalLine && prevMacdLine >= prevSignalLine;

        return new MacdResult(macdLine, signalLine, histogram, isCrossover, isCrossunder);
    }

    /**
     * Oblicza oscylator stochastyczny
     */
    private StochasticResult calculateStochastic(List<PriceData> data, int kPeriod, int dPeriod) {
        if (data.size() < kPeriod + dPeriod) {
            log.warn("Za mało danych do obliczenia Stochastic: {} < {}", data.size(), kPeriod + dPeriod);
            return new StochasticResult(50, 50); // Neutralne wartości
        }

        // Oblicz %K
        double currentClose = data.get(0).close;
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < kPeriod; i++) {
            highest = Math.max(highest, data.get(i).high);
            lowest = Math.min(lowest, data.get(i).low);
        }
        double k = 100 * ((currentClose - lowest) / (highest - lowest));

        // Oblicz %D (średnia z %K)
        double dSum = k;
        for (int i = 1; i < dPeriod; i++) {
            double highestI = Double.MIN_VALUE;
            double lowestI = Double.MAX_VALUE;
            boolean dataAvailable = true;

            for (int j = i; j < kPeriod + i; j++) {
                if (j < data.size()) {
                    highestI = Math.max(highestI, data.get(j).high);
                    lowestI = Math.min(lowestI, data.get(j).low);
                } else {
                    dataAvailable = false;
                    break;
                }
            }

            if (dataAvailable && i < data.size()) {
                double kI = 100 * ((data.get(i).close - lowestI) / (highestI - lowestI));
                dSum += kI;
            }
        }
        double d = dSum / dPeriod;

        return new StochasticResult(k, d);
    }

    /**
     * Oblicza średni wolumen
     */
    private double calculateAverageVolume(List<PriceData> data, int period) {
        if (data.size() < period) {
            log.warn("Za mało danych do obliczenia średniego wolumenu: {} < {}", data.size(), period);
            return data.get(0).volume; // Aktualny wolumen
        }

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += data.get(i).volume;
        }

        return sum / period;
    }

    /**
     * Klasa pomocnicza do przechowywania danych cenowych
     */
    private static class PriceData {
        Instant timestamp;
        double open;
        double high;
        double low;
        double close;
        double volume;
    }

    /**
     * Klasa pomocnicza dla wyniku RSI
     */
    private static class RsiResult {
        final double rsi;
        final boolean overbought;
        final boolean oversold;

        RsiResult(double rsi, boolean overbought, boolean oversold) {
            this.rsi = rsi;
            this.overbought = overbought;
            this.oversold = oversold;
        }
    }

    /**
     * Klasa pomocnicza dla wyniku Bollinger Bands
     */
    private static class BollingerBandsResult {
        final double upper;
        final double middle;
        final double lower;

        BollingerBandsResult(double upper, double middle, double lower) {
            this.upper = upper;
            this.middle = middle;
            this.lower = lower;
        }
    }

    /**
     * Klasa pomocnicza dla wyniku MACD
     */
    private static class MacdResult {
        final double macdLine;
        final double signalLine;
        final double histogram;
        final boolean isCrossover;
        final boolean isCrossunder;

        MacdResult(double macdLine, double signalLine, double histogram, boolean isCrossover, boolean isCrossunder) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
            this.isCrossover = isCrossover;
            this.isCrossunder = isCrossunder;
        }
    }

    /**
     * Klasa pomocnicza dla wyniku oscylatora stochastycznego
     */
    private static class StochasticResult {
        final double k;
        final double d;

        StochasticResult(double k, double d) {
            this.k = k;
            this.d = d;
        }
    }
}