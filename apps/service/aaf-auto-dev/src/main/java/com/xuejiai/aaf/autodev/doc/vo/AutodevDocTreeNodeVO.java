package com.xuejiai.aaf.autodev.doc.vo;

import java.util.List;

/** 开发文档树节点。 */
public record AutodevDocTreeNodeVO(
        Long id, String name, String path, boolean isDir, List<AutodevDocTreeNodeVO> children) {}
