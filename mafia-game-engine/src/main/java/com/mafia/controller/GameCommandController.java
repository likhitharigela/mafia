package com.mafia.controller;

import com.mafia.dto.request.VoteCommandRequest;

import com.mafia.service.GameCommandService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/rooms")
public class GameCommandController {

    private final GameCommandService gameCommandService;

    public GameCommandController(GameCommandService gameCommandService) {
        this.gameCommandService = gameCommandService;
    }

    @PostMapping("/{roomId}/vote")
    public ResponseEntity<Map<String, String>> submitVote(
            @PathVariable String roomId,
            @RequestBody VoteCommandRequest request) {
        try {
            gameCommandService.submitVote(roomId, request.voterId(), request.targetPlayerId());
            return ResponseEntity.ok(Map.of(
                    "roomId", roomId,
                    "voterId", request.voterId(),
                    "votedFor", request.targetPlayerId(),
                    "status", "vote-recorded"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }
}