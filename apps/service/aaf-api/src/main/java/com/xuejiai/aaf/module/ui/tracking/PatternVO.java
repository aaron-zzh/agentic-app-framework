package com.xuejiai.aaf.module.ui.tracking;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 操作模式识别结果 VO。
 */
@Schema(description = "操作模式")
public record PatternVO(
        @Schema(description = "模式名称") String name,
        @Schema(description = "操作序列描述") String sequence,
        @Schema(description = "出现频次") int frequency,
        @Schema(description = "占比") double ratio
) {}
