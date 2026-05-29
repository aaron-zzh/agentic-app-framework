package com.xuejiai.aaf.module.stats.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 用户行为事件批量上报请求。
 */
@Schema(description = "行为事件批量上报")
public record UserEventBatchDTO(
        @NotEmpty @Schema(description = "事件列表") List<EventItem> events
) {
    @Schema(description = "单条事件")
    public record EventItem(
            @NotNull @Schema(description = "事件类型") String eventType,
            @Schema(description = "页面路径") String page,
            @Schema(description = "操作目标") String target,
            @Schema(description = "附加数据JSON") String extra
    ) {}
}
