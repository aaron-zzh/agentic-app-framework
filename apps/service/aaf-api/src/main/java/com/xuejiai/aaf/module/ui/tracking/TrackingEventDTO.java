package com.xuejiai.aaf.module.ui.tracking;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/**
 * 行为事件批量上报 DTO。
 */
@Schema(description = "行为事件批量上报")
public record TrackingEventDTO(
        @NotEmpty @Schema(description = "事件列表") List<EventItem> events
) {
    @Schema(description = "单条埋点事件")
    public record EventItem(
            @Schema(description = "事件类型") String type,
            @Schema(description = "页面路径") String page,
            @Schema(description = "目标元素") String target,
            @Schema(description = "坐标 X") Integer x,
            @Schema(description = "坐标 Y") Integer y,
            @Schema(description = "时间戳") Long timestamp,
            @Schema(description = "附加数据") String extra
    ) {}
}
