package com.mafia.service;

import com.mafia.dto.response.AggregatedGameSnapshot;
import com.mafia.entity.GameEvent;
import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.repository.GameEventRepository;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.MessageRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameStateServiceTest {

    @Mock
    GameStateRepository gameStateRepository;
    @Mock
    PlayerRepository playerRepository;
    @Mock
    GameEventRepository gameEventRepository;
    @Mock
    MessageRepository messageRepository;
    @Mock
    RoomRepository roomRepository;
    @Mock
    WinConditionService winConditionService;

    @InjectMocks
    GameStateService service;

    @Test
    void initializeGameState_savesGameStateAndEvent() {
        service.initializeGameState("room-1");

        verify(gameStateRepository).save(any(GameState.class));

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(captor.capture());
        assertEquals("GAME_INITIALIZED", captor.getValue().getEventType());
    }

    @Test
    void requireGameState_returnsGameState_whenFound() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        GameState result = service.requireGameState("room-1");

        assertNotNull(result);
    }

    @Test
    void requireGameState_throwsIllegalArgumentException_whenNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.requireGameState("room-1"));

        assertTrue(ex.getMessage().contains("Game not found"));
    }

    @ParameterizedTest
    @CsvSource({
            "SUNRISE,   SUNRISE,   true",
            "DAY_DISCUSSION, SUNRISE, true",
            "VOTING,    SUNRISE,   true",
            "GAME_OVER, SUNRISE,   true",
            "NIGHT,     SUNRISE,   false",
            "LOBBY,     SUNRISE,   false",
            "POLICE_GUESS, SUNRISE, false"
    })
    void isAtOrAfter_returnsCorrectResult(String phase, String target, boolean expected) {
        assertEquals(expected, service.isAtOrAfter(phase, target));
    }

    @Test
    void startGame_throwsIllegalStateException_whenNotEnoughPlayers() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                new Player("p1", "room-1"),
                new Player("p2", "room-1")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.startGame("room-1"));

        assertTrue(ex.getMessage().contains("Need at least 6 players"));
        verify(gameStateRepository, never()).save(any());
    }

    @Test
    void startGame_assignsRolesAndSetsNightPhase_whenEnoughPlayers() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(playerRepository.findByRoomId("room-1")).thenReturn(sixPlayers());

        service.startGame("room-1");

        assertEquals("NIGHT", gs.getPhase());
        assertEquals(1, gs.getNightNumber());
        verify(playerRepository, times(6)).save(any(Player.class));
        verify(gameStateRepository).save(gs);
        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(captor.capture());
        assertEquals("GAME_STARTED", captor.getValue().getEventType());
        assertTrue(captor.getValue().getDescription().contains("6 players"));
    }

    @Test
    void startGame_throwsIllegalArgumentException_whenGameNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.startGame("room-1"));
    }

    @Test
    void getSnapshot_hidesNightData_whenPhaseIsBeforeSunrise() {
        GameState gs = gameStateWithPhase("NIGHT");
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");
        assertNull(snap.nightKillTarget());
        assertNull(snap.policeGuessTarget());
        assertNull(snap.policeGuessCorrect());
        assertNull(snap.nightKillFailed());
    }

    @Test
    void getSnapshot_revealsNightData_whenPhaseIsAtOrAfterSunrise() {
        GameState gs = gameStateWithPhase("SUNRISE");
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertEquals("targetA", snap.nightKillTarget());
        assertNotNull(snap.nightKillFailed());
    }

    @Test
    void getSnapshot_hidesPoliceGuess_whenPoliceGuessIncorrect() {
        GameState gs = gameStateWithPhase("SUNRISE");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertNull(snap.policeGuessTarget()); // only revealed when correct
    }

    @Test
    void getSnapshot_throwsIllegalArgumentException_whenRoomNotFound() {
        GameState gs = gameStateWithPhase("LOBBY");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getSnapshot("room-1"));
    }

    @ParameterizedTest
    @CsvSource({
            "LOBBY,        start_game",
            "NIGHT,        submit_night_kill",
            "POLICE_GUESS, submit_police_guess",
            "DOCTOR_SAVE,  submit_doctor_save",
            "VOTING,       submit_vote",
            "GAME_OVER,    restart"
    })
    void getSnapshot_includesCorrectAvailableAction_forPhase(String phase, String expectedAction) {
        GameState gs = gameStateWithPhase(phase);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertTrue(snap.allowedActions().contains(expectedAction),
                "Expected action '%s' for phase '%s'".formatted(expectedAction, phase));
        assertTrue(snap.allowedActions().contains("send_message"),
                "send_message should always be present");
    }

    private GameState gameStateWithPhase(String phase) {
        GameState gs = new GameState("room-1");
        gs.setPhase(phase);
        gs.setDayNumber(1);
        gs.setNightNumber(1);
        gs.setWinner("NONE");
        gs.setAlivePlayers(List.of());
        gs.setEliminatedPlayers(List.of());
        return gs;
    }

    private Room testRoom() {
        Room r = new Room("Test Room", "host1", "CODE01", 12);
        r.setId("room-1");
        return r;
    }

    private List<Player> sixPlayers() {
        return List.of(
                new Player("p1", "room-1"),
                new Player("p2", "room-1"),
                new Player("p3", "room-1"),
                new Player("p4", "room-1"),
                new Player("p5", "room-1"),
                new Player("p6", "room-1"));
    }
}