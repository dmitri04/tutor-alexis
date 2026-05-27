package com.tutor.alexis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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
        .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB para imágenes
        .build();

    public String enviarConversacionCompleta(String systemPrompt, List<Map<String, Object>> historial) {
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 2048,
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
            return (String) content.get(0).get("text");

        } catch (Exception e) {
            e.printStackTrace(); // agrega esta línea
            return "Error al conectar con el tutor: " + e.getMessage();
        }
    }
}

