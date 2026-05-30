package com.mafia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.mafia.entity.Player;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;

@ExtendWith(MockitoExtension.class)
class PhaseTransitionServiceTest {

    @Mock
    private GameStateRepository gameStateRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GameEventRepository gameEventRepository;

    @Mock
    private WinConditionService winConditionService;

    @Mock
    private EventServiceClient eventServiceClient;

    @InjectMocks
    private PhaseTransitionService service;

    @Test
    void TestShouldThrowWhenGameStateNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.advancePhase("room-1")
        );

        assertEquals("Game not found for room: room-1", ex.getMessage());
        verifyNoInteractions(playerRepository, gameEventRepository, winConditionService, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenPhaseCannotAdvance() {
        GameState gs = state("room-1", "LOBBY");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.advancePhase("room-1")
        );

        assertEquals("Cannot advance from phase: LOBBY", ex.getMessage());
        verify(gameStateRepository, never()).save(any());
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldTransitionFromNightToPoliceGuess() {
        GameState gs = state("room-1", "NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("POLICE_GUESS", gs.getPhase());
        verify(gameStateRepository).save(gs);
        verify(eventServiceClient).startPhaseTimer("room-1", "POLICE_GUESS", 30);

        assertLastEvent("PHASE_TRANSITIONED", "Night is over. Police is investigating.");
        verify(eventServiceClient).pushEvent("room-1", "PHASE_TRANSITIONED",
                "Night is over. Police is investigating.");
    }

    @Test
    void TestShouldTransitionFromPoliceGuessToDoctorSave() {
        GameState gs = state("room-1", "POLICE_GUESS");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("DOCTOR_SAVE", gs.getPhase());
        verify(eventServiceClient).startPhaseTimer("room-1", "DOCTOR_SAVE", 30);
        assertLastEvent("PHASE_TRANSITIONED", "Doctors may now choose players to save.");
    }

    @Test
    void TestShouldTransitionFromDoctorSaveToSunriseWhenNoWinner() {
        GameState gs = state("room-1", "DOCTOR_SAVE");
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(false);
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.getAlivePlayers().add("targetA");

        Player target = alivePlayer("targetA", "room-1");
        target.setRole("VILLAGER");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(target));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("SUNRISE", gs.getPhase());
        assertFalse(gs.getNightKillFailed());
        assertFalse(gs.getAlivePlayers().contains("targetA"));
        assertTrue(gs.getEliminatedPlayers().contains("targetA"));

        verify(playerRepository).save(target);
        verify(eventServiceClient).startPhaseTimer("room-1", "SUNRISE", 30);

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(captor.capture());
        GameEvent saved = captor.getValue();
        assertEquals("PHASE_TRANSITIONED", saved.getEventType());
        assertTrue(saved.getDescription().contains("Sunrise!"));
    }

    @Test
    void TestShouldFailNightKillWhenDoctorProtectedVictim() {
        GameState gs = state("room-1", "DOCTOR_SAVE");
        gs.setNightKillTarget("targetA");
        gs.setDoctorSaveTargets(new ArrayList<>(List.of("targetA")));
        gs.getAlivePlayers().add("targetA");

        Player target = alivePlayer("targetA", "room-1");
        target.setRole("VILLAGER");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(target));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("SUNRISE", gs.getPhase());
        assertTrue(gs.getNightKillFailed());
        assertTrue(gs.getAlivePlayers().contains("targetA"));
        assertFalse(gs.getEliminatedPlayers().contains("targetA"));
        verify(playerRepository, never()).save(target);
    }

    @Test
    void TestShouldFailNightKillWhenVictimIsSoldier() {
        GameState gs = state("room-1", "DOCTOR_SAVE");
        gs.setNightKillTarget("targetA");
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.getAlivePlayers().add("targetA");

        Player target = alivePlayer("targetA", "room-1");
        target.setRole("SOLDIER");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(target));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("SUNRISE", gs.getPhase());
        assertTrue(gs.getNightKillFailed());
        assertTrue(gs.getAlivePlayers().contains("targetA"));
        verify(playerRepository, never()).save(target);
    }

    @Test
    void TestShouldEndGameFromDoctorSaveWhenWinnerFound() {
        GameState gs = state("room-1", "DOCTOR_SAVE");
        gs.setNightKillTarget("targetA");
        gs.getAlivePlayers().add("targetA");

        Player target = alivePlayer("targetA", "room-1");
        target.setRole("VILLAGER");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(target));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("MAFIA");

        service.advancePhase("room-1");

        assertEquals("GAME_OVER", gs.getPhase());
        assertEquals("MAFIA", gs.getWinner());
        verify(eventServiceClient).cancelPhaseTimer("room-1");
        verify(eventServiceClient).pushEvent("room-1", "GAME_OVER", "MAFIA wins!");
    }

    @Test
    void TestShouldTransitionFromSunriseToDayDiscussion() {
        GameState gs = state("room-1", "SUNRISE");
        gs.setDayNumber(1);
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setDoctorSaveTargets(new ArrayList<>(List.of("savedA")));
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("DAY_DISCUSSION", gs.getPhase());
        assertEquals(2, gs.getDayNumber());
        assertEquals(null, gs.getNightKillTarget());
        assertEquals(null, gs.getPoliceGuessTarget());
        assertEquals(null, gs.getPoliceGuessCorrect());
        assertEquals(null, gs.getNightKillFailed());
        assertTrue(gs.getDoctorSaveTargets().isEmpty());

        verify(eventServiceClient).startPhaseTimer("room-1", "DAY_DISCUSSION", 30);
        assertLastEvent("PHASE_TRANSITIONED", "Day 2 discussion begins.");
    }

    @Test
    void TestShouldTransitionFromDayDiscussionToVoting() {
        GameState gs = state("room-1", "DAY_DISCUSSION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        service.advancePhase("room-1");

        assertEquals("VOTING", gs.getPhase());
        verify(eventServiceClient).startPhaseTimer("room-1", "VOTING", 30);
        assertLastEvent("PHASE_TRANSITIONED", "Voting begins. Choose who to eliminate.");
    }

    @Test
    void TestShouldTransitionFromVotingToNightWhenNoWinner() {
        GameState gs = state("room-1", "VOTING");
        gs.setNightNumber(1);
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setDoctorSaveTargets(new ArrayList<>(List.of("savedA")));
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("NIGHT", gs.getPhase());
        assertEquals(2, gs.getNightNumber());
        assertEquals(null, gs.getNightKillTarget());
        assertEquals(null, gs.getPoliceGuessTarget());
        assertEquals(null, gs.getPoliceGuessCorrect());
        assertEquals(null, gs.getNightKillFailed());
        assertTrue(gs.getDoctorSaveTargets().isEmpty());

        verify(eventServiceClient).startPhaseTimer("room-1", "NIGHT", 30);
        assertLastEvent("PHASE_TRANSITIONED", "Night 2 begins.");
    }

    @Test
    void TestShouldEndGameFromVotingWhenWinnerFound() {
        GameState gs = state("room-1", "VOTING");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("VILLAGERS");

        service.advancePhase("room-1");

        assertEquals("GAME_OVER", gs.getPhase());
        assertEquals("VILLAGERS", gs.getWinner());
        verify(eventServiceClient).cancelPhaseTimer("room-1");
        assertLastEvent("GAME_OVER", "VILLAGERS wins!");
    }

    @Test
    void TestShouldTransitionFromEliminationToWinCheckWhenNoWinner() {
        GameState gs = state("room-1", "ELIMINATION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("WIN_CHECK", gs.getPhase());
        verify(eventServiceClient).startPhaseTimer("room-1", "WIN_CHECK", 30);
        assertLastEvent("PHASE_TRANSITIONED", "Win check complete. Game continues.");
    }

    @Test
    void TestShouldEndGameFromEliminationWhenWinnerFound() {
        GameState gs = state("room-1", "ELIMINATION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("MAFIA");

        service.advancePhase("room-1");

        assertEquals("GAME_OVER", gs.getPhase());
        assertEquals("MAFIA", gs.getWinner());
        verify(eventServiceClient).cancelPhaseTimer("room-1");
        assertLastEvent("GAME_OVER", "MAFIA wins!");
    }

    @Test
    void TestShouldTransitionFromWinCheckToNextNightWhenNoWinner() {
        GameState gs = state("room-1", "WIN_CHECK");
        gs.setNightNumber(2);
        gs.setDoctorSaveTargets(new ArrayList<>(List.of("savedA")));
        gs.setNightKillFailed(true);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("NONE");

        service.advancePhase("room-1");

        assertEquals("NIGHT", gs.getPhase());
        assertEquals(3, gs.getNightNumber());
        assertTrue(gs.getDoctorSaveTargets().isEmpty());
        assertEquals(null, gs.getNightKillFailed());

        verify(eventServiceClient).startPhaseTimer("room-1", "NIGHT", 30);
        assertLastEvent("PHASE_TRANSITIONED", "Night 3 begins.");
    }

    @Test
    void TestShouldEndGameFromWinCheckWhenWinnerFound() {
        GameState gs = state("room-1", "WIN_CHECK");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(winConditionService.checkWinCondition("room-1")).thenReturn("VILLAGERS");

        service.advancePhase("room-1");

        assertEquals("GAME_OVER", gs.getPhase());
        assertEquals("VILLAGERS", gs.getWinner());
        verify(eventServiceClient).cancelPhaseTimer("room-1");
        assertLastEvent("GAME_OVER", "VILLAGERS wins!");
    }

    @Test
    void TestShouldEliminatePlayerAndUpdateLists() {
        GameState gs = state("room-1", "NIGHT");
        gs.setDayNumber(2);
        gs.setAlivePlayers(new ArrayList<>(List.of("p1", "p2", "p3")));
        gs.setEliminatedPlayers(new ArrayList<>(List.of("p4")));

        Player player = alivePlayer("p2", "room-1");
        when(playerRepository.findByUsernameAndRoomId("p2", "room-1")).thenReturn(Optional.of(player));

        service.eliminatePlayer("room-1", gs, "p2");

        assertEquals("ELIMINATED", player.getStatus());
        assertEquals(2, player.getVoteEligibleDayNumber());
        assertFalse(gs.getAlivePlayers().contains("p2"));
        assertTrue(gs.getEliminatedPlayers().contains("p2"));
        verify(playerRepository).save(player);
    }

    @Test
    void TestShouldUpdateListsEvenWhenPlayerEntityNotFoundDuringElimination() {
        GameState gs = state("room-1", "NIGHT");
        gs.setAlivePlayers(new ArrayList<>(List.of("p1", "p2")));
        gs.setEliminatedPlayers(new ArrayList<>());

        when(playerRepository.findByUsernameAndRoomId("p2", "room-1")).thenReturn(Optional.empty());

        service.eliminatePlayer("room-1", gs, "p2");

        assertFalse(gs.getAlivePlayers().contains("p2"));
        assertTrue(gs.getEliminatedPlayers().contains("p2"));
        verify(playerRepository, never()).save(any());
    }

    private GameState state(String roomId, String phase) {
        GameState gs = new GameState(roomId);
        gs.setPhase(phase);
        gs.setDayNumber(1);
        gs.setNightNumber(1);
        gs.setWinner("NONE");
        gs.setAlivePlayers(new ArrayList<>());
        gs.setEliminatedPlayers(new ArrayList<>());
        gs.setDoctorSaveTargets(new ArrayList<>());
        return gs;
    }

    private Player alivePlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ALIVE");
        return p;
    }

    private void assertLastEvent(String expectedType, String expectedMessage) {
        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(captor.capture());
        GameEvent event = captor.getValue();
        assertEquals(expectedType, event.getEventType());
        assertEquals(expectedMessage, event.getDescription());
    }
}