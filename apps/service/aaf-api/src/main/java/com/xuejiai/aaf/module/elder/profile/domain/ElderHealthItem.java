package com.xuejiai.aaf.module.elder.profile.domain;

import java.time.LocalDate;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.elder.HealthItemTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 长者健康条目。 */
@Getter
@Setter
@Entity
@Table(name = "elder_health_item")
@SQLDelete(
        sql =
                "UPDATE elder_health_item SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ElderHealthItem extends BaseEntity {

    /** 长者 ID。 */
    @CrudReference(targetResource = "elder.profile.elder")
    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    /** 条目类型。枚举 {@link HealthItemTypeEnum}。 */
    @Column(name = "item_type", nullable = false, length = 32)
    private String itemType;

    /** 条目名称。 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 详细说明。 */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** 发生或确诊日期。 */
    @Column(name = "occurred_date")
    private LocalDate occurredDate;
}
