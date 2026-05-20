package com.mafia.controller;
import com.mafia.service.GameStateService;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GameStateController {

    private final GameStateService gameStateService;

    public GameStateController(GameStateService gameStateService) {
        this.gameStateService = gameStateService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "ok",
            "service", "mafia-game-engine",
            "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/game-state/{roomId}")
    public ResponseEntity<?> gameState(@PathVariable String roomId) {
        try {
            return ResponseEntity.ok(gameStateService.getSnapshot(roomId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @PostMapping("/game-state/{roomId}/start")
    public ResponseEntity<Map<String, String>> startGame(@PathVariable String roomId) {
        try {
            gameStateService.startGame(roomId);
            return ResponseEntity.ok(Map.of("roomId", roomId, "status", "started"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }
}