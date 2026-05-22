package com.xuejiai.aaf.autodev.doc.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import lombok.Getter;
import lombok.Setter;

/** 开发文档 Neo4j 节点（文档引用关系图谱）。 */
@Getter
@Setter
@Node("AutodevDoc")
public class AutodevDocNode {

    @Id
    private Long docId;

    @Property
    private String title;

    @Property
    private String filePath;

    public AutodevDocNode(Long docId, String title, String filePath) {
        this.docId = docId;
        this.title = title;
        this.filePath = filePath;
    }
}
