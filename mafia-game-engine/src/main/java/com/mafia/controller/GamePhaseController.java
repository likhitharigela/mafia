package com.mafia.controller;

import com.mafia.dto.request.DoctorSaveSubmitRequest;
import com.mafia.dto.request.NightActionSubmitRequest;
import com.mafia.dto.request.PoliceGuessSubmitRequest;
import com.mafia.service.GameLoopService;
import com.mafia.service.NightPhaseService;
import com.mafia.service.PhaseTransitionService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GamePhaseController {

    private final NightPhaseService nightPhaseService;
    private final PhaseTransitionService phaseTransitionService;
    private final GameLoopService gameLoopService;

    public GamePhaseController(NightPhaseService nightPhaseService,
            PhaseTransitionService phaseTransitionService,
            GameLoopService gameLoopService) {
        this.nightPhaseService = nightPhaseService;
        this.phaseTransitionService = phaseTransitionService;
        this.gameLoopService = gameLoopService;
    }

    @PostMapping("/{roomId}/advance-phase")
    public ResponseEntity<Map<String, String>> advancePhase(@PathVariable String roomId) {
        return handle(roomId, () -> phaseTransitionService.advancePhase(roomId), "phase-advanced");
    }

    @PostMapping("/{roomId}/submit-night-kill")
    public ResponseEntity<Map<String, String>> submitNightKill(@PathVariable String roomId, @RequestBody NightActionSubmitRequest req) {
        return handle(roomId, () -> nightPhaseService.submitNightKill(roomId, req.targetPlayer()), "recorded");
    }

    @PostMapping("/{roomId}/submit-police-guess")
    public ResponseEntity<Map<String, String>> submitPoliceGuess(@PathVariable String roomId, @RequestBody PoliceGuessSubmitRequest req) {
        return handle(roomId, () -> nightPhaseService.submitPoliceGuess(roomId, req.suspectPlayer()), "recorded");
    }

    @PostMapping("/{roomId}/submit-doctor-save")
    public ResponseEntity<Map<String, String>> submitDoctorSave(@PathVariable String roomId, @RequestBody DoctorSaveSubmitRequest req) {
        return handle(roomId, () -> nightPhaseService.submitDoctorSave(roomId, req.savedPlayer()), "recorded");
    }

    @PostMapping("/{roomId}/resolve-voting")
    public ResponseEntity<Map<String, String>> resolveVoting(@PathVariable String roomId) {
        return handle(roomId, () -> gameLoopService.resolveVoting(roomId), "voting-resolved");
    }

    private ResponseEntity<Map<String, String>> handle(String roomId, Runnable action, String successStatus) {
        try {
            action.run();
            return ResponseEntity.ok(Map.of("roomId", roomId, "status", successStatus));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Internal server error"));
        }
    }
}