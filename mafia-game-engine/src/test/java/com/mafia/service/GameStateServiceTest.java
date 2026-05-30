package com.mafia.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mafia.client.EventServiceClient;
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

@ExtendWith(MockitoExtension.class)
class GameStateServiceTest {

    @Mock
    private GameStateRepository gameStateRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GameEventRepository gameEventRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private WinConditionService winConditionService;

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @InjectMocks
    private GameStateService service;

    @Test
    void TestShouldInitializeGameStateAndSaveEvent() {
        service.initializeGameState("room-1");

        ArgumentCaptor<GameState> gsCaptor = ArgumentCaptor.forClass(GameState.class);
        verify(gameStateRepository).save(gsCaptor.capture());
        assertEquals("room-1", gsCaptor.getValue().getRoomId());

        ArgumentCaptor<GameEvent> eventCaptor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(eventCaptor.capture());
        assertEquals("GAME_INITIALIZED", eventCaptor.getValue().getEventType());

        verify(eventServiceClient).pushEvent("room-1", "GAME_INITIALIZED", "Room created");
    }

    @Test
    void TestShouldReturnGameStateWhenFound() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        GameState result = service.requireGameState("room-1");

        verify(gameStateRepository).findByRoomId("room-1");
        assertNotNull(result);
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenGameNotFound() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.requireGameState("room-1")
        );

        verify(gameStateRepository).findByRoomId("room-1");
        assertTrue(ex.getMessage().contains("Game not found"));
    }

    @ParameterizedTest
    @CsvSource({
            "SUNRISE, SUNRISE, true",
            "DAY_DISCUSSION, SUNRISE, true",
            "VOTING, SUNRISE, true",
            "GAME_OVER, SUNRISE, true",
            "NIGHT, SUNRISE, false",
            "LOBBY, SUNRISE, false",
            "POLICE_GUESS, SUNRISE, false"
    })
    void TestShouldReturnCorrectResultForIsAtOrAfter(String phase, String target, boolean expected) {
        assertEquals(expected, service.isAtOrAfter(phase, target));
    }

    @Test
    void TestShouldThrowIllegalStateExceptionWhenNotEnoughPlayers() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                new Player("p1", "room-1"),
                new Player("p2", "room-1")
        ));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.startGame("room-1")
        );

        assertTrue(ex.getMessage().contains("Need at least 6 players"));
        verify(gameStateRepository, never()).save(any());
        verify(roleAssignmentService, never()).assignRoles(any());
        verify(eventServiceClient, never()).startPhaseTimer(any(), any(), anyInt());
    }

    @Test
    void TestShouldAssignRolesAndSetNightPhaseWhenEnoughPlayers() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));

        Room room = testRoom();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        List<Player> players = sixPlayers();
        when(playerRepository.findByRoomId("room-1")).thenReturn(players);

        service.startGame("room-1");

        verify(roleAssignmentService).assignRoles(players);

        assertEquals("NIGHT", gs.getPhase());
        assertEquals(1, gs.getNightNumber());
        assertNull(gs.getNightKillTarget());
        assertNull(gs.getPoliceGuessTarget());
        assertNull(gs.getPoliceGuessCorrect());
        assertNull(gs.getNightKillFailed());
        assertNotNull(gs.getDoctorSaveTargets());
        assertEquals(6, gs.getAlivePlayers().size());

        verify(gameStateRepository).save(gs);
        verify(eventServiceClient).startPhaseTimer("room-1", "NIGHT", 30);

        assertEquals("IN_GAME", room.getStatus());
        verify(roomRepository).save(room);

        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        verify(gameEventRepository).save(captor.capture());
        assertEquals("GAME_STARTED", captor.getValue().getEventType());
        assertTrue(captor.getValue().getDescription().contains("6 players"));

        verify(eventServiceClient).pushEvent("room-1", "GAME_STARTED", "Game started with 6 players");
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenGameNotFoundForStartGame() {
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.startGame("room-1")
        );

        assertTrue(ex.getMessage().contains("Game not found for room: room-1"));
        verifyNoInteractions(roleAssignmentService, roomRepository, playerRepository, eventServiceClient);
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenRoomNotFoundDuringStartGame() {
        GameState gs = new GameState("room-1");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.startGame("room-1")
        );

        assertEquals("Room not found: room-1", ex.getMessage());
        verify(playerRepository, never()).findByRoomId(any());
        verify(roleAssignmentService, never()).assignRoles(any());
    }

    @Test
    void TestShouldHideNightDataWhenPhaseIsBeforeSunrise() {
        GameState gs = gameStateWithPhase("NIGHT");
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getEvents("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getTimerRemainingSeconds("room-1")).thenReturn(0);

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertNull(snap.nightKillTarget());
        assertNull(snap.policeGuessTarget());
        assertNull(snap.policeGuessCorrect());
        assertNull(snap.nightKillFailed());
    }

    @Test
    void TestShouldRevealNightDataWhenPhaseIsAtOrAfterSunrise() {
        GameState gs = gameStateWithPhase("SUNRISE");
        gs.setNightKillTarget("targetA");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(true);
        gs.setNightKillFailed(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getEvents("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getTimerRemainingSeconds("room-1")).thenReturn(0);

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertEquals("targetA", snap.nightKillTarget());
        assertNotNull(snap.nightKillFailed());
    }

    @Test
    void TestShouldHidePoliceGuessWhenGuessIsIncorrect() {
        GameState gs = gameStateWithPhase("SUNRISE");
        gs.setPoliceGuessTarget("suspectB");
        gs.setPoliceGuessCorrect(false);

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getEvents("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getTimerRemainingSeconds("room-1")).thenReturn(0);

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertNull(snap.policeGuessTarget());
    }

    @Test
    void TestShouldThrowIllegalArgumentExceptionWhenRoomNotFound() {
        GameState gs = gameStateWithPhase("LOBBY");
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.getSnapshot("room-1")
        );

        assertEquals("Room not found: room-1", ex.getMessage());
    }

    @Test
    void TestShouldUseRemoteEventsWhenAvailable() {
        GameState gs = gameStateWithPhase("LOBBY");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getEvents("room-1")).thenReturn(
                List.of(Map.of("event", "REMOTE_EVENT", "description", "from event service", "at", "now"))
        );
        when(eventServiceClient.getTimerRemainingSeconds("room-1")).thenReturn(0);

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) snap.recentEvents();

        assertEquals(1, events.size());
        assertEquals("REMOTE_EVENT", events.get(0).get("event"));
        verify(gameEventRepository, never()).findByRoomIdOrderByCreatedAtDesc(any());
    }

    @Test
    void TestShouldFallbackToDatabaseEventsWhenRemoteEventsAreEmpty() {
        GameState gs = gameStateWithPhase("LOBBY");
        GameEvent event = new GameEvent("room-1", "DB_EVENT", "from db");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getEvents("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of(event));
        when(eventServiceClient.getTimerRemainingSeconds("room-1")).thenReturn(0);

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) snap.recentEvents();

        assertEquals(1, events.size());
        assertEquals("DB_EVENT", events.get(0).get("event"));
}

    @Test
    void TestShouldReturnPhaseEndsAtWhenTimerExists() {
        GameState gs = gameStateWithPhase("NIGHT");

        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getEvents("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getTimerRemainingSeconds("room-1")).thenReturn(25);

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertNotNull(snap.phaseEndsAt());
    }

    @ParameterizedTest
    @CsvSource({
            "LOBBY, start_game",
            "NIGHT, submit_night_kill",
            "POLICE_GUESS, submit_police_guess",
            "DOCTOR_SAVE, submit_doctor_save",
            "VOTING, submit_vote",
            "GAME_OVER, restart"
    })
    void TestShouldIncludeCorrectAvailableActionForPhase(String phase, String expectedAction) {
        GameState gs = gameStateWithPhase(phase);
        when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(testRoom()));
        when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getEvents("room-1")).thenReturn(List.of());
        when(gameEventRepository.findByRoomIdOrderByCreatedAtDesc("room-1")).thenReturn(List.of());
        when(eventServiceClient.getTimerRemainingSeconds("room-1")).thenReturn(0);

        AggregatedGameSnapshot snap = service.getSnapshot("room-1");

        assertTrue(snap.allowedActions().contains(expectedAction));
        assertTrue(snap.allowedActions().contains("send_message"));
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
                new Player("p6", "room-1")
        );
    }
}