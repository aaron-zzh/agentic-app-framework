package com.xuejiai.aaf.autodev.doc.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 开发文档引用关系（autodev_doc_link 表）。 */
@Getter
@Setter
@Entity
@Table(name = "autodev_doc_link")
public class AutodevDocLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** wikilink（[[文档名]]）或 mdlink（[text](path)） */
    @Column(name = "link_type", nullable = false, length = 20)
    private String linkType = "mdlink";

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();
}
