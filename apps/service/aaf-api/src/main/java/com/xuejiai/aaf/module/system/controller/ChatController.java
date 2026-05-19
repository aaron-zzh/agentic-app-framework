package com.xuejiai.aaf.module.system.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.service.ChatService;
import com.xuejiai.aaf.module.system.vo.ChatMessageSendDTO;
import com.xuejiai.aaf.module.system.vo.ChatMessageVO;
import com.xuejiai.aaf.module.system.vo.ChatSessionCreateDTO;
import com.xuejiai.aaf.module.system.vo.ChatSessionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 聊天接口。 */
@Tag(name = "聊天")
@RestController
@RequestMapping("/api/system/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ActorContext actorContext;

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(@RequestBody @Validated ChatSessionCreateDTO dto) {
        var userId = actorContext.currentUserId().orElseThrow();
        return Result.success(chatService.createSession(userId, dto));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions() {
        var userId = actorContext.currentUserId().orElseThrow();
        return Result.success(chatService.listSessions(userId));
    }

    @Operation(summary = "获取会话消息历史")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> listMessages(@PathVariable Long sessionId) {
        return Result.success(chatService.listMessages(sessionId));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/messages")
    public Result<ChatMessageVO> sendMessage(@RequestBody @Validated ChatMessageSendDTO dto) {
        var userId = actorContext.currentUserId().orElseThrow();
        var message = chatService.saveMessage(userId, "HUMAN", dto.sessionId(), "user", dto.content());
        return Result.success(message);
    }
}
