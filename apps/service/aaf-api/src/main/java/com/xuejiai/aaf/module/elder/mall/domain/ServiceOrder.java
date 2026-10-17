package com.xuejiai.aaf.module.elder.mall.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.elder.ServiceOrderStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 一期服务预约单，一个预约对应一个服务项目。 */
@Getter
@Setter
@Entity
@Table(name = "elder_service_order")
@CrudReference(
        key = "elder",
        idProperty = "elderId",
        targetResource = "elder.profile.elder",
        inputField = "elderId",
        viewField = "elder")
@CrudReference(
        key = "serviceItem",
        idProperty = "serviceItemId",
        targetResource = "elder.mall.service-item",
        inputField = "serviceItemId",
        viewField = "serviceItem")
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
                "UPDATE elder_service_order SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ServiceOrder extends BaseEntity {

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Column(name = "service_item_id", nullable = false)
    private Long serviceItemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "service_address", nullable = false, length = 500)
    private String serviceAddress;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** 预约状态。枚举 {@link ServiceOrderStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = ServiceOrderStatusEnum.CREATED.getCode();

    /** 确认时创建的设备提醒幂等指令号，取消时据此撤销。 */
    @Column(name = "reminder_command_id", length = 100)
    private String reminderCommandId;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
