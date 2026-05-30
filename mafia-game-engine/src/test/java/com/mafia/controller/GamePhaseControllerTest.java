package com.mafia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.dto.request.DoctorSaveSubmitRequest;
import com.mafia.dto.request.NightActionSubmitRequest;
import com.mafia.dto.request.PoliceGuessSubmitRequest;
import com.mafia.service.GameLoopService;
import com.mafia.service.NightPhaseService;
import com.mafia.service.PhaseTransitionService;
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

@WebMvcTest(GamePhaseController.class)
class GamePhaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NightPhaseService nightPhaseService;

    @MockBean
        private PhaseTransitionService phaseTransitionService;

    @MockBean
    private GameLoopService gameLoopService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void TestShouldAdvancePhaseSuccessfullyWithValidRoomId() throws Exception {
        doNothing().when(phaseTransitionService).advancePhase("room-1");

        mockMvc.perform(post("/api/game/room-1/advance-phase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.status").value("phase-advanced"));

        verify(phaseTransitionService).advancePhase("room-1");
    }

    @Test
    void TestShouldSubmitNightKillSuccessfully() throws Exception {
        NightActionSubmitRequest request = new NightActionSubmitRequest("targetA");
        doNothing().when(nightPhaseService).submitNightKill("room-1", "targetA");

        mockMvc.perform(post("/api/game/room-1/submit-night-kill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.target").value("targetA"))
                .andExpect(jsonPath("$.status").value("recorded"));

        verify(nightPhaseService).submitNightKill("room-1", "targetA");
    }

    @Test
    void TestShouldSubmitPoliceGuessSuccessfully() throws Exception {
        PoliceGuessSubmitRequest request = new PoliceGuessSubmitRequest("suspectB");
        doNothing().when(nightPhaseService).submitPoliceGuess("room-1", "suspectB");

        mockMvc.perform(post("/api/game/room-1/submit-police-guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.suspect").value("suspectB"))
                .andExpect(jsonPath("$.status").value("recorded"));

        verify(nightPhaseService).submitPoliceGuess("room-1", "suspectB");
    }

    @Test
    void TestShouldSubmitDoctorSaveSuccessfully() throws Exception {
        DoctorSaveSubmitRequest request = new DoctorSaveSubmitRequest("savedC");
        doNothing().when(nightPhaseService).submitDoctorSave("room-1", "savedC");

        mockMvc.perform(post("/api/game/room-1/submit-doctor-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.saved").value("savedC"))
                .andExpect(jsonPath("$.status").value("recorded"));

        verify(nightPhaseService).submitDoctorSave("room-1", "savedC");
    }

    @Test
    void TestShouldResolveVotingSuccessfully() throws Exception {
        doNothing().when(gameLoopService).resolveVoting("room-1");

        mockMvc.perform(post("/api/game/room-1/resolve-voting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.status").value("voting-resolved"));

        verify(gameLoopService).resolveVoting("room-1");
    }
}