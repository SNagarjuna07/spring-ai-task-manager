package com.nagarjuna.toolcalling.controller;

import com.nagarjuna.toolcalling.dto.ChatReply;
import com.nagarjuna.toolcalling.dto.ChatRequest;
import com.nagarjuna.toolcalling.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatReply> chat(
            @Valid @RequestBody
            ChatRequest request
    ) {

        String reply = chatService
                .chat(
                        request.sessionId(),
                        request.message()
                );

        return ResponseEntity
                .ok(new ChatReply(
                                reply,
                                request.sessionId()
                        )
                );
    }
}