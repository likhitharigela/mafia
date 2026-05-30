package com.mafia.service;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class NightPhaseServiceTest {

    @Mock
    private GameStateRepository gameStateRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GameEventRepository gameEventRepository;

    @Mock
    private EventServiceClient eventServiceClient;

    @InjectMocks
    private NightPhaseService service;

    @Test
    void TestShouldSubmitNightKillSuccessfully() {
        GameState gs = gameState("room-1", "NIGHT");
        Player target = alivePlayer("targetA", "room-1");
        target.setRole("VILLAGER");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(target));

        assertDoesNotThrow(() -> service.submitNightKill("room-1", "targetA"));

        assertEquals("targetA", gs.getNightKillTarget());
        assertNotNull(gs.getUpdatedAt());
        verify(gameStateRepository).save(gs);

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(eventCaptor.capture());
        assertEquals("NIGHT_KILL", eventCaptor.getValue().getEventType());
        assertEquals("Mafia has chosen their target", eventCaptor.getValue().getDescription());

        verify(eventServiceClient).pushEvent("room-1", "NIGHT_KILL", "Mafia has chosen their target");
    }

    @Test
    void TestShouldThrowWhenNightKillSubmittedOutsideNightPhase() {
        GameState gs = gameState("room-1", "DAY_DISCUSSION");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.submitNightKill("room-1", "targetA")
        );

        assertEquals("Not in NIGHT phase", ex.getMessage());
        verify(playerRepository, never()).findByUsernameAndRoomId("targetA", "room-1");
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenNightKillTargetPlayerNotFound() {
        GameState gs = gameState("room-1", "NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitNightKill("room-1", "targetA")
        );

        assertEquals("Player not found: targetA", ex.getMessage());
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenNightKillTargetAlreadyEliminated() {
        GameState gs = gameState("room-1", "NIGHT");
        Player target = new Player("targetA", "room-1");
        target.setStatus("ELIMINATED");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(target));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.submitNightKill("room-1", "targetA")
        );

        assertEquals("Player is already eliminated: targetA", ex.getMessage());
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenMafiaTargetsAnotherMafia() {
        GameState gs = gameState("room-1", "NIGHT");
        Player mafiaTarget = alivePlayer("targetA", "room-1");
        mafiaTarget.setRole("MAFIA");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("targetA", "room-1")).thenReturn(Optional.of(mafiaTarget));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.submitNightKill("room-1", "targetA")
        );

        assertEquals("Mafia cannot target another Mafia", ex.getMessage());
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldSubmitPoliceGuessWhenCorrect() {
        GameState gs = gameState("room-1", "POLICE_GUESS");
        Player suspect = alivePlayer("suspectA", "room-1");
        suspect.setRole("MAFIA");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("suspectA", "room-1")).thenReturn(Optional.of(suspect));

        assertDoesNotThrow(() -> service.submitPoliceGuess("room-1", "suspectA"));

        assertEquals("suspectA", gs.getPoliceGuessTarget());
        assertEquals(true, gs.getPoliceGuessCorrect());
        assertNotNull(gs.getUpdatedAt());
        verify(gameStateRepository).save(gs);

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(eventCaptor.capture());
        assertEquals("POLICE_GUESS", eventCaptor.getValue().getEventType());
        assertEquals("Police correctly identified a Mafia member: suspectA", eventCaptor.getValue().getDescription());

        verify(eventServiceClient).pushEvent(
                "room-1",
                "POLICE_GUESS",
                "Police correctly identified a Mafia member: suspectA"
        );
    }

    @Test
    void TestShouldSubmitPoliceGuessWhenIncorrect() {
        GameState gs = gameState("room-1", "POLICE_GUESS");
        Player suspect = alivePlayer("suspectA", "room-1");
        suspect.setRole("VILLAGER");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("suspectA", "room-1")).thenReturn(Optional.of(suspect));

        assertDoesNotThrow(() -> service.submitPoliceGuess("room-1", "suspectA"));

        assertEquals("suspectA", gs.getPoliceGuessTarget());
        assertFalse(gs.getPoliceGuessCorrect());
        verify(gameStateRepository).save(gs);

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(eventCaptor.capture());
        assertEquals("POLICE_GUESS", eventCaptor.getValue().getEventType());
        assertEquals("Police made a guess but it was incorrect.", eventCaptor.getValue().getDescription());

        verify(eventServiceClient).pushEvent(
                "room-1",
                "POLICE_GUESS",
                "Police made a guess but it was incorrect."
        );
    }

    @Test
    void TestShouldThrowWhenPoliceGuessSubmittedOutsidePoliceGuessPhase() {
        GameState gs = gameState("room-1", "NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.submitPoliceGuess("room-1", "suspectA")
        );

        assertEquals("Not in POLICE_GUESS phase", ex.getMessage());
        verify(playerRepository, never()).findByUsernameAndRoomId("suspectA", "room-1");
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenPoliceGuessPlayerNotFound() {
        GameState gs = gameState("room-1", "POLICE_GUESS");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("suspectA", "room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitPoliceGuess("room-1", "suspectA")
        );

        assertEquals("Player not found: suspectA", ex.getMessage());
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenPoliceGuessTargetsEliminatedPlayer() {
        GameState gs = gameState("room-1", "POLICE_GUESS");
        Player suspect = new Player("suspectA", "room-1");
        suspect.setStatus("ELIMINATED");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("suspectA", "room-1")).thenReturn(Optional.of(suspect));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.submitPoliceGuess("room-1", "suspectA")
        );

        assertEquals("Player is already eliminated: suspectA", ex.getMessage());
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldSubmitDoctorSaveSuccessfully() {
        GameState gs = gameState("room-1", "DOCTOR_SAVE");
        gs.setDoctorSaveTargets(new ArrayList<>());
        Player saved = alivePlayer("playerA", "room-1");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("playerA", "room-1")).thenReturn(Optional.of(saved));

        assertDoesNotThrow(() -> service.submitDoctorSave("room-1", "playerA"));

        assertEquals(1, gs.getDoctorSaveTargets().size());
        assertEquals("playerA", gs.getDoctorSaveTargets().get(0));
        assertNotNull(gs.getUpdatedAt());
        verify(gameStateRepository).save(gs);

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(eventCaptor.capture());
        assertEquals("DOCTOR_SAVE", eventCaptor.getValue().getEventType());
        assertEquals("A doctor has chosen a save target.", eventCaptor.getValue().getDescription());

        verify(eventServiceClient).pushEvent(
                "room-1",
                "DOCTOR_SAVE",
                "A doctor has chosen a save target."
        );
    }

    @Test
    void TestShouldNotDuplicateDoctorSaveTarget() {
        GameState gs = gameState("room-1", "DOCTOR_SAVE");
        gs.setDoctorSaveTargets(new ArrayList<>());
        gs.getDoctorSaveTargets().add("playerA");

        Player saved = alivePlayer("playerA", "room-1");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("playerA", "room-1")).thenReturn(Optional.of(saved));

        assertDoesNotThrow(() -> service.submitDoctorSave("room-1", "playerA"));

        assertEquals(1, gs.getDoctorSaveTargets().size());
        assertEquals("playerA", gs.getDoctorSaveTargets().get(0));
        verify(gameStateRepository).save(gs);
    }

    @Test
    void TestShouldCreateDoctorSaveListWhenInitiallyNull() {
        GameState gs = gameState("room-1", "DOCTOR_SAVE");
        gs.setDoctorSaveTargets(null);

        Player saved = alivePlayer("playerA", "room-1");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("playerA", "room-1")).thenReturn(Optional.of(saved));

        assertDoesNotThrow(() -> service.submitDoctorSave("room-1", "playerA"));

        assertNotNull(gs.getDoctorSaveTargets());
        assertEquals(1, gs.getDoctorSaveTargets().size());
        assertEquals("playerA", gs.getDoctorSaveTargets().get(0));
        verify(gameStateRepository).save(gs);
    }

    @Test
    void TestShouldThrowWhenDoctorSaveSubmittedOutsideDoctorSavePhase() {
        GameState gs = gameState("room-1", "NIGHT");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.submitDoctorSave("room-1", "playerA")
        );

        assertEquals("Not in DOCTOR_SAVE phase", ex.getMessage());
        verify(playerRepository, never()).findByUsernameAndRoomId("playerA", "room-1");
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenDoctorSavePlayerNotFound() {
        GameState gs = gameState("room-1", "DOCTOR_SAVE");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("playerA", "room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitDoctorSave("room-1", "playerA")
        );

        assertEquals("Player not found: playerA", ex.getMessage());
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenDoctorSaveTargetsEliminatedPlayer() {
        GameState gs = gameState("room-1", "DOCTOR_SAVE");
        Player saved = new Player("playerA", "room-1");
        saved.setStatus("ELIMINATED");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByUsernameAndRoomId("playerA", "room-1")).thenReturn(Optional.of(saved));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.submitDoctorSave("room-1", "playerA")
        );

        assertEquals("Player is already eliminated: playerA", ex.getMessage());
        verify(gameStateRepository, never()).save(gs);
        verifyNoInteractions(gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenGameStateNotFoundForNightKill() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitNightKill("room-1", "targetA")
        );

        assertEquals("Game not found for room: room-1", ex.getMessage());
        verifyNoInteractions(playerRepository, gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenGameStateNotFoundForPoliceGuess() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitPoliceGuess("room-1", "suspectA")
        );

        assertEquals("Game not found for room: room-1", ex.getMessage());
        verifyNoInteractions(playerRepository, gameEventRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowWhenGameStateNotFoundForDoctorSave() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitDoctorSave("room-1", "playerA")
        );

        assertEquals("Game not found for room: room-1", ex.getMessage());
        verifyNoInteractions(playerRepository, gameEventRepository, eventServiceClient);
    }

    private GameState gameState(String roomId, String phase) {
        GameState gs = new GameState(roomId);
        gs.setPhase(phase);
        return gs;
    }

    private Player alivePlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ALIVE");
        return p;
    }
}