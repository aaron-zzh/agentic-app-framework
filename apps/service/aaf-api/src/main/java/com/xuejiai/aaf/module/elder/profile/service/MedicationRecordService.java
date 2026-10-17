package com.xuejiai.aaf.module.elder.profile.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.MEDICATION_PLAN_NOT_FOUND;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.MEDICATION_RECORD_CONFLICT;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.MEDICATION_RECORD_IMMUTABLE;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.MEDICATION_RECORD_INVALID;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.MedicationRecordResultEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationRecordSourceEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.elder.profile.api.MedicationApi.MedicationRecordSnapshot;
import com.xuejiai.aaf.module.elder.profile.api.MedicationApi.RecordMedicationResult;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationPlan;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationRecord;
import com.xuejiai.aaf.module.elder.profile.mapper.MedicationRecordConvert;
import com.xuejiai.aaf.module.elder.profile.repository.MedicationPlanRepository;
import com.xuejiai.aaf.module.elder.profile.repository.MedicationRecordRepository;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationRecordCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationRecordPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationRecordVO;

import lombok.RequiredArgsConstructor;

/** 服药事实服务；标准入口和跨域入口都只允许新增。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationRecordService
        extends BaseCrudService<
                MedicationRecord,
                MedicationRecordVO,
                MedicationRecordCreateDTO,
                Void,
                MedicationRecordPageDTO> {

    private final MedicationRecordRepository repository;
    private final MedicationPlanRepository planRepository;

    @Override
    protected MedicationRecordRepository getRepository() {
        return repository;
    }

    @Override
    protected MedicationRecordVO toVO(MedicationRecord entity) {
        return new MedicationRecordVO(
                entity.getId(),
                entity.getElderId(),
                entity.getPlanId(),
                entity.getScheduledAt(),
                entity.getResult(),
                entity.getSource(),
                entity.getRecordedAt(),
                entity.getSourceReference(),
                entity.getCreateTime(),
                null,
                null,
                null);
    }

    @Override
    protected MedicationRecord toEntity(MedicationRecordCreateDTO request) {
        var plan = requirePlan(request.planId());
        var entity = MedicationRecordConvert.INSTANCE.toEntity(request);
        entity.setElderId(plan.getElderId());
        if (entity.getRecordedAt() == null) {
            entity.setRecordedAt(LocalDateTime.now());
        }
        validate(entity);
        return entity;
    }

    @Override
    protected void updateEntity(MedicationRecord entity, Void request) {
        throw exception(MEDICATION_RECORD_IMMUTABLE);
    }

    @Override
    protected void beforeDelete(MedicationRecord entity) {
        throw exception(MEDICATION_RECORD_IMMUTABLE);
    }

    @Override
    protected Specification<MedicationRecord> buildSpec(MedicationRecordPageDTO request) {
        return SpecificationBuilder.<MedicationRecord>builder()
                .eqIfPresent("elderId", request.getElderId())
                .eqIfPresent("planId", request.getPlanId())
                .eqIfPresent("result", request.getResult())
                .eqIfPresent("source", request.getSource())
                .build();
    }

    /** 使用数据库唯一约束按计划与计划时间原子幂等写入。 */
    @Transactional
    public MedicationRecordSnapshot recordResult(
            MedicationPlan plan,
            RecordMedicationResult command,
            LocalDateTime scheduledAt,
            LocalDateTime recordedAt) {
        validateCommand(command, scheduledAt, recordedAt);
        repository.insertIfAbsent(
                plan.getOrgId(),
                plan.getWorkspaceId(),
                plan.getElderId(),
                plan.getId(),
                scheduledAt,
                command.result(),
                command.source(),
                recordedAt,
                command.sourceReference(),
                plan.getOwnerId());
        var record =
                repository
                        .findByPlanIdAndScheduledAt(plan.getId(), scheduledAt)
                        .orElseThrow(() -> exception(MEDICATION_RECORD_INVALID));
        if (!Objects.equals(record.getResult(), command.result())
                || !Objects.equals(record.getSource(), command.source())) {
            throw exception(MEDICATION_RECORD_CONFLICT);
        }
        return toSnapshot(record, command.zoneId());
    }

    private MedicationPlan requirePlan(Long planId) {
        if (planId == null || planId <= 0) {
            throw exception(MEDICATION_RECORD_INVALID);
        }
        return planRepository
                .findById(planId)
                .orElseThrow(() -> exception(MEDICATION_PLAN_NOT_FOUND));
    }

    private void validateCommand(
            RecordMedicationResult command, LocalDateTime scheduledAt, LocalDateTime recordedAt) {
        if (command == null
                || scheduledAt == null
                || recordedAt == null
                || command.sourceReference() != null && command.sourceReference().length() > 100) {
            throw exception(MEDICATION_RECORD_INVALID);
        }
        requireAllowedResult(command.result());
        requireAllowedSource(command.source());
    }

    private void validate(MedicationRecord entity) {
        if (entity.getElderId() == null
                || entity.getPlanId() == null
                || entity.getScheduledAt() == null
                || entity.getRecordedAt() == null
                || entity.getSourceReference() != null
                        && entity.getSourceReference().length() > 100) {
            throw exception(MEDICATION_RECORD_INVALID);
        }
        requireAllowedResult(entity.getResult());
        requireAllowedSource(entity.getSource());
    }

    private void requireAllowedResult(String result) {
        if (result == null
                || Arrays.stream(MedicationRecordResultEnum.ARRAYS).noneMatch(result::equals)) {
            throw exception(MEDICATION_RECORD_INVALID);
        }
    }

    private void requireAllowedSource(String source) {
        if (source == null
                || Arrays.stream(MedicationRecordSourceEnum.ARRAYS).noneMatch(source::equals)) {
            throw exception(MEDICATION_RECORD_INVALID);
        }
    }

    private MedicationRecordSnapshot toSnapshot(MedicationRecord record, ZoneId zoneId) {
        return new MedicationRecordSnapshot(
                record.getId(),
                record.getPlanId(),
                record.getElderId(),
                record.getScheduledAt().atZone(zoneId).toInstant(),
                zoneId,
                record.getResult(),
                record.getSource(),
                record.getRecordedAt().atZone(zoneId).toInstant(),
                record.getSourceReference());
    }

    /** 从最近记录起连续统计漏服，遇到已服或跳过即停止。 */
    public int countConsecutiveMissed(Long planId) {
        if (planId == null || planId <= 0) {
            throw exception(MEDICATION_RECORD_INVALID);
        }
        var count = 0;
        for (var record :
                repository.findTop100ByPlanIdAndDeletedFalseOrderByScheduledAtDesc(planId)) {
            if (!MedicationRecordResultEnum.MISSED.getCode().equals(record.getResult())) {
                break;
            }
            count++;
        }
        return count;
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of();
    }
}
