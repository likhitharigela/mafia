package com.mafia.dto.response;

import java.util.List;
import java.util.Map;

public record AggregatedGameSnapshot(
                String phase,
                int dayNumber,
                int nightNumber,
                List<Map<String, Object>> players,
                List<String> alivePlayers,
                List<String> eliminatedPlayers,
                String nightKillTarget,
                Boolean nightKillFailed,
                String policeGuessTarget,
                Boolean policeGuessCorrect,
                String winner,
                List<Map<String, Object>> chatMessages,
                List<Map<String, Object>> recentEvents,
                List<String> allowedActions,
                String roomCode,
                String hostUsername,
                String phaseEndsAt) {
}
