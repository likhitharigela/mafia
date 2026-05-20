package com.mafia.controller;

import com.mafia.dto.request.MessageRequest;
import com.mafia.service.MessageService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/{roomId}/message")
    public ResponseEntity<Map<String, String>> postMessage(
            @PathVariable String roomId,
            @RequestBody MessageRequest request) {
        try {
            return ResponseEntity.ok(
                    messageService.postMessage(roomId, request.senderUsername(), request.content()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable String roomId) {
        try {
            return ResponseEntity.ok(messageService.getMessages(roomId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }
}