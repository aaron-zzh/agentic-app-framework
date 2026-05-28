package com.xuejiai.aaf.autodev.doc.vo;

import java.util.List;

/** 开发文档关系图数据。 */
public record AutodevDocRelationGraphVO(List<Node> nodes, List<Edge> edges) {
    public record Node(Long id, String title, String filePath, boolean isCenter) {}

    public record Edge(Long source, Long target, String type) {}
}
