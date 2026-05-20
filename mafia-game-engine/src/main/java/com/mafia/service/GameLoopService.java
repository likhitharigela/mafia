package com.mafia.service;

import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import org.springframework.stereotype.Service;

@Service
public class GameLoopService {

    private final NightPhaseService nightPhaseService;
    private final VoteCountingService voteCountingService;
    private final GameStateRepository gameStateRepository;
    private final GameEventRepository gameEventRepository;

    public GameLoopService(NightPhaseService nightPhaseService,
            VoteCountingService voteCountingService,
            GameStateRepository gameStateRepository,
            GameEventRepository gameEventRepository) {
        this.nightPhaseService = nightPhaseService;
        this.voteCountingService = voteCountingService;
        this.gameStateRepository = gameStateRepository;
        this.gameEventRepository = gameEventRepository;
    }

    public void resolveVoting(String roomId) {
        GameState gs = gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"VOTING".equals(gs.getPhase())) {
            throw new IllegalStateException("Not in VOTING phase");
        }

        String target = voteCountingService.getEliminationTarget(roomId, gs.getDayNumber());
        if (target != null) {
            voteCountingService.applyElimination(roomId, target);
            gameEventRepository.save(new GameEvent(roomId, "PLAYER_ELIMINATED",
                    target + " was eliminated by village vote"));
        } else {
            gameEventRepository.save(new GameEvent(roomId, "VOTING_COMPLETE",
                    "Voting ended in a tie — no one was eliminated"));
        }

        nightPhaseService.advancePhase(roomId);
    }
}