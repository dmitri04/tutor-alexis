package com.tutor.alexis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.model}")
    private String model;

    private final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

    public String enviarConversacionCompleta(String systemPrompt, List<Map<String, Object>> historial) {
        int intentos = 0;
        int maxIntentos = 3;

        while (intentos < maxIntentos) {
            try {
                Map<String, Object> body = Map.of(
                        "model", model,
                        "max_tokens", 4096,
                        "system", systemPrompt,
                        "messages", historial
                );

                Map response = webClient.post()
                        .uri(apiUrl)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .header("content-type", "application/json")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                List<Map> content = (List<Map>) response.get("content");
                if (content == null || content.isEmpty()) {
                    System.err.println("Respuesta vacía de Claude.");
                    return "Error: respuesta vacía del tutor.";
                }
                return (String) content.get(0).get("text");

            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    intentos++;
                    int espera = 3000 * intentos;
                    System.err.println("429 rate limit — intento " + intentos +
                            " de " + maxIntentos + " — esperando " + espera + "ms");
                    try { Thread.sleep(espera); } catch (Exception ignored) {}
                } else {
                    System.err.println("Error API: " + e.getStatusCode() + " — " + e.getMessage());
                    return "Error al conectar con el tutor: " + e.getMessage();
                }
            } catch (Exception e) {
                System.err.println("Error inesperado: " + e.getMessage());
                return "Error al conectar con el tutor: " + e.getMessage();
            }
        }
        return "El tutor está ocupado. Espera unos segundos e intenta de nuevo.";
    }
}