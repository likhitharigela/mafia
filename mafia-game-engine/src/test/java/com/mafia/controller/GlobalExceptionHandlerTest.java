package com.mafia.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void TestShouldReturn400WhenIllegalArgumentExceptionThrown() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid input"));
    }

    @Test
    void TestShouldReturn400WhenIllegalStateExceptionThrown() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid state"));
    }

    @Test
    void TestShouldReturn500WhenUnexpectedExceptionThrown() throws Exception {
        mockMvc.perform(get("/test/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/illegal-argument")
        public ResponseEntity<Void> throwIllegalArgument() {
            throw new IllegalArgumentException("Invalid input");
        }

        @GetMapping("/test/illegal-state")
        public ResponseEntity<Void> throwIllegalState() {
            throw new IllegalStateException("Invalid state");
        }

        @GetMapping("/test/runtime")
        public ResponseEntity<Void> throwRuntime() {
            throw new RuntimeException("Something broke");
        }
    }
}