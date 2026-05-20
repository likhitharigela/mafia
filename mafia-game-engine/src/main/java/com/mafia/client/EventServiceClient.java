package com.mafia.client;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

@Component
public class EventServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EventServiceClient(RestTemplate restTemplate,
                              @Value("${gin.event.base-url:http://localhost:8081/api}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> getTimer(String roomId) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/timer/" + roomId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            
            return Map.of("remaining", 0);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getEvents(String roomId) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl + "/events/" + roomId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> body = response.getBody();
            return body != null ? body : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}