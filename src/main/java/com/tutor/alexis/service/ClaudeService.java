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

    /**
     * Version original (system como string plano, sin caching).
     * Se mantiene por compatibilidad: delega a la version con caching
     * poniendo todo el prompt como parte estable.
     */
    public String enviarConversacionCompleta(String systemPrompt, List<Map<String, Object>> historial) {
        return enviarConversacionCompleta(systemPrompt, null, historial);
    }

    /**
     * PROMPT CACHING: el system se manda en dos bloques.
     *   - systemEstable: prompt base + perfil (identico en cada llamada del dia)
     *     -> marcado con cache_control, la API lo cachea y las llamadas
     *        siguientes leen del cache a ~10% del costo de input.
     *   - contextoVariable: datos del dia (sesiones hoy, semana del plan, etc.)
     *     -> va DESPUES del bloque cacheado, sin cache (cambia entre mensajes).
     * El cache dura 5 min y se refresca con cada hit, asi que dentro de una
     * sesion activa practicamente todas las llamadas pegan en cache.
     */
    public String enviarConversacionCompleta(String systemEstable, String contextoVariable,
                                             List<Map<String, Object>> historial) {
        int intentos = 0;
        int maxIntentos = 3;

        // Bloques del system: el estable con cache_control, el variable sin.
        List<Map<String, Object>> systemBlocks;
        Map<String, Object> bloqueEstable = Map.of(
                "type", "text",
                "text", systemEstable,
                "cache_control", Map.of("type", "ephemeral")
        );
        if (contextoVariable != null && !contextoVariable.isBlank()) {
            systemBlocks = List.of(
                    bloqueEstable,
                    Map.of("type", "text", "text", contextoVariable)
            );
        } else {
            systemBlocks = List.of(bloqueEstable);
        }

        while (intentos < maxIntentos) {
            try {
                Map<String, Object> body = Map.of(
                        "model", model,
                        "max_tokens", 4096,
                        "system", systemBlocks,
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

                // Log de uso de cache (visible en consola para verificar el ahorro)
                Object usage = response.get("usage");
                if (usage instanceof Map<?, ?> u) {
                    Object creation = u.get("cache_creation_input_tokens");
                    Object read = u.get("cache_read_input_tokens");
                    if (creation != null || read != null) {
                        System.out.println("[CACHE] escritos: " + creation + " | leidos: " + read);
                    }
                }

                List<Map> content = (List<Map>) response.get("content");
                if (content == null || content.isEmpty()) {
                    System.err.println("Respuesta vacía de Claude. Body: " + response);
                    return "Error: respuesta vacía del tutor.";
                }
                // Robusto ante modelos con bloques de razonamiento (p.ej. Sonnet 5):
                // el texto puede NO ser el primer bloque. Se concatenan todos los
                // bloques type="text" y se ignora el resto (thinking, tool_use...).
                StringBuilder texto = new StringBuilder();
                for (Map bloque : content) {
                    if ("text".equals(bloque.get("type")) && bloque.get("text") != null) {
                        texto.append(bloque.get("text"));
                    }
                }
                if (texto.isEmpty()) {
                    System.err.println("Respuesta sin bloques de texto. Body: " + response);
                    return "Error: respuesta vacía del tutor.";
                }
                return texto.toString();

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