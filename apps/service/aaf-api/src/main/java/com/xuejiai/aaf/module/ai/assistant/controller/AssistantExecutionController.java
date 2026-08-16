package com.xuejiai.aaf.module.ai.assistant.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionEventVO;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/** 通用 Assistant 无会话执行接口。 */
@Tag(name = "Assistant 执行")
@RestController
@RequestMapping("/api/assistants")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AssistantExecutionController {

    private final AssistantExecutionService assistantExecutionService;

    @Operation(summary = "流式执行 Assistant")
    @PostMapping(
            value = "/{assistantId}/executions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AssistantExecutionEventVO> execute(
            @PathVariable String assistantId,
            @Valid @RequestBody AssistantExecutionRequest request) {
        return assistantExecutionService.execute(assistantId, request);
    }
}
