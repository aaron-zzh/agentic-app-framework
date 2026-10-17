package com.xuejiai.aaf.module.elder.mall.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.elder.ServiceItemStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 可预约的养老服务项目。 */
@Getter
@Setter
@Entity
@Table(name = "elder_service_item")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(
        sql =
                "UPDATE elder_service_item SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ServiceItem extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    /** 分类编码，由 elder_service_category 字典解释。 */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit;

    /** 一期线下结算，仅展示价格说明。 */
    @Column(name = "price_description", nullable = false, length = 200)
    private String priceDescription;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** 项目状态。枚举 {@link ServiceItemStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = ServiceItemStatusEnum.AVAILABLE.getCode();
}
