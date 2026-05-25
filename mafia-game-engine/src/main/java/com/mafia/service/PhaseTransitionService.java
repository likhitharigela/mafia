package com.mafia.service;

import com.mafia.client.EventServiceClient;
import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

@Service
public class PhaseTransitionService {

    private static final int PHASE_TIMER_SECONDS = 30;

    private final GameStateRepository gameStateRepository;
    private final PlayerRepository playerRepository;
    private final GameEventRepository gameEventRepository;
    private final WinConditionService winConditionService;
    private final EventServiceClient eventServiceClient;

    public PhaseTransitionService(GameStateRepository gameStateRepository,PlayerRepository playerRepository,GameEventRepository gameEventRepository,
    WinConditionService winConditionService,EventServiceClient eventServiceClient) 
    {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.gameEventRepository = gameEventRepository;
        this.winConditionService = winConditionService;
        this.eventServiceClient = eventServiceClient;
    }

    public void advancePhase(String roomId) 
    {
        GameState gs = requireGameState(roomId);
        switch (gs.getPhase()) 
        {
            case "NIGHT" -> transitionToPoliceGuess(roomId, gs);
            case "POLICE_GUESS" -> transitionToDoctorSave(roomId, gs);
            case "DOCTOR_SAVE" -> transitionToSunrise(roomId, gs);
            case "SUNRISE" -> transitionToDayDiscussion(roomId, gs);
            case "DAY_DISCUSSION" -> transitionToVoting(roomId, gs);
            case "VOTING" -> transitionAfterVoting(roomId, gs);
            case "ELIMINATION" -> checkWinAfterElimination(roomId, gs);
            case "WIN_CHECK" -> transitionToNextNight(roomId, gs);
            default -> throw new IllegalStateException("Cannot advance from phase: " + gs.getPhase());
        }
    }

    private void transitionToPoliceGuess(String roomId, GameState gs) 
    {
        setPhaseAndSave(roomId, gs, "POLICE_GUESS");
        pushEvent(roomId, "PHASE_TRANSITIONED", "Night is over. Police is investigating.");
    }

    private void transitionToDoctorSave(String roomId, GameState gs) 
    {
        setPhaseAndSave(roomId, gs, "DOCTOR_SAVE");
        pushEvent(roomId, "PHASE_TRANSITIONED", "Doctors may now choose players to save.");
    }

    private void transitionToSunrise(String roomId, GameState gs) 
    {
        gs.setNightKillFailed(null);
        resolveNightKill(roomId, gs);
        resolvePoliceGuess(roomId, gs);

        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) 
        {
            endGame(roomId, gs, winner);
            return;
        }

