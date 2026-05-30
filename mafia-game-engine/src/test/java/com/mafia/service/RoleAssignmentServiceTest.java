package com.mafia.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mafia.entity.Player;
import com.mafia.repository.PlayerRepository;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private RoleAssignmentService service;

    @Test
    void TestShouldAssignAndSaveRolesForSixPlayers() {
        List<Player> players = players("room-1", 6);

        service.assignRoles(players);

        verify(playerRepository, times(6)).save(org.mockito.ArgumentMatchers.any(Player.class));

        for (Player player : players) {
            assertNotNull(player.getRole());
        }

        Map<String, Long> counts = countRoles(players);
        assertEquals(2L, counts.getOrDefault("MAFIA", 0L));
        assertEquals(1L, counts.getOrDefault("POLICE", 0L));
        assertEquals(1L, counts.getOrDefault("DOCTOR", 0L));
        assertEquals(1L, counts.getOrDefault("SOLDIER", 0L));
        assertEquals(1L, counts.getOrDefault("VILLAGER", 0L));
    }

    @Test
    void TestShouldAssignAndSaveRolesForThreePlayers() {
        List<Player> players = players("room-1", 3);

        service.assignRoles(players);

        verify(playerRepository, times(3)).save(org.mockito.ArgumentMatchers.any(Player.class));

        Map<String, Long> counts = countRoles(players);
        assertEquals(1L, counts.getOrDefault("MAFIA", 0L));
        assertEquals(1L, counts.getOrDefault("POLICE", 0L));
        assertEquals(1L, counts.getOrDefault("SOLDIER", 0L));
        assertEquals(0L, counts.getOrDefault("DOCTOR", 0L));
        assertEquals(0L, counts.getOrDefault("VILLAGER", 0L));
    }

    @Test
    void TestShouldAssignAndSaveRolesForFourPlayers() {
        List<Player> players = players("room-1", 4);

        service.assignRoles(players);

        verify(playerRepository, times(4)).save(org.mockito.ArgumentMatchers.any(Player.class));

        Map<String, Long> counts = countRoles(players);
        assertEquals(1L, counts.getOrDefault("MAFIA", 0L));
        assertEquals(1L, counts.getOrDefault("POLICE", 0L));
        assertEquals(1L, counts.getOrDefault("DOCTOR", 0L));
        assertEquals(1L, counts.getOrDefault("SOLDIER", 0L));
        assertEquals(0L, counts.getOrDefault("VILLAGER", 0L));
    }

    @Test
    void TestShouldAssignAndSaveRolesForEightPlayers() {
        List<Player> players = players("room-1", 8);

        service.assignRoles(players);

        verify(playerRepository, times(8)).save(org.mockito.ArgumentMatchers.any(Player.class));

        Map<String, Long> counts = countRoles(players);
        assertEquals(2L, counts.getOrDefault("MAFIA", 0L));
        assertEquals(1L, counts.getOrDefault("POLICE", 0L));
        assertEquals(2L, counts.getOrDefault("DOCTOR", 0L));
        assertEquals(1L, counts.getOrDefault("SOLDIER", 0L));
        assertEquals(2L, counts.getOrDefault("VILLAGER", 0L));
    }

    @Test
    void TestShouldSaveExactlyTheMutatedPlayers() {
        List<Player> players = players("room-1", 6);

        service.assignRoles(players);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository, times(6)).save(captor.capture());

        List<Player> savedPlayers = captor.getAllValues();
        assertEquals(6, savedPlayers.size());

        for (int i = 0; i < players.size(); i++) {
            assertEquals(players.get(i), savedPlayers.get(i));
            assertNotNull(savedPlayers.get(i).getRole());
        }
    }

    private List<Player> players(String roomId, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new Player("p" + i, roomId))
                .toList();
    }

    private Map<String, Long> countRoles(List<Player> players) {
        Map<String, Long> counts = new HashMap<>();
        for (Player player : players) {
            counts.merge(player.getRole(), 1L, Long::sum);
        }
        return counts;
    }
}