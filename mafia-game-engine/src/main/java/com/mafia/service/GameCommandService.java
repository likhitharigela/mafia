package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.VoteRepository;
import org.springframework.stereotype.Service;

@Service
public class GameCommandService {

    private final GameStateRepository gameStateRepository;
    private final PlayerRepository playerRepository;
    private final VoteRepository voteRepository;

    public GameCommandService(GameStateRepository gameStateRepository,
            PlayerRepository playerRepository,
            VoteRepository voteRepository) {
        this.gameStateRepository = gameStateRepository;
        this.playerRepository = playerRepository;
        this.voteRepository = voteRepository;
    }

    public void submitVote(String roomId, String voterId, String votedFor) {
        GameState gs = gameStateRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (!"VOTING".equals(gs.getPhase())) {
            throw new IllegalStateException("Voting phase not active");
        }

        if (voteRepository.existsByRoomIdAndDayNumberAndVoterId(roomId, gs.getDayNumber(), voterId)) {
            throw new IllegalStateException("Vote already submitted for this round");
        }

        Player voter = playerRepository.findByUsernameAndRoomId(voterId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + voterId));

        boolean alive = "ALIVE".equals(voter.getStatus());
        boolean ghostVoteAllowed = voter.getVoteEligibleDayNumber() != null
                && voter.getVoteEligibleDayNumber() == gs.getDayNumber();

        if (!alive && !ghostVoteAllowed) {
            throw new IllegalStateException("Dead players cannot vote now");
        }

        voteRepository.save(new Vote(roomId, gs.getDayNumber(), voterId, votedFor));
    }
}