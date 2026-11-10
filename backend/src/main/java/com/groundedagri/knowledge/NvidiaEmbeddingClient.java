package com.groundedagri.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NvidiaEmbeddingClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestClient restClient;
    private final String endpoint;
    private final String model;
    private final int dimensions;

    public NvidiaEmbeddingClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.embedding.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
            @Value("${app.embedding.api-key:missing-key}") String apiKey,
            @Value("${app.embedding.model:nvidia/nemotron-3.5-embed-1b}") String model,
            @Value("${app.embedding.dimensions:1024}") int dimensions) {
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/embeddings";
        this.model = model;
        this.dimensions = dimensions;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    public List<float[]> embed(List<String> inputs, String inputType) {
        if (inputs.isEmpty()) return List.of();
        if (!inputType.equals("query") && !inputType.equals("passage")) {
            throw new IllegalArgumentException("Embedding input type must be query or passage.");
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("input", inputs);
        requestBody.put("model", model);
        requestBody.put("input_type", inputType);
        requestBody.put("dimensions", dimensions);

        String responseBody = restClient.post()
                .uri(endpoint)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        JsonNode response;
        try {
            response = OBJECT_MAPPER.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("NVIDIA embeddings response was not valid JSON.", exception);
        }
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.size() != inputs.size()) {
            throw new IllegalStateException("NVIDIA embeddings response did not contain one vector per input.");
        }

        List<float[]> vectors = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.size() != dimensions) {
                throw new IllegalStateException("NVIDIA embeddings response had an unexpected vector dimension.");
            }
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vector[i] = (float) embedding.get(i).asDouble();
                if (!Float.isFinite(vector[i])) {
                    throw new IllegalStateException("NVIDIA embeddings response contained a non-finite value.");
                }
            }
            vectors.add(vector);
        }
        return List.copyOf(vectors);
    }

    public String model() {
        return model;
    }

    public int dimensions() {
        return dimensions;
    }
}
