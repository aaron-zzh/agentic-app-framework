package com.xuejiai.aaf.module.elder.guard.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.elder.AlertHandlingChannelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AlertRecipientTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 预警通知、认领、已知晓与结案的只增记录。 */
@Getter
@Setter
@Entity
@Table(name = "elder_alert_handling")
public class AlertHandling extends BaseEntity {

    /** 预警事件 ID。 */
    @CrudReference(
            key = "alert",
            idProperty = "alertId",
            targetResource = "elder.guard.alert",
            viewField = "alert")
    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    /** 响应类型。枚举 {@link AlertHandlingTypeEnum}。 */
    @Column(name = "handling_type", nullable = false, length = 24)
    private String handlingType;

    /** 响应渠道。枚举 {@link AlertHandlingChannelEnum}。 */
    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    /** 响应状态。枚举 {@link AlertHandlingStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 接收者类型。枚举 {@link AlertRecipientTypeEnum}。 */
    @Column(name = "recipient_type", length = 32)
    private String recipientType;

    /** 接收者标识。 */
    @Column(name = "recipient_id")
    private Long recipientId;

    /** 执行操作的运营用户 ID。 */
    @CrudReference(
            key = "operatorUser",
            idProperty = "operatorUserId",
            targetResource = "system.user",
            viewField = "operatorUser")
    @Column(name = "operator_user_id")
    private Long operatorUserId;

    /** 被代记已知晓的联系人 ID。 */
    @CrudReference(
            key = "acknowledgedContact",
            idProperty = "acknowledgedContactId",
            targetResource = "elder.profile.elder-contact",
            viewField = "acknowledgedContact")
    @Column(name = "acknowledged_contact_id")
    private Long acknowledgedContactId;

    /** 处理结论或渠道响应摘要。 */
    @Column(name = "detail", length = 1000)
    private String detail;

    /** 渠道失败原因。 */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 响应发生时间。 */
    @Column(name = "handled_at", nullable = false)
    private LocalDateTime handledAt;
}
