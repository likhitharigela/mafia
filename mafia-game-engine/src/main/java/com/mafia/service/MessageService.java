package com.mafia.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mafia.entity.Message;
import com.mafia.repository.MessageRepository;
import com.mafia.repository.PlayerRepository;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final PlayerRepository playerRepository;

    public MessageService(MessageRepository messageRepository,
                          PlayerRepository playerRepository) {
        this.messageRepository = messageRepository;
        this.playerRepository = playerRepository;
    }

    public Map<String, String> postMessage(String roomId, String sender, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Empty message");
        }

        String trimmed = content.trim();
        if (trimmed.length() > 300) {
            trimmed = trimmed.substring(0, 300);
        }

        playerRepository.findByUsernameAndRoomId(sender, roomId)
                .filter(p -> "ALIVE".equals(p.getStatus()))
                .orElseThrow(() -> new IllegalStateException("Dead players cannot chat"));

        messageRepository.save(new Message(roomId, sender, trimmed));
        return Map.of("status", "sent", "roomId", roomId, "sender", sender);
    }

    public List<Map<String, Object>> getMessages(String roomId) {
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId)
                .stream()
                .limit(50)
                .map(m -> Map.<String, Object>of(
                        "sender",    m.getSenderUsername(),
                        "message",   m.getContent(),
                        "timestamp", m.getCreatedAt().toString()))
                .collect(Collectors.toList());
    }
}