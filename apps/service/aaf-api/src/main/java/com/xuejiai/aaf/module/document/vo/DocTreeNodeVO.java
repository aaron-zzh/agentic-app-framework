package com.xuejiai.aaf.module.document.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 文档树节点 Response VO。 */
@Schema(description = "文档树节点")
public record DocTreeNodeVO(
        @Schema(description = "文档编号（目录节点为 null）") Long id,
        @Schema(description = "节点名称") String name,
        @Schema(description = "路径") String path,
        @Schema(description = "是否为目录") boolean isDirectory,
        @Schema(description = "子节点") List<DocTreeNodeVO> children) {}
