package com.xuejiai.aaf.module.system.file.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件分页查询请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "文件分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class FileRecordPageDTO extends PageParam {

    @Schema(description = "原始文件名，模糊匹配")
    private String originalName;

    @Schema(description = "MIME 类型")
    private String mimeType;
}
