package com.xuejiai.aaf.module.system.log.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体版本快照。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_record_version")
public class RecordVersion extends BaseEntity {

    /** 实体类型标识 */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /** 实体记录 ID */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** 业务版本号（递增） */
    @Column(name = "ver_number", nullable = false)
    private Integer verNumber;

    /** 快照数据（完整 JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, columnDefinition = "jsonb")
    private String data;
}
