package com.xuejiai.aaf.module.document.vo;

import java.util.List;

/** 文档关系图 VO。 */
public record DocRelationGraphVO(List<Node> nodes, List<Edge> edges) {

    public record Node(Long id, String title, String filePath, boolean isCurrent) {}

    public record Edge(Long sourceId, Long targetId, String linkType) {}
}
