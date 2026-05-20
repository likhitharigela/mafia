package com.mafia.service;

import com.mafia.entity.Player;
import com.mafia.repository.PlayerRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WinConditionService {

    private final PlayerRepository playerRepository;

    public WinConditionService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public String checkWinCondition(String roomId) {
        List<Player> players = playerRepository.findByRoomId(roomId);

        long aliveMafia = players.stream()
                .filter(p -> "MAFIA".equals(p.getRole()) && "ALIVE".equals(p.getStatus()))
                .count();

        long aliveVillagers = players.stream()
                .filter(p -> "ALIVE".equals(p.getStatus())
                        && !"MAFIA".equals(p.getRole())
                        && !"SOLDIER".equals(p.getRole()))
                .count();

        if (aliveMafia == 0)
            return "VILLAGERS";
        if (aliveMafia >= aliveVillagers)
            return "MAFIA";
        return "NONE";
    }
}