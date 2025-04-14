package com.example.bybitbotai.util;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Narzędzie do generowania podpisów dla API Bybit
 */
@Component
public class ApiSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Generuje timestamp dla API Bybit
     */
    public String generateTimestamp() {
        return String.valueOf(Instant.now().toEpochMilli());
    }

    /**
     * Generuje podpis HMAC-SHA256 dla parametrów zapytania Bybit
     *
     * @param apiKey Klucz API Bybit
     * @param apiSecret Sekret API Bybit
     * @param timestamp Znacznik czasu w milisekundach
     * @param params Parametry zapytania
     * @return Podpis
     */
    public String generateSignature(String apiKey, String apiSecret, String timestamp, Map<String, Object> params) {
        try {
            String recvWindow = "5000";

            // Konwersja parametrów na string zapytania
            String queryString = params.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));

            // String podpisu: timestamp + apiKey + recvWindow + queryString
            String signatureBase = timestamp + apiKey + recvWindow + queryString;

            // Generowanie podpisu HMAC-SHA256
            Mac hmacSha256 = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            hmacSha256.init(secretKeySpec);
            byte[] hash = hmacSha256.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));

            // Konwersja na string heksadecymalny
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Błąd podczas generowania podpisu API", e);
        }
    }

    /**
     * Sortuje mapę parametrów dla zapytania API
     */
    public Map<String, Object> sortParams(Map<String, Object> params) {
        return new TreeMap<>(params);
    }
}