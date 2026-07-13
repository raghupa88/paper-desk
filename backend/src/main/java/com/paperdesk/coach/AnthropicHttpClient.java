package com.paperdesk.coach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Real implementation, calling the Anthropic Messages API directly over {@link HttpClient} (JDK 21, no extra SDK dependency needed). */
@Component
public class AnthropicHttpClient implements AnthropicClient {

    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 512;

    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicHttpClient(@Value("${paperdesk.coach.api-key:}") String apiKey,
                               @Value("${paperdesk.coach.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        ArrayNode messages = body.putArray("messages");
        ObjectNode userTurn = messages.addObject();
        userTurn.put("role", "user");
        userTurn.put("content", userMessage);

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Anthropic API returned HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        return root.path("content").path(0).path("text").asText("");
    }
}
