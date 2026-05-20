package com.mafia.dto.request;

public record MessageRequest(
    String senderUsername,
    String content
) {}