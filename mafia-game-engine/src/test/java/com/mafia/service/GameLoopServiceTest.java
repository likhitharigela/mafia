    package com.mafia.service;

    import java.util.Optional;

    import static org.junit.jupiter.api.Assertions.assertEquals;
    import static org.junit.jupiter.api.Assertions.assertThrows;
    import static org.junit.jupiter.api.Assertions.assertTrue;
    import org.junit.jupiter.api.Test;
    import org.junit.jupiter.api.extension.ExtendWith;
    import org.mockito.ArgumentCaptor;
    import static org.mockito.ArgumentMatchers.any;
    import org.mockito.InjectMocks;
    import org.mockito.Mock;
    import static org.mockito.Mockito.never;
    import static org.mockito.Mockito.verify;
    import static org.mockito.Mockito.verifyNoInteractions;
    import static org.mockito.Mockito.when;
    import org.mockito.junit.jupiter.MockitoExtension;

    import com.mafia.client.EventServiceClient;
    import com.mafia.entity.GameEvent;
    import com.mafia.entity.GameState;
    import com.mafia.repository.GameEventRepository;
    import com.mafia.repository.GameStateRepository;

    @ExtendWith(MockitoExtension.class)
    class GameLoopServiceTest {

        @Mock
        private PhaseTransitionService phaseTransitionService;

        @Mock
        private VoteCountingService voteCountingService;

        @Mock
        private GameStateRepository gameStateRepository;

        @Mock
        private GameEventRepository gameEventRepository;

        @Mock
        private EventServiceClient eventServiceClient;

        @InjectMocks
        private GameLoopService service;

        @Test
        void TestShouldResolveVotingAndEliminatePlayerWithMajorityVotes() {
            GameState gs = votingState("room-1", 2);
            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(voteCountingService.getEliminationTarget("room-1", 2)).thenReturn("targetA");

            service.resolveVoting("room-1");

            verify(voteCountingService).applyElimination("room-1", "targetA");

            ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
            verify(gameEventRepository).save(eventCaptor.capture());
            GameEvent saved = eventCaptor.getValue();

            assertEquals("PLAYER_ELIMINATED", saved.getEventType());
            assertTrue(saved.getDescription().contains("targetA"));

            verify(eventServiceClient).pushEvent("room-1", "PLAYER_ELIMINATED",
                    "targetA was eliminated by village vote");
            verify(phaseTransitionService).advancePhase("room-1");
        }

        @Test
        void TestShouldSaveTieEventAndAdvancePhaseWhenNoMajority() {
            GameState gs = votingState("room-1", 2);
            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(voteCountingService.getEliminationTarget("room-1", 2)).thenReturn(null);

            service.resolveVoting("room-1");

            verify(voteCountingService, never()).applyElimination(any(), any());

            ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
            verify(gameEventRepository).save(eventCaptor.capture());
            GameEvent saved = eventCaptor.getValue();

            assertEquals("VOTING_COMPLETE", saved.getEventType());
            assertTrue(saved.getDescription().contains("tie"));

            verify(eventServiceClient).pushEvent(
                    "room-1",
                    "VOTING_COMPLETE",
                    "Voting ended in a tie — no one was eliminated"
            );
            verify(phaseTransitionService).advancePhase("room-1");
        }

        @Test
        void TestShouldAlwaysAdvancePhaseRegardlessOfElimination() {
            GameState gs = votingState("room-1", 1);
            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(voteCountingService.getEliminationTarget("room-1", 1)).thenReturn("targetA");

            service.resolveVoting("room-1");

            verify(phaseTransitionService).advancePhase("room-1");
        }

        @Test
        void TestShouldThrowIllegalArgumentExceptionWhenGameNotFound() {
            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.resolveVoting("room-1")
            );

            assertEquals("Game not found", ex.getMessage());
            verifyNoInteractions(voteCountingService, phaseTransitionService, gameEventRepository, eventServiceClient);
        }

        @Test
        void TestShouldThrowIllegalStateExceptionWhenNotInVotingPhase() {
            GameState gs = new GameState("room-1");
            gs.setPhase("NIGHT");
            gs.setDayNumber(1);
            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.resolveVoting("room-1")
            );

            assertEquals("Not in VOTING phase", ex.getMessage());
            verifyNoInteractions(voteCountingService, phaseTransitionService, gameEventRepository, eventServiceClient);
        }

        private static GameState votingState(String roomId, int dayNumber) {
            GameState gs = new GameState(roomId);
            gs.setPhase("VOTING");
            gs.setDayNumber(dayNumber);
            return gs;
        }
    }