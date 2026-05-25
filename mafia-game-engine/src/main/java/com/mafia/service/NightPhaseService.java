package com.mafia.service;

import com.mafia.client.EventServiceClient;
import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NightPhaseService {

    private final GameStateRepository gameStateRepository;
    private final PlayerRepository playerRepository;
    private final GameEventRepository gameEventRepository;
    private final EventServiceClient eventServiceClient;

    public NightPhaseService(GameStateRepository gameStateRepository,
            PlayerRepository playerRepository,
            GameEventRepository gameEventRepository,
            EventServiceClient eventServiceClient) {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.gameEventRepository = gameEventRepository;
        this.eventServiceClient = eventServiceClient;
    }

    public void submitNightKill(String roomId, String targetUsername) {
        GameState gs = requireGameState(roomId);
        if (!"NIGHT".equals(gs.getPhase())) throw new IllegalStateException("Not in NIGHT phase");
        requireAlivePlayer(targetUsername, roomId);

        Player target = playerRepository.findByUsernameAndRoomId(targetUsername, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + targetUsername));
        if ("MAFIA".equals(target.getRole())) throw new IllegalStateException("Mafia cannot target another Mafia");

        gs.setNightKillTarget(targetUsername);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);
        pushEvent(roomId, "NIGHT_KILL", "Mafia has chosen their target");
    }

    public void submitPoliceGuess(String roomId, String suspectUsername) {
        GameState gs = requireGameState(roomId);
        if (!"POLICE_GUESS".equals(gs.getPhase())) throw new IllegalStateException("Not in POLICE_GUESS phase");
        requireAlivePlayer(suspectUsername, roomId);

        Player suspect = playerRepository.findByUsernameAndRoomId(suspectUsername, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + suspectUsername));
        boolean correct = "MAFIA".equals(suspect.getRole());

        gs.setPoliceGuessTarget(suspectUsername);
        gs.setPoliceGuessCorrect(correct);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);

        String msg = correct
                ? "Police correctly identified a Mafia member: " + suspectUsername
                : "Police made a guess but it was incorrect.";
        pushEvent(roomId, "POLICE_GUESS", msg);
    }

    public void submitDoctorSave(String roomId, String savedUsername) {
        GameState gs = requireGameState(roomId);
        if (!"DOCTOR_SAVE".equals(gs.getPhase())) throw new IllegalStateException("Not in DOCTOR_SAVE phase");
        requireAlivePlayer(savedUsername, roomId);

        List<String> saves = gs.getDoctorSaveTargets() != null ? gs.getDoctorSaveTargets() : new ArrayList<>();
        if (!saves.contains(savedUsername)) saves.add(savedUsername);
        gs.setDoctorSaveTargets(saves);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);
        pushEvent(roomId, "DOCTOR_SAVE", "A doctor has chosen a save target.");
    }

    private void pushEvent(String roomId, String type, String message) {
        gameEventRepository.save(new GameEvent(roomId, type, message));
        eventServiceClient.pushEvent(roomId, type, message);
    }

    private void requireAlivePlayer(String username, String roomId) {
        Player p = playerRepository.findByUsernameAndRoomId(username, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + username));
        if (!"ALIVE".equals(p.getStatus())) throw new IllegalStateException("Player is already eliminated: " + username);
    }

    private GameState requireGameState(String roomId) {
        return gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room: " + roomId));
    }
}