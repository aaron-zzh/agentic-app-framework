package com.xuejiai.aaf.module.document.domain;

import org.springframework.data.neo4j.core.schema.*;

/** 文档节点（Neo4j）。 */
@Node("DocNode")
public class DocNode {

    @Id @GeneratedValue private Long neoId;

    /** 对应 PostgreSQL doc_document.id */
    @Property("docId")
    private Long docId;

    @Property("title")
    private String title;

    @Property("filePath")
    private String filePath;

    public DocNode() {}

    public DocNode(Long docId, String title, String filePath) {
        this.docId = docId;
        this.title = title;
        this.filePath = filePath;
    }

    public Long getNeoId() {
        return neoId;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
