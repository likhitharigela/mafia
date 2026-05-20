package com.mafia.service;

import com.mafia.entity.Player;
import com.mafia.repository.PlayerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WinConditionServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private WinConditionService winConditionService;

    private Player player(String username, String role, String status) {
        Player p = new Player(username, "room-1");
        p.setRole(role);
        p.setStatus(status);
        return p;
    }

    private Player aliveMafia(String username) {
        return player(username, "MAFIA", "ALIVE");
    }

    private Player aliveVillager(String username) {
        return player(username, "VILLAGER", "ALIVE");
    }

    private Player aliveSoldier(String username) {
        return player(username, "SOLDIER", "ALIVE");
    }

    private Player deadMafia(String username) {
        return player(username, "MAFIA", "ELIMINATED");
    }

    private Player deadVillager(String username) {
        return player(username, "VILLAGER", "ELIMINATED");
    }

    @Nested
    @DisplayName("VILLAGERS win — aliveMafia == 0")
    class VillagersWin {

        @Test
        @DisplayName("all mafia are eliminated, villagers remain")
        void villagersWin_mafiaEliminated() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    deadMafia("mafia1"),
                    aliveVillager("v1"),
                    aliveVillager("v2")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("VILLAGERS");
        }

        @Test
        @DisplayName("no mafia ever in the game — only villagers")
        void villagersWin_noMafiaAtAll() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveVillager("v1"),
                    aliveVillager("v2")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("VILLAGERS");
        }

        @Test
        @DisplayName("no mafia, only a soldier remaining")
        void villagersWin_noMafiaOnlySoldier() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    deadMafia("mafia1"),
                    aliveSoldier("s1")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("VILLAGERS");
        }

        @Test
        @DisplayName("empty player list — no mafia alive")
        void villagersWin_emptyRoom() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of());

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("VILLAGERS");
        }
    }

    @Nested
    @DisplayName("MAFIA win — aliveMafia >= aliveVillagers")
    class MafiaWins {

        @Test
        @DisplayName("mafia equal villagers (aliveMafia == aliveVillagers)")
        void mafiaWin_equalCount() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveVillager("v1")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("MAFIA");
        }

        @Test
        @DisplayName("mafia outnumber villagers (aliveMafia > aliveVillagers)")
        void mafiaWin_mafiaMoreThanVillagers() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveMafia("mafia2"),
                    aliveVillager("v1")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("MAFIA");
        }

        @Test
        @DisplayName("mafia win when only soldiers remain — soldiers excluded from villager count")
        void mafiaWin_onlySoldiersLeft() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveSoldier("s1")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("MAFIA");
        }

        @Test
        @DisplayName("mafia win when no villagers or soldiers alive at all")
        void mafiaWin_noVillagersAtAll() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    deadVillager("v1")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("MAFIA");
        }

        @Test
        @DisplayName("mafia equal combined count with dead villagers excluded")
        void mafiaWin_deadVillagersNotCounted() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveVillager("v1"),
                    deadVillager("v2"),
                    deadVillager("v3")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("MAFIA");
        }
    }

    @Nested
    @DisplayName("NONE — game still ongoing")
    class GameOngoing {

        @Test
        @DisplayName("mafia alive but fewer than villagers")
        void none_mafiaFewerThanVillagers() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveVillager("v1"),
                    aliveVillager("v2")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("NONE");
        }

        @Test
        @DisplayName("soldier does not count as villager — game continues")
        void none_soldierDoesNotCountAsVillager() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveVillager("v1"),
                    aliveVillager("v2"),
                    aliveSoldier("s1")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("NONE");
        }

        @Test
        @DisplayName("large game — mafia clearly outnumbered")
        void none_largeLobby() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveMafia("mafia2"),
                    aliveVillager("v1"),
                    aliveVillager("v2"),
                    aliveVillager("v3"),
                    aliveVillager("v4"),
                    aliveVillager("v5")));

            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("NONE");
        }
    }

    @Nested
    @DisplayName("Role and status filtering edge cases")
    class FilteringEdgeCases {

        @Test
        @DisplayName("eliminated mafia does not count toward aliveMafia")
        void filtering_eliminatedMafiaNotCounted() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    deadMafia("mafia1"),
                    aliveVillager("v1"),
                    aliveVillager("v2"),
                    aliveVillager("v3")));
            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("VILLAGERS");
        }

        @Test
        @DisplayName("eliminated villager does not count toward aliveVillagers")
        void filtering_eliminatedVillagerNotCounted() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    deadVillager("v1"),
                    deadVillager("v2")));
            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("MAFIA");
        }

        @Test
        @DisplayName("alive SOLDIER excluded from both mafia and villager counts")
        void filtering_soldierExcludedFromBothCounts() {
            when(playerRepository.findByRoomId("room-1")).thenReturn(List.of(
                    aliveMafia("mafia1"),
                    aliveSoldier("s1"),
                    aliveSoldier("s2"),
                    aliveVillager("v1"),
                    aliveVillager("v2"),
                    aliveVillager("v3")));
            assertThat(winConditionService.checkWinCondition("room-1")).isEqualTo("NONE");
        }
    }
}