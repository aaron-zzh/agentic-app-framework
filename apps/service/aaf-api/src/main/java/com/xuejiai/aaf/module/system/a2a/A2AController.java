package com.xuejiai.aaf.module.system.a2a;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.assistant.a2a.A2AEngine.A2AResponse;
import com.xuejiai.aaf.framework.intelligent.assistant.a2a.A2AEngine.AgentCard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * A2A 协议端点——对外暴露 Agent-to-Agent 通信接口（Layer 5 交互层）
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "A2A 协议")
@RestController
@RequestMapping("/api/a2a")
@RequiredArgsConstructor
public class A2AController {

    private final A2AProtocolService a2aService;

    @Operation(summary = "接收 A2A 消息", description = "外部 Assistant 发送消息到本地 Assistant")
    @PostMapping("/receive/{assistantId}")
    public Result<A2AResponse> receive(
            @PathVariable String assistantId, @RequestBody @Valid A2AMessageRequest request) {
        var response =
                a2aService.receive(
                        assistantId,
                        request.conversationId(),
                        request.fromUserId(),
                        request.content());
        return Result.success(response);
    }

    @Operation(summary = "发现可用 Assistant", description = "按能力发现已注册的 Assistant")
    @GetMapping("/discover")
    public Result<List<AgentCard>> discover(@RequestParam String capability) {
        return Result.success(a2aService.discover(capability));
    }

    @Operation(summary = "暴露 Assistant", description = "注册本地 Assistant 为 A2A 可达")
    @PostMapping("/expose")
    public Result<Void> expose(@RequestBody @Valid ExposeRequest request) {
        a2aService.expose(
                request.assistantId(),
                request.name(),
                request.description(),
                request.capabilities());
        return Result.success();
    }

    public record A2AMessageRequest(
            @NotBlank String conversationId,
            Long fromUserId,
            @NotBlank String content,
            Map<String, Object> metadata) {}

    public record ExposeRequest(
            @NotBlank String assistantId,
            @NotBlank String name,
            String description,
            List<String> capabilities) {}
}
