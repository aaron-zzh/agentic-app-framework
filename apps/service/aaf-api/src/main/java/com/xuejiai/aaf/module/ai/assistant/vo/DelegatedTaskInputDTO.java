package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交任务运行期输入的请求体。
 *
 * <p>{@code kind} 是客户端建议值，服务端会重新分类判定，不直接信任；{@code text} 是原始自然语言输入，
 * 修改类输入据此重新协调规划。取消不走本入口——统一使用 {@code POST /{taskId}/stop}。
 */
public record DelegatedTaskInputDTO(
        @jakarta.validation.constraints.NotBlank @Size(max = 128) String inputId,
        @NotNull ExecutionInput.Kind kind,
        @Size(max = 4000) String text,
        @NotNull @Size(max = 64) Map<@Size(max = 128) String, @Size(max = 4000) String> values) {}
