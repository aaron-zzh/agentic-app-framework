package com.xuejiai.aaf.module.document.vo;

import java.util.List;

/** 文档关系图数据（nodes + edges）。 */
public record DocRelationGraphVO(List<Node> nodes, List<Edge> edges) {
    public record Node(Long id, String title, String filePath, boolean isCenter) {}

    public record Edge(Long source, Long target, String type) {}
}
