package com.mafia.service;

import com.mafia.entity.GameState;
import com.mafia.entity.Player;
import com.mafia.entity.Vote;
import com.mafia.repository.GameStateRepository;
import com.mafia.repository.PlayerRepository;
import com.mafia.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteCountingServiceTest {

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GameStateRepository gameStateRepository;

    @InjectMocks
    private VoteCountingService voteCountingService;

    private Vote voteFor(String votedFor) {
        Vote v = new Vote();
        v.setVotedFor(votedFor);
        return v;
    }

    private Player alivePlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ALIVE");
        return p;
    }

    private Player eliminatedPlayer(String username, String roomId) {
        Player p = new Player(username, roomId);
        p.setStatus("ELIMINATED");
        return p;
    }

    private GameState gameStateWith(List<String> alive, List<String> eliminated) {
        GameState gs = new GameState();
        gs.setAlivePlayers(new ArrayList<>(alive));
        gs.setEliminatedPlayers(new ArrayList<>(eliminated));
        return gs;
    }

    @Nested
    @DisplayName("countVotes()")
    class CountVotes {

        @Test
        @DisplayName("returns empty map when no votes exist")
        void countVotes_noVotes_returnsEmptyMap() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of());

            Map<String, Integer> result = voteCountingService.countVotes("room-1", 1);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("counts single vote correctly")
        void countVotes_singleVote() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(voteFor("alice")));

            Map<String, Integer> result = voteCountingService.countVotes("room-1", 1);

            assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("alice", 1));
        }

        @Test
        @DisplayName("groups multiple votes for the same player correctly")
        void countVotes_multipleVotesForSamePlayer() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(voteFor("alice"), voteFor("alice"), voteFor("bob")));

            Map<String, Integer> result = voteCountingService.countVotes("room-1", 1);

            assertThat(result)
                    .containsEntry("alice", 2)
                    .containsEntry("bob", 1);
        }

        @Test
        @DisplayName("counts votes across multiple candidates")
        void countVotes_multipleCandidates() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 2))
                    .thenReturn(List.of(
                            voteFor("alice"), voteFor("bob"),
                            voteFor("charlie"), voteFor("bob")));

            Map<String, Integer> result = voteCountingService.countVotes("room-1", 2);

            assertThat(result)
                    .containsEntry("alice", 1)
                    .containsEntry("bob", 2)
                    .containsEntry("charlie", 1);
        }
    }


    @Nested
    @DisplayName("getEliminationTarget()")
    class GetEliminationTarget {

        @Test
        @DisplayName("returns null when there are no votes")
        void getEliminationTarget_noVotes_returnsNull() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of());

            assertThat(voteCountingService.getEliminationTarget("room-1", 1)).isNull();
        }

        @Test
        @DisplayName("returns null on a tie — two players with equal top votes")
        void getEliminationTarget_tie_returnsNull() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(voteFor("alice"), voteFor("bob")));

            assertThat(voteCountingService.getEliminationTarget("room-1", 1)).isNull();
        }

        @Test
        @DisplayName("returns null on a three-way tie")
        void getEliminationTarget_threeWayTie_returnsNull() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(
                            voteFor("alice"), voteFor("bob"), voteFor("charlie")));

            assertThat(voteCountingService.getEliminationTarget("room-1", 1)).isNull();
        }

        @Test
        @DisplayName("returns null when target player not found in repository")
        void getEliminationTarget_playerNotFound_returnsNull() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(voteFor("alice"), voteFor("alice")));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.empty());

            assertThat(voteCountingService.getEliminationTarget("room-1", 1)).isNull();
        }

        @Test
        @DisplayName("returns null when target player is already ELIMINATED")
        void getEliminationTarget_playerAlreadyEliminated_returnsNull() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(voteFor("alice"), voteFor("alice")));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.of(eliminatedPlayer("alice", "room-1")));

            assertThat(voteCountingService.getEliminationTarget("room-1", 1)).isNull();
        }

        @Test
        @DisplayName("returns target username when clear winner and player is ALIVE")
        void getEliminationTarget_clearWinner_returnsTarget() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(
                            voteFor("alice"), voteFor("alice"), voteFor("bob")));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.of(alivePlayer("alice", "room-1")));

            assertThat(voteCountingService.getEliminationTarget("room-1", 1)).isEqualTo("alice");
        }

        @Test
        @DisplayName("returns correct winner when one player has all votes")
        void getEliminationTarget_unanimousVote_returnsTarget() {
            when(voteRepository.findByRoomIdAndDayNumber("room-1", 1))
                    .thenReturn(List.of(
                            voteFor("charlie"), voteFor("charlie"), voteFor("charlie")));
            when(playerRepository.findByUsernameAndRoomId("charlie", "room-1"))
                    .thenReturn(Optional.of(alivePlayer("charlie", "room-1")));

            assertThat(voteCountingService.getEliminationTarget("room-1", 1)).isEqualTo("charlie");
        }
    }


    @Nested
    @DisplayName("applyElimination()")
    class ApplyElimination {

        @Test
        @DisplayName("throws when game state not found")
        void applyElimination_gameNotFound_throws() {
            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> voteCountingService.applyElimination("room-1", "alice"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Game not found");

            verify(playerRepository, never()).save(any());
            verify(gameStateRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when player not found")
        void applyElimination_playerNotFound_throws() {
            GameState gs = gameStateWith(List.of("alice", "bob"), List.of());
            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> voteCountingService.applyElimination("room-1", "alice"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Player not found");

            verify(playerRepository, never()).save(any());
            verify(gameStateRepository, never()).save(any());
        }

        @Test
        @DisplayName("sets player status to ELIMINATED and clears voteEligibleDayNumber")
        void applyElimination_updatesPlayerStatus() {
            GameState gs = gameStateWith(List.of("alice", "bob"), List.of());
            Player alice = alivePlayer("alice", "room-1");
            alice.setVoteEligibleDayNumber(2);

            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.of(alice));

            voteCountingService.applyElimination("room-1", "alice");

            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            Player saved = playerCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo("ELIMINATED");
            assertThat(saved.getVoteEligibleDayNumber()).isNull();
        }

        @Test
        @DisplayName("moves player from alivePlayers to eliminatedPlayers in game state")
        void applyElimination_updatesGameStateLists() {
            GameState gs = gameStateWith(List.of("alice", "bob"), List.of("charlie"));
            Player alice = alivePlayer("alice", "room-1");

            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.of(alice));

            voteCountingService.applyElimination("room-1", "alice");

            ArgumentCaptor<GameState> gsCaptor = ArgumentCaptor.forClass(GameState.class);
            verify(gameStateRepository).save(gsCaptor.capture());
            GameState saved = gsCaptor.getValue();

            assertThat(saved.getAlivePlayers()).containsExactly("bob");
            assertThat(saved.getEliminatedPlayers()).containsExactlyInAnyOrder("charlie", "alice");
        }

        @Test
        @DisplayName("handles elimination when eliminatedPlayers list starts empty")
        void applyElimination_firstElimination() {
            GameState gs = gameStateWith(List.of("alice"), List.of());
            Player alice = alivePlayer("alice", "room-1");

            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.of(alice));

            voteCountingService.applyElimination("room-1", "alice");

            ArgumentCaptor<GameState> gsCaptor = ArgumentCaptor.forClass(GameState.class);
            verify(gameStateRepository).save(gsCaptor.capture());
            GameState saved = gsCaptor.getValue();

            assertThat(saved.getAlivePlayers()).isEmpty();
            assertThat(saved.getEliminatedPlayers()).containsExactly("alice");
        }

        @Test
        @DisplayName("persists both player and game state in every successful elimination")
        void applyElimination_savesBothEntities() {
            GameState gs = gameStateWith(List.of("alice", "bob"), List.of());
            Player alice = alivePlayer("alice", "room-1");

            when(gameStateRepository.findByRoomId("room-1")).thenReturn(Optional.of(gs));
            when(playerRepository.findByUsernameAndRoomId("alice", "room-1"))
                    .thenReturn(Optional.of(alice));

            voteCountingService.applyElimination("room-1", "alice");

            verify(playerRepository, times(1)).save(alice);
            verify(gameStateRepository, times(1)).save(gs);
        }
    }
}