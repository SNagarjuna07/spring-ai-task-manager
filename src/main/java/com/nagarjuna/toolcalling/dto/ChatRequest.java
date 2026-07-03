package com.nagarjuna.toolcalling.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(

        @NotBlank(message = "Session ID should not be blank")
        String sessionId,

        @NotBlank(message = "Message should not be empty")
        String message
) {}
