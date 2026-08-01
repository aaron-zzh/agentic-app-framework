package com.xuejiai.aaf.module.ai.chat.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;
import com.xuejiai.aaf.module.ai.chat.service.IntentService;
import com.xuejiai.aaf.module.ai.chat.service.WelcomeSuggestionService;
import com.xuejiai.aaf.module.ai.chat.vo.ChatMessageSendDTO;
import com.xuejiai.aaf.module.ai.chat.vo.ChatMessageVO;
import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionCreateDTO;
import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionRenameDTO;
import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionVO;
import com.xuejiai.aaf.module.ai.chat.vo.IntentClassifyDTO;
import com.xuejiai.aaf.module.ai.chat.vo.IntentResult;
import com.xuejiai.aaf.module.ai.chat.vo.MessageFeedbackDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 聊天管理接口
 *
 * <p>仅提供会话与消息管理；AI 执行统一走 v2 {@code /agui/run}。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "聊天")
@RestController
@RequestMapping("/api/system/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private final ChatService chatService;
    private final IntentService intentService;
    private final OperatorContext operatorContext;
    private final WelcomeSuggestionService welcomeSuggestionService;

    @Operation(summary = "意图识别")
    @PostMapping("/intent")
    public Result<IntentResult> classifyIntent(@RequestBody @Validated IntentClassifyDTO dto) {
        return Result.success(intentService.classify(dto.text()));
    }

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(@RequestBody @Validated ChatSessionCreateDTO dto) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        return Result.success(chatService.createSession(userId, dto));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions() {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        return Result.success(chatService.listSessions(userId));
    }

    @Operation(summary = "获取会话消息历史（按数字 ID）")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> listMessages(@PathVariable Long sessionId) {
        return Result.success(chatService.listMessages(sessionId));
    }

    @Operation(summary = "获取会话消息历史（按 threadId，AG-UI 链路）")
    @GetMapping("/sessions/thread/{threadId}/messages")
    public Result<List<ChatMessageVO>> listMessagesByThreadId(@PathVariable String threadId) {
        return Result.success(chatService.listMessagesByThreadId(threadId));
    }

    @Operation(summary = "分页获取会话消息")
    @GetMapping("/sessions/{sessionId}/messages/page")
    public Result<Page<ChatMessageVO>> getMessagesPaged(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.getMessagesPaged(sessionId, page, size));
    }

    @Operation(summary = "归档会话")
    @PostMapping("/sessions/{sessionId}/archive")
    public Result<Void> archiveSession(@PathVariable Long sessionId) {
        chatService.archiveSession(sessionId);
        return Result.success(null);
    }

    @Operation(summary = "发送消息")
    @PostMapping("/messages")
    public Result<ChatMessageVO> sendMessage(@RequestBody @Validated ChatMessageSendDTO dto) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        // M18：走 saveUserMessage，内部校验 sessionId 归属当前用户
        var message = chatService.saveUserMessage(userId, dto.sessionId(), dto.content());
        return Result.success(message);
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success();
    }

    @Operation(summary = "重命名会话")
    @PutMapping("/sessions/{sessionId}/rename")
    public Result<ChatSessionVO> renameSession(
            @PathVariable Long sessionId, @RequestBody @Validated ChatSessionRenameDTO dto) {
        return Result.success(chatService.renameSession(sessionId, dto.title()));
    }

    @Operation(summary = "消息反馈（点赞/点踩）")
    @PostMapping("/messages/{messageId}/feedback")
    public Result<Void> messageFeedback(
            @PathVariable Long messageId, @RequestBody @Validated MessageFeedbackDTO dto) {
        chatService.messageFeedback(messageId, dto.type(), dto.comment());
        return Result.success();
    }

    @GetMapping("/suggestions")
    @Operation(summary = "获取欢迎页建议问题")
    public Result<List<java.util.Map<String, String>>> getSuggestions(
            @RequestParam(required = false, defaultValue = "default") String agentId) {
        return Result.success(welcomeSuggestionService.getWelcomeSuggestions(agentId));
    }
}
