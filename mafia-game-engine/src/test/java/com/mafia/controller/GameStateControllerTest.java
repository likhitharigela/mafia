package com.mafia.controller;

import com.mafia.dto.response.AggregatedGameSnapshot;
import com.mafia.service.GameStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameStateController.class)
class GameStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameStateService gameStateService;

    @Test
    void TestShouldReturnOKWhenPinged() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("mafia-game-engine"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void TestShouldReturn200WhenGameStateRetrievedSuccessfully() throws Exception {
        AggregatedGameSnapshot snapshot = new AggregatedGameSnapshot(
                "DAY_DISCUSSION",
                1,
                1,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                "NONE",
                List.of(),
                List.of(),
                List.of(),
                "CODE",
                "host1",
                "now"
        );

        when(gameStateService.getSnapshot("room-1")).thenReturn(snapshot);

        mockMvc.perform(get("/api/game-state/room-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DAY_DISCUSSION"));

        verify(gameStateService).getSnapshot("room-1");
    }

    @Test
    void TestShouldReturn200WhenGameStartedSuccessfully() throws Exception {
        doNothing().when(gameStateService).startGame("room-1");

        mockMvc.perform(post("/api/game-state/room-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.status").value("started"));

        verify(gameStateService).startGame("room-1");
    }
}