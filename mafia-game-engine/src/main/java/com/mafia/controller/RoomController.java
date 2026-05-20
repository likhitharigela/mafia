package com.mafia.controller;

import com.mafia.dto.request.CreateRoomRequest;
import com.mafia.dto.request.JoinRoomByCodeRequest;
import com.mafia.entity.Player;
import com.mafia.entity.Room;
import com.mafia.service.RoomService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest req) {
        try {
            return ResponseEntity.ok(roomToMap(roomService.createRoom(req.roomName(), req.hostUsername())));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<?> joinByCode(@RequestBody JoinRoomByCodeRequest req) {
        try {
            return ResponseEntity.ok(roomToMap(roomService.joinRoomByCode(req.roomCode(), req.username())));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<?> getByCode(@PathVariable String code) {
        try {
            return ResponseEntity.ok(roomToMap(roomService.getRoomByCode(code)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @GetMapping("/{roomId}/players")
    public ResponseEntity<?> getPlayers(@PathVariable String roomId) {
        try {
            return ResponseEntity.ok(playersToList(roomService.getRoomPlayers(roomId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    @GetMapping("/by-code/{code}/players")
    public ResponseEntity<?> getPlayersByCode(@PathVariable String code) {
        try {
            Room room = roomService.getRoomByCode(code);
            return ResponseEntity.ok(playersToList(roomService.getRoomPlayers(room.getId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    private Map<String, Object> roomToMap(Room room) {
        return Map.of(
                "roomId", room.getId(),
                "roomCode", room.getRoomCode(),
                "roomName", room.getName(),
                "hostUsername", room.getHostUsername(),
                "playerCount", room.getPlayerIds().size(),
                "minPlayers", room.getMinPlayers(),
                "status", room.getStatus());
    }

    private List<Map<String, Object>> playersToList(List<Player> players) {
        return players.stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.getUsername(),
                        "alive", "ALIVE".equals(p.getStatus()),
                        "role", p.getRole() != null ? p.getRole() : ""))
                .collect(Collectors.toList());
    }
}