package com.xuejiai.aaf.module.elder.profile.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.ELDER_VERSION_CONFLICT;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.MEDICATION_PLAN_INVALID;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.MEDICATION_PLAN_NOT_FOUND;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.MEDICATION_PLAN_STATUS_INVALID;
import static com.xuejiai.aaf.module.elder.profile.MedicationPlanResource.COMMAND_ACTIVATE;
import static com.xuejiai.aaf.module.elder.profile.MedicationPlanResource.COMMAND_DEACTIVATE;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.AuthorizationScopeEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationPlanSourceEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationPlanStatusEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessage;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.profile.api.MedicationApi;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationPlan;
import com.xuejiai.aaf.module.elder.profile.event.MedicationPlanDeactivatedEvent;
import com.xuejiai.aaf.module.elder.profile.mapper.MedicationPlanConvert;
import com.xuejiai.aaf.module.elder.profile.repository.ElderContactRepository;
import com.xuejiai.aaf.module.elder.profile.repository.MedicationPlanRepository;
import com.xuejiai.aaf.module.elder.profile.repository.MedicationRecordRepository;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanVO;

import lombok.RequiredArgsConstructor;

/** 用药计划 CRUD 服务及 profile 对外用药契约实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationPlanService
        extends BaseCrudService<
                MedicationPlan,
                MedicationPlanVO,
                MedicationPlanCreateDTO,
                MedicationPlanUpdateDTO,
                MedicationPlanPageDTO>
        implements MedicationApi {

    private static final Map<String, Object> NO_PAYLOAD = Map.of();

    private final MedicationPlanRepository repository;
    private final MedicationRecordRepository medicationRecordRepository;
    private final MedicationRecordService medicationRecordService;
    private final ElderContactRepository elderContactRepository;
    private final ElderQueryApi elderQueryApi;
    private final InternalMessageSender internalMessageSender;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    protected MedicationPlanRepository getRepository() {
        return repository;
    }

    @Override
    protected MedicationPlanVO toVO(MedicationPlan entity) {
        return new MedicationPlanVO(
                entity.getId(),
                entity.getVersion(),
                entity.getElderId(),
                entity.getMedicationName(),
                entity.getDosageInstructions(),
                reminderTimeTexts(entity),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getSource(),
                entity.getNextReminderAt(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                null);
    }

    @Override
    protected MedicationPlan toEntity(MedicationPlanCreateDTO request) {
        var entity = MedicationPlanConvert.INSTANCE.toEntity(request);
        entity.setReminderTimes(serializeReminderTimes(request.reminderTimes()));
        validate(entity);
        refreshNextReminder(entity, LocalDateTime.now());
        return entity;
    }

    @Override
    protected void updateEntity(MedicationPlan entity, MedicationPlanUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        var previousStatus = entity.getStatus();
        applyRequired(request.elderId(), entity::setElderId);
        applyRequired(request.medicationName(), entity::setMedicationName);
        applyRequired(request.dosageInstructions(), entity::setDosageInstructions);
        if (!request.reminderTimes().isAbsent()) {
            if (request.reminderTimes().isNullValue()) {
                throw exception(MEDICATION_PLAN_INVALID);
            }
            entity.setReminderTimes(serializeReminderTimes(request.reminderTimes().valueOrNull()));
        }
        applyRequired(request.startDate(), entity::setStartDate);
        applyNullable(request.endDate(), entity::setEndDate);
        applyRequired(request.status(), entity::setStatus);
        validate(entity);
        refreshNextReminder(entity, LocalDateTime.now());
        bumpVersion(entity);
        publishDeactivatedIfNeeded(entity, previousStatus);
    }

    @Override
    protected Specification<MedicationPlan> buildSpec(MedicationPlanPageDTO request) {
        return SpecificationBuilder.<MedicationPlan>builder()
                .eqIfPresent("elderId", request.getElderId())
                .likeIfPresent("medicationName", request.getMedicationName())
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("source", request.getSource())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("medicationName");
    }

    /** 启用计划；是否真正到点仍由起止日期共同决定。 */
    @Transactional
    public MedicationPlanVO activate(Long id) {
        return changeStatus(id, COMMAND_ACTIVATE, MedicationPlanStatusEnum.ACTIVE.getCode());
    }

    /** 停用计划并清空下一提醒时间。 */
    @Transactional
    public MedicationPlanVO deactivate(Long id) {
        return changeStatus(id, COMMAND_DEACTIVATE, MedicationPlanStatusEnum.INACTIVE.getCode());
    }

    @Override
    public List<DueMedicationPlan> listDuePlans(
            Instant windowStart, Instant windowEnd, ZoneId zoneId) {
        requireWindow(windowStart, windowEnd, zoneId);
        var startDate = windowStart.atZone(zoneId).toLocalDate();
        var endDate = windowEnd.atZone(zoneId).toLocalDate();
        return repository
                .findActiveInDateRange(
                        MedicationPlanStatusEnum.ACTIVE.getCode(), startDate, endDate)
                .stream()
                .flatMap(plan -> expandDuePlans(plan, windowStart, windowEnd, zoneId).stream())
                .sorted(
                        Comparator.comparing(DueMedicationPlan::scheduledAt)
                                .thenComparing(DueMedicationPlan::planId))
                .toList();
    }

    @Override
    @Transactional
    public MedicationRecordSnapshot recordResult(RecordMedicationResult command) {
        if (command == null
                || command.planId() == null
                || command.scheduledAt() == null
                || command.zoneId() == null) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        var plan =
                repository
                        .findById(command.planId())
                        .orElseThrow(() -> exception(MEDICATION_PLAN_NOT_FOUND));
        var scheduledAt = command.scheduledAt().atZone(command.zoneId()).toLocalDateTime();
        validateScheduledTime(plan, scheduledAt);
        var recordedAt =
                command.recordedAt() == null
                        ? LocalDateTime.now(command.zoneId())
                        : command.recordedAt().atZone(command.zoneId()).toLocalDateTime();
        var result = medicationRecordService.recordResult(plan, command, scheduledAt, recordedAt);
        advanceNextReminder(plan, scheduledAt);
        return result;
    }

    @Override
    public int countConsecutiveMissed(Long planId) {
        return medicationRecordService.countConsecutiveMissed(planId);
    }

    @Override
    public List<MedicationPlanSnapshot> listPlans(Long elderId, LocalDate date) {
        elderQueryApi.requireAccess(elderId, AuthorizationScopeEnum.VIEW_MEDICATION.getCode());
        if (date == null) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        return repository.findAllByElderIdOrderByIdDesc(elderId).stream()
                .map(plan -> toApiSnapshot(plan, date))
                .toList();
    }

    @Override
    @Transactional
    public MedicationPlanSnapshot createConversationPlan(CreateConversationPlan command) {
        if (command == null) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        var access =
                elderQueryApi.requireAccess(
                        command.elderId(), AuthorizationScopeEnum.VIEW_MEDICATION.getCode());
        var request =
                new MedicationPlanCreateDTO(
                        command.elderId(),
                        command.medicationName(),
                        command.dosageInstructions(),
                        command.reminderTimes(),
                        command.startDate(),
                        command.endDate(),
                        MedicationPlanStatusEnum.ACTIVE.getCode(),
                        MedicationPlanSourceEnum.CONVERSATION.getCode());
        var plan = toEntity(request);
        plan.setOrgId(access.orgId());
        plan.setWorkspaceId(access.workspaceId());
        plan.setOwnerId(access.requestingOwnerId());
        plan = repository.save(plan);
        notifyContactsToVerify(plan);
        return toApiSnapshot(plan, LocalDate.now());
    }

    private MedicationPlanSnapshot toApiSnapshot(MedicationPlan plan, LocalDate date) {
        var results =
                medicationRecordRepository
                        .findTop100ByPlanIdAndDeletedFalseOrderByScheduledAtDesc(plan.getId())
                        .stream()
                        .filter(record -> date.equals(record.getScheduledAt().toLocalDate()))
                        .map(
                                record ->
                                        new MedicationResultSnapshot(
                                                record.getScheduledAt().toString(),
                                                record.getResult(),
                                                record.getSource(),
                                                record.getRecordedAt().toString()))
                        .toList();
        return new MedicationPlanSnapshot(
                plan.getId(),
                plan.getMedicationName(),
                plan.getDosageInstructions(),
                reminderTimeTexts(plan),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getStatus(),
                plan.getSource(),
                results);
    }

    private void notifyContactsToVerify(MedicationPlan plan) {
        elderContactRepository.findAllByElderIdOrderByPriorityAscIdAsc(plan.getElderId()).stream()
                .filter(contact -> contact.getUserId() != null)
                .filter(
                        contact ->
                                JsonUtils.parseArray(contact.getAuthorizationScopes(), String.class)
                                        .contains(AuthorizationScopeEnum.VIEW_MEDICATION.getCode()))
                .map(contact -> contact.getUserId())
                .distinct()
                .forEach(
                        userId ->
                                internalMessageSender.send(
                                        InternalMessage.builder()
                                                .userId(userId)
                                                .type("elder-medication")
                                                .title("请核对对话创建的用药计划")
                                                .body(
                                                        "请核对药名“%s”和剂量说明“%s”，如有误请在管理端停用并修正。"
                                                                .formatted(
                                                                        plan.getMedicationName(),
                                                                        plan
                                                                                .getDosageInstructions()))
                                                .entityType("elder.medication.plan")
                                                .entityId(plan.getId())
                                                .build()));
    }

    private MedicationPlanVO changeStatus(Long id, String commandType, String status) {
        return executeCustomUpdateCommand(
                id,
                NO_PAYLOAD,
                new CustomUpdatePlan<MedicationPlan, Map<String, Object>, Object, MedicationPlanVO>(
                        commandType,
                        Set.of("status"),
                        (plan, command) -> {},
                        (plan, command) -> {
                            var previousStatus = plan.getStatus();
                            plan.setStatus(status);
                            refreshNextReminder(plan, LocalDateTime.now());
                            bumpVersion(plan);
                            publishDeactivatedIfNeeded(plan, previousStatus);
                        },
                        (plan, command) -> null,
                        true,
                        (plan, command, ignored) -> {},
                        (plan, command, ignored) -> toVO(plan)));
    }

    private void publishDeactivatedIfNeeded(MedicationPlan plan, String previousStatus) {
        if (MedicationPlanStatusEnum.ACTIVE.getCode().equals(previousStatus)
                && MedicationPlanStatusEnum.INACTIVE.getCode().equals(plan.getStatus())) {
            eventPublisher.publishEvent(
                    new MedicationPlanDeactivatedEvent(
                            plan.getId(), plan.getElderId(), Instant.now()));
        }
    }

    private List<DueMedicationPlan> expandDuePlans(
            MedicationPlan plan, Instant windowStart, Instant windowEnd, ZoneId zoneId) {
        var due = new ArrayList<DueMedicationPlan>();
        var firstDate = max(plan.getStartDate(), windowStart.atZone(zoneId).toLocalDate());
        var lastDate = min(plan.getEndDate(), windowEnd.atZone(zoneId).toLocalDate());
        for (var date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            for (var time : reminderTimes(plan)) {
                var scheduledAt = date.atTime(time).atZone(zoneId).toInstant();
                if (!scheduledAt.isBefore(windowStart) && scheduledAt.isBefore(windowEnd)) {
                    due.add(
                            new DueMedicationPlan(
                                    plan.getId(),
                                    plan.getElderId(),
                                    plan.getMedicationName(),
                                    plan.getDosageInstructions(),
                                    scheduledAt,
                                    zoneId));
                }
            }
        }
        return List.copyOf(due);
    }

    private void validateScheduledTime(MedicationPlan plan, LocalDateTime scheduledAt) {
        var date = scheduledAt.toLocalDate();
        if (date.isBefore(plan.getStartDate())
                || plan.getEndDate() != null && date.isAfter(plan.getEndDate())
                || !reminderTimes(plan).contains(scheduledAt.toLocalTime())) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
    }

    private void advanceNextReminder(MedicationPlan plan, LocalDateTime scheduledAt) {
        var next = nextReminderAfter(plan, scheduledAt);
        if (!Objects.equals(plan.getNextReminderAt(), next)
                && (plan.getNextReminderAt() == null
                        || next == null
                        || next.isAfter(plan.getNextReminderAt()))) {
            plan.setNextReminderAt(next);
            bumpVersion(plan);
            repository.save(plan);
        }
    }

    private void refreshNextReminder(MedicationPlan plan, LocalDateTime after) {
        plan.setNextReminderAt(nextReminderAfter(plan, after));
    }

    private LocalDateTime nextReminderAfter(MedicationPlan plan, LocalDateTime after) {
        if (!MedicationPlanStatusEnum.ACTIVE.getCode().equals(plan.getStatus())) {
            return null;
        }
        var date = max(plan.getStartDate(), after.toLocalDate());
        while (plan.getEndDate() == null || !date.isAfter(plan.getEndDate())) {
            for (var time : reminderTimes(plan)) {
                var candidate = date.atTime(time);
                if (candidate.isAfter(after)) {
                    return candidate;
                }
            }
            date = date.plusDays(1);
        }
        return null;
    }

    private void validate(MedicationPlan plan) {
        if (plan.getElderId() == null
                || plan.getElderId() <= 0
                || isBlankOrTooLong(plan.getMedicationName(), 200)
                || isBlankOrTooLong(plan.getDosageInstructions(), 500)
                || plan.getStartDate() == null
                || plan.getEndDate() != null && plan.getEndDate().isBefore(plan.getStartDate())) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        if (plan.getStatus() == null
                || Arrays.stream(MedicationPlanStatusEnum.ARRAYS)
                        .noneMatch(plan.getStatus()::equals)) {
            throw exception(MEDICATION_PLAN_STATUS_INVALID);
        }
        if (plan.getSource() == null
                || Arrays.stream(MedicationPlanSourceEnum.ARRAYS)
                        .noneMatch(plan.getSource()::equals)) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        reminderTimes(plan);
    }

    private List<LocalTime> reminderTimes(MedicationPlan plan) {
        try {
            var times =
                    JsonUtils.parseArray(plan.getReminderTimes(), String.class).stream()
                            .map(this::parseReminderTime)
                            .distinct()
                            .sorted()
                            .toList();
            if (times.isEmpty() || times.size() > 24) {
                throw exception(MEDICATION_PLAN_INVALID);
            }
            return times;
        } catch (RuntimeException cause) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
    }

    private List<String> reminderTimeTexts(MedicationPlan plan) {
        return reminderTimes(plan).stream().map(LocalTime::toString).toList();
    }

    private String serializeReminderTimes(List<String> values) {
        if (values == null) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        try {
            var times =
                    values.stream()
                            .map(this::parseReminderTime)
                            .distinct()
                            .sorted()
                            .map(LocalTime::toString)
                            .toList();
            if (times.isEmpty() || times.size() > 24) {
                throw exception(MEDICATION_PLAN_INVALID);
            }
            return JsonUtils.toJsonString(times);
        } catch (RuntimeException cause) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
    }

    private LocalTime parseReminderTime(String value) {
        if (value == null || !value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        return LocalTime.parse(value);
    }

    private void requireWindow(Instant start, Instant end, ZoneId zoneId) {
        if (start == null || end == null || zoneId == null || !start.isBefore(end)) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
    }

    private void requireExpectedVersion(MedicationPlan plan, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(plan)) {
            throw exception(ELDER_VERSION_CONFLICT);
        }
    }

    private void bumpVersion(MedicationPlan plan) {
        plan.setVersion(currentVersion(plan) + 1);
    }

    private int currentVersion(MedicationPlan plan) {
        return plan.getVersion() == null ? 0 : plan.getVersion();
    }

    private boolean isBlankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private LocalDate max(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalDate min(LocalDate nullableLeft, LocalDate right) {
        return nullableLeft == null || right.isBefore(nullableLeft) ? right : nullableLeft;
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(MEDICATION_PLAN_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }
}
