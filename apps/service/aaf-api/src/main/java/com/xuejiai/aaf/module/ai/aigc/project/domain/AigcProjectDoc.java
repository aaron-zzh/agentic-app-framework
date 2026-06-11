package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AIGC 项目-文档 M2M 关联。 */
@Getter
@Setter
@Entity
@Table(
        name = "aigc_project_doc",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "doc_id"}))
public class AigcProjectDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的 AIGC 项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 关联的文档 ID（doc_document.id） */
    @Column(name = "doc_id", nullable = false)
    private Long docId;

    /** 文档角色：spec=创作规范 / ref=参考资料 / output=产出文档 */
    @Column(name = "role", nullable = false, length = 20)
    private String role = "ref";

    /** 排序号 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();
}
