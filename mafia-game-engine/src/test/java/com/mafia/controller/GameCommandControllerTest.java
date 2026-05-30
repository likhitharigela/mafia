package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.VoteCommandRequest;
import com.mafia.service.GameCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameCommandController.class)
class GameCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameCommandService gameCommandService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void TestShouldRecordVoteSuccessfullyWithValidInput() throws Exception {
        VoteCommandRequest request = new VoteCommandRequest("voterA", "targetB");

        doNothing().when(gameCommandService).submitVote("room-1", "voterA", "targetB");

        mockMvc.perform(post("/api/rooms/room-1/vote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.voterId").value("voterA"))
                .andExpect(jsonPath("$.votedFor").value("targetB"))
                .andExpect(jsonPath("$.status").value("vote-recorded"));

        verify(gameCommandService).submitVote("room-1", "voterA", "targetB");
    }
}