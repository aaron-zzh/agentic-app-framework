package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目引用的渠道规格版本。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_channel_ref")
@SQLDelete(
        sql =
                "UPDATE aigc_project_channel_ref SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectChannelRef extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "channel_spec_id", nullable = false)
    private Long channelSpecId;

    @Column(name = "primary_channel", nullable = false)
    private Boolean primaryChannel = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "override_config", columnDefinition = "jsonb")
    private Map<String, Object> overrideConfig;
}
