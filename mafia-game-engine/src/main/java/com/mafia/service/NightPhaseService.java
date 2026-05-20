package com.mafia.service;

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
    private final WinConditionService winConditionService;

    public NightPhaseService(GameStateRepository gameStateRepository,
            PlayerRepository playerRepository,
            GameEventRepository gameEventRepository,
            WinConditionService winConditionService) {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.gameEventRepository = gameEventRepository;
        this.winConditionService = winConditionService;
    }

    public void submitNightKill(String roomId, String targetUsername) {
        GameState gs = requireGameState(roomId);
        if (!"NIGHT".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in NIGHT phase");
        }
        requireAlivePlayer(targetUsername, roomId);
        gs.setNightKillTarget(targetUsername);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "NIGHT_KILL",
                "Mafia has chosen their target"));
    }

    public void submitPoliceGuess(String roomId, String suspectUsername) {
        GameState gs = requireGameState(roomId);
        if (!"POLICE_GUESS".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in POLICE_GUESS phase");
        }
        requireAlivePlayer(suspectUsername, roomId);

        Player suspect = playerRepository.findByUsernameAndRoomId(suspectUsername, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + suspectUsername));
        boolean correct = "MAFIA".equals(suspect.getRole());

        gs.setPoliceGuessTarget(suspectUsername);
        gs.setPoliceGuessCorrect(correct);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);

        if (correct) {
            gameEventRepository.save(new GameEvent(roomId, "POLICE_GUESS",
                    "Police correctly identified a Mafia member: " + suspectUsername));
        } else {
            gameEventRepository.save(new GameEvent(roomId, "POLICE_GUESS",
                    "Police made a guess but it was incorrect."));
        }
    }

    public void submitDoctorSave(String roomId, String savedUsername) {
        GameState gs = requireGameState(roomId);
        if (!"DOCTOR_SAVE".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in DOCTOR_SAVE phase");
        }
        requireAlivePlayer(savedUsername, roomId);

        List<String> saves = gs.getDoctorSaveTargets();
        if (saves == null)
            saves = new ArrayList<>();
        if (!saves.contains(savedUsername))
            saves.add(savedUsername);
        gs.setDoctorSaveTargets(saves);
        gs.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(gs);

        gameEventRepository.save(new GameEvent(roomId, "DOCTOR_SAVE",
                "A doctor has chosen a save target."));
    }

    public void advancePhase(String roomId) {
        GameState gs = requireGameState(roomId);
        String current = gs.getPhase();

        switch (current) {
            case "NIGHT" -> transitionToPoliceGuess(roomId, gs);
            case "POLICE_GUESS" -> transitionToDoctorSave(roomId, gs);
            case "DOCTOR_SAVE" -> transitionToSunrise(roomId, gs);
            case "SUNRISE" -> transitionToDayDiscussion(roomId, gs);
            case "DAY_DISCUSSION" -> transitionToVoting(roomId, gs);
            case "VOTING" -> transitionAfterVoting(roomId, gs);
            case "ELIMINATION" -> checkWinAfterElimination(roomId, gs);
            case "WIN_CHECK" -> transitionToNextNight(roomId, gs);
            default -> throw new IllegalStateException("Cannot advance from phase: " + current);
        }
    }

    private void transitionToPoliceGuess(String roomId, GameState gs) {
        setPhase(gs, "POLICE_GUESS");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Night is over. Police is investigating."));
    }

    private void transitionToDoctorSave(String roomId, GameState gs) {
        setPhase(gs, "DOCTOR_SAVE");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Doctors may now choose players to save."));
    }

    private void transitionToSunrise(String roomId, GameState gs) {
        gs.setNightKillFailed(null);

        if (gs.getNightKillTarget() != null && gs.getAlivePlayers().contains(gs.getNightKillTarget())) {
            String victim = gs.getNightKillTarget();

            boolean protectedByDoctor = gs.getDoctorSaveTargets() != null
                    && gs.getDoctorSaveTargets().contains(victim);

            boolean isSoldier = playerRepository.findByUsernameAndRoomId(victim, roomId)
                    .map(p -> "SOLDIER".equals(p.getRole()))
                    .orElse(false);

            if (protectedByDoctor || isSoldier) {
                gs.setNightKillFailed(true);
            } else {
                eliminatePlayer(roomId, gs, victim);
                gs.setNightKillFailed(false);
            }
        }

        if (Boolean.TRUE.equals(gs.getPoliceGuessCorrect()) && gs.getPoliceGuessTarget() != null) {
            if (gs.getAlivePlayers().contains(gs.getPoliceGuessTarget())) {
                eliminatePlayer(roomId, gs, gs.getPoliceGuessTarget());
            }
        }

        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", winner + " wins!"));
            return;
        }

        setPhase(gs, "SUNRISE");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                buildSunriseMessage(gs)));
    }

    private void transitionToDayDiscussion(String roomId, GameState gs) {
        gs.setDayNumber(gs.getDayNumber() + 1);
        setPhase(gs, "DAY_DISCUSSION");
        gs.setNightKillTarget(null);
        gs.setPoliceGuessTarget(null);
        gs.setPoliceGuessCorrect(null);
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.setNightKillFailed(null);
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Day " + gs.getDayNumber() + " discussion begins."));
    }

    private void transitionToVoting(String roomId, GameState gs) {
        setPhase(gs, "VOTING");
        gameStateRepository.save(gs);
        gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                "Voting begins. Choose who to eliminate."));
    }

    private void transitionAfterVoting(String roomId, GameState gs) {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", winner + " wins!"));
        } else {
            gs.setNightNumber(gs.getNightNumber() + 1);
            gs.setNightKillTarget(null);
            gs.setPoliceGuessTarget(null);
            gs.setPoliceGuessCorrect(null);
            gs.setDoctorSaveTargets(new ArrayList<>());
            gs.setNightKillFailed(null);
            setPhase(gs, "NIGHT");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                    "Night " + gs.getNightNumber() + " begins."));
        }
    }

    private void checkWinAfterElimination(String roomId, GameState gs) {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", winner + " wins!"));
        } else {
            setPhase(gs, "WIN_CHECK");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                    "Win check complete. Game continues."));
        }
    }

    private void transitionToNextNight(String roomId, GameState gs) {
        String winner = winConditionService.checkWinCondition(roomId);
        if (!"NONE".equals(winner)) {
            gs.setWinner(winner);
            setPhase(gs, "GAME_OVER");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "GAME_OVER", winner + " wins!"));
        } else {
            gs.setNightNumber(gs.getNightNumber() + 1);
            gs.setDoctorSaveTargets(new ArrayList<>());
            gs.setNightKillFailed(null);
            setPhase(gs, "NIGHT");
            gameStateRepository.save(gs);
            gameEventRepository.save(new GameEvent(roomId, "PHASE_TRANSITIONED",
                    "Night " + gs.getNightNumber() + " begins."));
        }
    }

    public void eliminatePlayer(String roomId, GameState gs, String username) {
        playerRepository.findByUsernameAndRoomId(username, roomId).ifPresent(p -> {
            p.setStatus("ELIMINATED");
            p.setVoteEligibleDayNumber(gs.getDayNumber());
            playerRepository.save(p);
        });
        List<String> alive = new ArrayList<>(gs.getAlivePlayers());
        List<String> elim = new ArrayList<>(gs.getEliminatedPlayers());
        alive.remove(username);
        elim.add(username);
        gs.setAlivePlayers(alive);
        gs.setEliminatedPlayers(elim);
    }

    private void setPhase(GameState gs, String phase) {
        gs.setPhase(phase);
        gs.setPhaseStartTime(LocalDateTime.now());
        gs.setUpdatedAt(LocalDateTime.now());
    }

    private void requireAlivePlayer(String username, String roomId) {
        Player p = playerRepository.findByUsernameAndRoomId(username, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + username));
        if (!"ALIVE".equals(p.getStatus())) {
            throw new IllegalStateException("Player is already eliminated: " + username);
        }
    }

    private GameState requireGameState(String roomId) {
        return gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room: " + roomId));
    }

    private String buildSunriseMessage(GameState gs) {
        StringBuilder sb = new StringBuilder("Sunrise! ");
        if (gs.getNightKillTarget() != null) {
            if (Boolean.TRUE.equals(gs.getNightKillFailed())) {
                sb.append("An attempted kill on ").append(gs.getNightKillTarget())
                        .append(" failed last night. ");
            } else if (Boolean.FALSE.equals(gs.getNightKillFailed())) {
                sb.append(gs.getNightKillTarget()).append(" was killed during the night. ");
            } else {
                sb.append(gs.getNightKillTarget()).append(" was targeted during the night. ");
            }
        } else {
            sb.append("Nobody was killed last night. ");
        }
        if (gs.getPoliceGuessTarget() != null) {
            if (Boolean.TRUE.equals(gs.getPoliceGuessCorrect())) {
                sb.append("Police correctly identified ").append(gs.getPoliceGuessTarget())
                        .append(" as Mafia!");
            } else {
                sb.append("Police guessed ").append(gs.getPoliceGuessTarget())
                        .append(" — but they were wrong.");
            }
        }
        return sb.toString();
    }
}