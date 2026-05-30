package com.mafia.service;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.VoteRepository;

@ExtendWith(MockitoExtension.class)
class GameCommandServiceTest {

    @Mock
    private GameStateRepository gameStateRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private VoteRepository voteRepository;

    @InjectMocks
    private GameCommandService service;

    @Test
    void TestShouldSubmitVoteWhenAlivePlayerVotesInVotingPhase() {
        GameState gs = votingPhaseState("room-1", 2);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(voteRepository.existsByRoomIdAndDayNumberAndVoterId("room-1", 2, "voterA")).thenReturn(false);
        when(playerRepository.findByUsernameAndRoomId("voterA", "room-1"))
                .thenReturn(Optional.of(alivePlayer("voterA", "room-1")));

        assertDoesNotThrow(() -> service.submitVote("room-1", "voterA", "targetB"));

        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).save(voteCaptor.capture());

        Vote saved = voteCaptor.getValue();
        assertEquals("room-1", saved.getRoomId());
        assertEquals(2, saved.getDayNumber());
        assertEquals("voterA", saved.getVoterId());
        assertEquals("targetB", saved.getVotedFor());
    }

    @Test
    void TestShouldSubmitVoteWhenGhostVotesOnCurrentDay() {
        GameState gs = votingPhaseState("room-1", 3);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(voteRepository.existsByRoomIdAndDayNumberAndVoterId("room-1", 3, "voterA")).thenReturn(false);

        Player ghost = new Player("voterA", "room-1");
        ghost.setStatus("ELIMINATED");
        ghost.setVoteEligibleDayNumber(3);

        when(playerRepository.findByUsernameAndRoomId("voterA", "room-1"))
                .thenReturn(Optional.of(ghost));

        assertDoesNotThrow(() -> service.submitVote("room-1", "voterA", "targetB"));

        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).save(voteCaptor.capture());

        Vote saved = voteCaptor.getValue();
        assertEquals("room-1", saved.getRoomId());
        assertEquals(3, saved.getDayNumber());
        assertEquals("voterA", saved.getVoterId());
        assertEquals("targetB", saved.getVotedFor());
    }

    static Stream<Arguments> submitVoteExceptionScenarios() {
        return Stream.of(
                Arguments.of(
                        Named.of("TestShouldThrowExceptionWhenGameStateIsNotFound", (TestSetup) s -> {
                            when(s.gameStateRepository.findByRoomId("room-1"))
                                    .thenReturn(Optional.empty());
                        }),
                        IllegalArgumentException.class,
                        "Game not found"
                ),
                Arguments.of(
                        Named.of("TestShouldThrowExceptionWhenWrongPhase", (TestSetup) s -> {
                            GameState gs = new GameState("room-1");
                            gs.setPhase("DAY_DISCUSSION");
                            gs.setDayNumber(1);
                            when(s.gameStateRepository.findByRoomId("room-1"))
                                    .thenReturn(Optional.of(gs));
                        }),
                        IllegalStateException.class,
                        "Voting phase not active"
                ),
                Arguments.of(
                        Named.of("TestShouldThrowExceptionForDuplicateVote", (TestSetup) s -> {
                            GameState gs = votingPhaseState("room-1", 2);
                            when(s.gameStateRepository.findByRoomId("room-1"))
                                    .thenReturn(Optional.of(gs));
                            when(s.voteRepository.existsByRoomIdAndDayNumberAndVoterId("room-1", 2, "voterA"))
                                    .thenReturn(true);
                        }),
                        IllegalStateException.class,
                        "Vote already submitted for this round"
                ),
                Arguments.of(
                        Named.of("TestShouldThrowExceptionWhenVoterNotFound", (TestSetup) s -> {
                            GameState gs = votingPhaseState("room-1", 2);
                            when(s.gameStateRepository.findByRoomId("room-1"))
                                    .thenReturn(Optional.of(gs));
                            when(s.voteRepository.existsByRoomIdAndDayNumberAndVoterId("room-1", 2, "voterA"))
                                    .thenReturn(false);
                            when(s.playerRepository.findByUsernameAndRoomId("voterA", "room-1"))
                                    .thenReturn(Optional.empty());
                        }),
                        IllegalArgumentException.class,
                        "Player not found: voterA"
                ),
                Arguments.of(
                        Named.of("TestShouldThrowExceptionForDeadPlayerWithoutGhostVote", (TestSetup) s -> {
                            GameState gs = votingPhaseState("room-1", 2);
                            when(s.gameStateRepository.findByRoomId("room-1"))
                                    .thenReturn(Optional.of(gs));
                            when(s.voteRepository.existsByRoomIdAndDayNumberAndVoterId("room-1", 2, "voterA"))
                                    .thenReturn(false);

                            Player dead = new Player("voterA", "room-1");
                            dead.setStatus("ELIMINATED");
                            dead.setVoteEligibleDayNumber(null);

                            when(s.playerRepository.findByUsernameAndRoomId("voterA", "room-1"))
                                    .thenReturn(Optional.of(dead));
                        }),
                        IllegalStateException.class,
                        "Dead players cannot vote now"
                )
        );
    }

    @ParameterizedTest
    @MethodSource("submitVoteExceptionScenarios")
    void TestShouldThrowExceptionWhenInvalidVoteIsSubmitted(
            TestSetup setup,
            Class<? extends Exception> expectedException,
            String expectedMessage
    ) {
        setup.configure(this);

        Exception ex = assertThrows(expectedException,
                () -> service.submitVote("room-1", "voterA", "targetB"));

        assertEquals(expectedMessage, ex.getMessage());
        verify(voteRepository, never()).save(any(Vote.class));
    }

    private static GameState votingPhaseState(String roomId, int dayNumber) {
        GameState gs = new GameState(roomId);
        gs.setPhase("VOTING");
        gs.setDayNumber(dayNumber);
        return gs;
    }

    private static Player alivePlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ALIVE");
        return p;
    }

    @FunctionalInterface
    interface TestSetup {
        void configure(GameCommandServiceTest test);
    }
}