        setPhaseAndSave(roomId, gs, "SUNRISE");
        pushEvent(roomId, "PHASE_TRANSITIONED", buildSunriseMessage(gs));
    }

    private void transitionToDayDiscussion(String roomId, GameState gs) 
    {
        gs.setDayNumber(gs.getDayNumber() + 1);
        resetNightState(gs);
        setPhaseAndSave(roomId, gs, "DAY_DISCUSSION");
        pushEvent(roomId, "PHASE_TRANSITIONED", "Day " + gs.getDayNumber() + " discussion begins.");
    }

    private void transitionToVoting(String roomId, GameState gs) 
    {
        setPhaseAndSave(roomId, gs, "VOTING");
        pushEvent(roomId, "PHASE_TRANSITIONED", "Voting begins. Choose who to eliminate.");
    }

    private void transitionAfterVoting(String roomId, GameState gs) 
    {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) 
        {
            endGame(roomId, gs, winner);
            return;
        }
        gs.setNightNumber(gs.getNightNumber() + 1);
        resetNightState(gs);
        setPhaseAndSave(roomId, gs, "NIGHT");
        pushEvent(roomId, "PHASE_TRANSITIONED", "Night " + gs.getNightNumber() + " begins.");
    }

    private void checkWinAfterElimination(String roomId, GameState gs) 
    {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) 
        {
            endGame(roomId, gs, winner);
            return;
        }
        setPhaseAndSave(roomId, gs, "WIN_CHECK");
        pushEvent(roomId, "PHASE_TRANSITIONED", "Win check complete. Game continues.");
    }

    private void transitionToNextNight(String roomId, GameState gs) 
    {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) 
        {
            endGame(roomId, gs, winner);
            return;
        }
        gs.setNightNumber(gs.getNightNumber() + 1);
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setNightKillFailed(null);
        setPhaseAndSave(roomId, gs, "NIGHT");
        pushEvent(roomId, "PHASE_TRANSITIONED", "Night " + gs.getNightNumber() + " begins.");
    }

    private void resolveNightKill(String roomId, GameState gs) 
    {
        String victim = gs.getNightKillTarget();
        if (victim == null || !gs.getAlivePlayers().contains(victim)) return;

        boolean protectedByDoctor = gs.getDoctorSaveTargets() != null && gs.getDoctorSaveTargets().contains(victim);
        boolean isSoldier = playerRepository.findByUsernameAndRoomId(victim, roomId)
                .map(p -> "SOLDIER".equals(p.getRole())).orElse(false);

        if (protectedByDoctor || isSoldier) {
            gs.setNightKillFailed(true);
        } else {
            eliminatePlayer(roomId, gs, victim);
            gs.setNightKillFailed(false);
        }
    }

    private void resolvePoliceGuess(String roomId, GameState gs) 
{
        if (!Boolean.TRUE.equals(gs.getPoliceGuessCorrect()) || gs.getPoliceGuessTarget() == null) return;
        if (gs.getAlivePlayers().contains(gs.getPoliceGuessTarget())) {
            eliminatePlayer(roomId, gs, gs.getPoliceGuessTarget());
        }
    }

    private void endGame(String roomId, GameState gs, String winner) 
    {
        gs.setWinner(winner);
        setPhaseAndSave(roomId, gs, "GAME_OVER");
        pushEvent(roomId, "GAME_OVER", winner + " wins!");
    }

    private void resetNightState(GameState gs) 
    {
        gs.setNightKillTarget(null);
        gs.setPoliceGuessTarget(null);
        gs.setPoliceGuessCorrect(null);
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setNightKillFailed(null);
    }

    public void eliminatePlayer(String roomId, GameState gs, String username) 
    {
        playerRepository.findByUsernameAndRoomId(username, roomId).ifPresent(p -> {
            p.setStatus("ELIMINATED");
            p.setVoteEligibleDayNumber(gs.getDayNumber());
            playerRepository.save(p);
        });
        java.util.List<String> alive = new ArrayList<>(gs.getAlivePlayers());
        java.util.List<String> elim = new ArrayList<>(gs.getEliminatedPlayers());
        alive.remove(username);
        elim.add(username);
        gs.setAlivePlayers(alive);
        gs.setEliminatedPlayers(elim);
    }

    private void setPhaseAndSave(String roomId, GameState gs, String phase) 
    {
        gs.setPhase(phase);
        gs.setPhaseStartTime(LocalDateTime.now());
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);
        if ("GAME_OVER".equals(phase)) 
        {
            eventServiceClient.cancelPhaseTimer(roomId);
            return;
        }
        eventServiceClient.startPhaseTimer(roomId, phase, PHASE_TIMER_SECONDS);
    }

    private void pushEvent(String roomId, String type, String message) 
    {
        gameEventRepository.save(new GameEvent(roomId, type, message));
        eventServiceClient.pushEvent(roomId, type, message);
    }

    private GameState requireGameState(String roomId) 
    {
        return gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room: " + roomId));
    }

    private String buildSunriseMessage(GameState gs) 
    {
        StringBuilder sb = new StringBuilder("Sunrise! ");
        if (gs.getNightKillTarget() != null) {
            if (Boolean.TRUE.equals(gs.getNightKillFailed())) 
            {
                sb.append("An attempted kill on ").append(gs.getNightKillTarget()).append(" failed last night. ");
            } 
            else if (Boolean.FALSE.equals(gs.getNightKillFailed())) 
            {
                sb.append(gs.getNightKillTarget()).append(" was killed during the night. ");
            } 
            else 
            {
                sb.append("A player was targeted during the night. ");
            }
        } else 
        {
            sb.append("Nobody was killed last night. ");
        }
        if (gs.getPoliceGuessTarget() != null) {
            if (Boolean.TRUE.equals(gs.getPoliceGuessCorrect())) 
            {
                sb.append("Police correctly identified ").append(gs.getPoliceGuessTarget()).append(" as Mafia!");
            } 
            else 
            {
                sb.append("Police guessed, but they were wrong.");
            }
        }
        return sb.toString();
    }
}