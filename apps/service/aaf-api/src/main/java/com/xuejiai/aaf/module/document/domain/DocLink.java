package com.xuejiai.aaf.module.document.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 文档关联关系实体。 */
@Getter
@Setter
@Entity
@Table(name = "doc_link")
public class DocLink extends BaseEntity {

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "link_type", length = 32)
    private String linkType;
}
