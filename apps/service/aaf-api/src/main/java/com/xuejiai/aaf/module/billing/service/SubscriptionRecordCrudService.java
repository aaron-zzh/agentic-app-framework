package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionSkuRepository;
import com.xuejiai.aaf.module.billing.vo.CompensationResolutionDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionRecordPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionRecordVO;
import com.xuejiai.aaf.module.system.user.api.UserRelationService;

import lombok.RequiredArgsConstructor;

/** 订阅购买流水只读查询及人工补偿事实记录。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionRecordCrudService
        extends ReadonlyCrudService<
                SubscriptionRecord, SubscriptionRecordVO, SubscriptionRecordPageParam> {

    public static final String COMMAND_RESOLVE_COMPENSATION = "resolve-compensation";

    private final SubscriptionRecordRepository recordRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionSkuRepository skuRepository;
    private final UserRelationService userRelationService;

    @Override
    protected SubscriptionRecordRepository getRepository() {
        return recordRepository;
    }

    @Override
    protected Specification<SubscriptionRecord> buildSpec(SubscriptionRecordPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getUserId() != null)
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            if (request.getPlanId() != null)
                predicates.add(cb.equal(root.get("planId"), request.getPlanId()));
            if (request.getSkuId() != null)
                predicates.add(cb.equal(root.get("skuId"), request.getSkuId()));
            addText(predicates, cb, root, "operation", request.getOperation());
            addText(predicates, cb, root, "payStatus", request.getPayStatus());
            addText(predicates, cb, root, "fulfillmentStatus", request.getFulfillmentStatus());
            addText(predicates, cb, root, "valueStatus", request.getValueStatus());
            addText(predicates, cb, root, "exceptionCode", request.getExceptionCode());
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected SubscriptionRecordVO toVO(SubscriptionRecord record) {
        return toVOList(List.of(record), "detail").getFirst();
    }

    @Override
    protected List<SubscriptionRecordVO> toVOList(
            List<SubscriptionRecord> records, String fieldSet) {
        if (records.isEmpty()) return List.of();
        var users =
                userRelationService.findRefs(
                        records.stream()
                                .map(SubscriptionRecord::getUserId)
                                .collect(Collectors.toSet()));
        Map<Long, SubscriptionPlan> plans =
                planRepository
                        .findAllById(
                                records.stream()
                                        .map(SubscriptionRecord::getPlanId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(SubscriptionPlan::getId, plan -> plan));
        Map<Long, SubscriptionSku> skus =
                skuRepository
                        .findAllById(
                                records.stream()
                                        .map(SubscriptionRecord::getSkuId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(SubscriptionSku::getId, sku -> sku));
        return records.stream()
                .map(
                        record ->
                                toVO(
                                        record,
                                        users.get(record.getUserId()),
                                        plans.get(record.getPlanId()),
                                        skus.get(record.getSkuId())))
                .toList();
    }

    @Transactional
    public SubscriptionRecordVO resolveCompensation(
            Long recordId, CompensationResolutionDTO command) {
        var plan =
                new CustomUpdatePlan<
                        SubscriptionRecord, CompensationResolutionDTO, Void, SubscriptionRecordVO>(
                        COMMAND_RESOLVE_COMPENSATION,
                        Set.of("compensationResolvedAt", "compensationResult"),
                        (record, request) -> requireResolvable(record),
                        (record, request) -> {
                            record.setCompensationResolvedAt(LocalDateTime.now());
                            record.setCompensationResult(request.result().trim());
                        },
                        (record, request) -> null,
                        true,
                        (record, request, ignored) -> {},
                        (record, request, ignored) -> toVO(record));
        return executeCustomUpdateCommand(recordId, command, plan);
    }

    private void requireResolvable(SubscriptionRecord record) {
        if (!"COMPENSATION_PENDING".equals(record.getFulfillmentStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该流水不处于人工补偿状态");
        }
        if (record.getCompensationResolvedAt() != null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该补偿流水已记录处理结果");
        }
    }

    private void addText(
            List<jakarta.persistence.criteria.Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<SubscriptionRecord> root,
            String field,
            String value) {
        if (StringUtils.hasText(value)) {
            predicates.add(cb.equal(root.get(field), value.trim()));
        }
    }

    private SubscriptionRecordVO toVO(
            SubscriptionRecord record,
            ResourceRefDTO user,
            SubscriptionPlan plan,
            SubscriptionSku sku) {
        return new SubscriptionRecordVO(
                record.getId(),
                user,
                plan == null ? null : new ResourceRefDTO(plan.getId(), plan.getName(), null),
                sku == null ? null : new ResourceRefDTO(sku.getId(), sku.getCode(), null),
                record.getOperation(),
                record.getPayOrderId(),
                record.getSkuPriceSnapshot(),
                record.getPayPrice(),
                record.getPayStatus(),
                record.getPayTime(),
                record.getFulfillmentStatus(),
                record.getCheckoutGenerationId(),
                record.getCheckoutCalculatedAt(),
                record.getCheckoutValueSnapshot(),
                record.getServiceGenerationId(),
                record.getServiceStartAt(),
                record.getServiceEndAt(),
                record.getValueStatus(),
                record.getSupersededByRecordId(),
                record.getSupersededAt(),
                record.getExceptionCode(),
                record.getExceptionReason(),
                record.getExceptionDetectedAt(),
                record.getCompensationResolvedAt(),
                record.getCompensationResult(),
                record.getCreateTime(),
                record.getUpdateTime());
    }
}
