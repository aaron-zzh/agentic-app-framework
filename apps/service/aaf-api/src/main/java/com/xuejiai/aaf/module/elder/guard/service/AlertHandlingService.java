package com.xuejiai.aaf.module.elder.guard.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.guard.ErrorCodeConstants.ALERT_HANDLING_IMMUTABLE;
import static com.xuejiai.aaf.module.elder.guard.ErrorCodeConstants.ALERT_REQUEST_INVALID;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.AlertHandlingChannelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AlertRecipientTypeEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.elder.guard.domain.AlertEvent;
import com.xuejiai.aaf.module.elder.guard.domain.AlertHandling;
import com.xuejiai.aaf.module.elder.guard.mapper.AlertHandlingConvert;
import com.xuejiai.aaf.module.elder.guard.repository.AlertHandlingRepository;
import com.xuejiai.aaf.module.elder.guard.vo.AlertHandlingCreateDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertHandlingPageDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertHandlingVO;

import lombok.RequiredArgsConstructor;

/** 预警处理审计服务；管理端只读，业务流程仅通过受控追加入口写入。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertHandlingService
        extends BaseCrudService<
                AlertHandling,
                AlertHandlingVO,
                AlertHandlingCreateDTO,
                Void,
                AlertHandlingPageDTO> {

    private final AlertHandlingRepository repository;

    @Override
    protected AlertHandlingRepository getRepository() {
        return repository;
    }

    @Override
    protected AlertHandlingVO toVO(AlertHandling entity) {
        return new AlertHandlingVO(
                entity.getId(),
                entity.getAlertId(),
                entity.getHandlingType(),
                entity.getChannel(),
                entity.getStatus(),
                entity.getRecipientType(),
                entity.getRecipientId(),
                entity.getOperatorUserId(),
                entity.getAcknowledgedContactId(),
                entity.getDetail(),
                entity.getErrorMessage(),
                entity.getHandledAt(),
                entity.getCreateTime(),
                null,
                null,
                null,
                null);
    }

    @Override
    protected AlertHandling toEntity(AlertHandlingCreateDTO request) {
        var entity = AlertHandlingConvert.INSTANCE.toEntity(request);
        validate(entity);
        return entity;
    }

    @Override
    protected void updateEntity(AlertHandling entity, Void request) {
        throw exception(ALERT_HANDLING_IMMUTABLE);
    }

    @Override
    protected void beforeDelete(AlertHandling entity) {
        throw exception(ALERT_HANDLING_IMMUTABLE);
    }

    @Override
    protected Specification<AlertHandling> buildSpec(AlertHandlingPageDTO request) {
        return SpecificationBuilder.<AlertHandling>builder()
                .eqIfPresent("alertId", request.getAlertId())
                .eqIfPresent("handlingType", request.getHandlingType())
                .eqIfPresent("channel", request.getChannel())
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("operatorUserId", request.getOperatorUserId())
                .eqIfPresent("acknowledgedContactId", request.getAcknowledgedContactId())
                .build();
    }

    /** 在当前业务事务内追加一条不可变处理审计。 */
    @Transactional
    public AlertHandling append(AlertEvent alert, HandlingRecord record) {
        if (alert == null || alert.getId() == null || record == null) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        var entity = new AlertHandling();
        entity.setAlertId(alert.getId());
        entity.setHandlingType(record.handlingType());
        entity.setChannel(record.channel());
        entity.setStatus(record.status());
        entity.setRecipientType(record.recipientType());
        entity.setRecipientId(record.recipientId());
        entity.setOperatorUserId(record.operatorUserId());
        entity.setAcknowledgedContactId(record.acknowledgedContactId());
        entity.setDetail(record.detail());
        entity.setErrorMessage(record.errorMessage());
        entity.setHandledAt(record.handledAt() == null ? LocalDateTime.now() : record.handledAt());
        entity.setOrgId(alert.getOrgId());
        entity.setWorkspaceId(alert.getWorkspaceId());
        entity.setOwnerId(alert.getOwnerId());
        validate(entity);
        return repository.save(entity);
    }

    private void validate(AlertHandling entity) {
        if (entity.getAlertId() == null
                || entity.getHandledAt() == null
                || !allowed(entity.getHandlingType(), AlertHandlingTypeEnum.ARRAYS)
                || !allowed(entity.getChannel(), AlertHandlingChannelEnum.ARRAYS)
                || !allowed(entity.getStatus(), AlertHandlingStatusEnum.ARRAYS)
                || entity.getRecipientType() != null
                        && !allowed(entity.getRecipientType(), AlertRecipientTypeEnum.ARRAYS)
                || entity.getDetail() != null && entity.getDetail().length() > 1000
                || entity.getErrorMessage() != null && entity.getErrorMessage().length() > 500) {
            throw exception(ALERT_REQUEST_INVALID);
        }
    }

    private boolean allowed(String value, String[] values) {
        return value != null && Arrays.stream(values).anyMatch(value::equals);
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of();
    }

    /** 处理审计写入命令。 */
    public record HandlingRecord(
            String handlingType,
            String channel,
            String status,
            String recipientType,
            Long recipientId,
            Long operatorUserId,
            Long acknowledgedContactId,
            String detail,
            String errorMessage,
            LocalDateTime handledAt) {}
}
