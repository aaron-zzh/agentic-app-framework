package com.xuejiai.aaf.module.document.vo;

import java.util.List;

/** 文档树节点。 */
public record DocTreeNodeVO(
        Long id, String name, String path, boolean isDir, List<DocTreeNodeVO> children) {}
