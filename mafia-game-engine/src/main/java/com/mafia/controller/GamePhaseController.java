package com.mafia.controller;

import com.mafia.dto.request.DoctorSaveSubmitRequest;
import com.mafia.dto.request.NightActionSubmitRequest;
import com.mafia.dto.request.PoliceGuessSubmitRequest;
import com.mafia.service.GameLoopService;
import com.mafia.service.NightPhaseService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GamePhaseController {

    private final NightPhaseService nightPhaseService;
    private final GameLoopService gameLoopService;

    public GamePhaseController(NightPhaseService nightPhaseService,
            GameLoopService gameLoopService) {
        this.nightPhaseService = nightPhaseService;
        this.gameLoopService = gameLoopService;
    }

    @PostMapping("/{roomId}/advance-phase")
    public ResponseEntity<Map<String, String>> advancePhase(@PathVariable String roomId) {
        try {
            nightPhaseService.advancePhase(roomId);
            return ResponseEntity.ok(Map.of("roomId", roomId, "status", "phase-advanced"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @PostMapping("/{roomId}/submit-night-kill")
    public ResponseEntity<Map<String, String>> submitNightKill(
            @PathVariable String roomId,
            @RequestBody NightActionSubmitRequest req) {
        try {
            nightPhaseService.submitNightKill(roomId, req.targetPlayer());
            return ResponseEntity.ok(
                    Map.of("roomId", roomId, "target", req.targetPlayer(), "status", "recorded"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @PostMapping("/{roomId}/submit-police-guess")
    public ResponseEntity<Map<String, String>> submitPoliceGuess(
            @PathVariable String roomId,
            @RequestBody PoliceGuessSubmitRequest req) {
        try {
            nightPhaseService.submitPoliceGuess(roomId, req.suspectPlayer());
            return ResponseEntity.ok(
                    Map.of("roomId", roomId, "suspect", req.suspectPlayer(), "status", "recorded"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @PostMapping("/{roomId}/submit-doctor-save")
    public ResponseEntity<Map<String, String>> submitDoctorSave(
            @PathVariable String roomId,
            @RequestBody DoctorSaveSubmitRequest req) {
        try {
            nightPhaseService.submitDoctorSave(roomId, req.savedPlayer());
            return ResponseEntity.ok(
                    Map.of("roomId", roomId, "saved", req.savedPlayer(), "status", "recorded"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @PostMapping("/{roomId}/resolve-voting")
    public ResponseEntity<Map<String, String>> resolveVoting(@PathVariable String roomId) {
        try {
            gameLoopService.resolveVoting(roomId);
            return ResponseEntity.ok(Map.of("roomId", roomId, "status", "voting-resolved"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }
}