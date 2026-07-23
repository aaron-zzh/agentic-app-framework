package com.xuejiai.aaf.module.billing.service;

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
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.billing.domain.EntitlementDef;
import com.xuejiai.aaf.module.billing.domain.PlanEntitlement;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.EntitlementDefRepository;
import com.xuejiai.aaf.module.billing.repository.PlanEntitlementRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanCreateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanUpdateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanVO;

import lombok.RequiredArgsConstructor;

/** 订阅套餐管理 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionPlanCrudService
        extends BaseCrudService<
                SubscriptionPlan,
                SubscriptionPlanVO,
                SubscriptionPlanCreateDTO,
                SubscriptionPlanUpdateDTO,
                SubscriptionPlanPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "code",
                    "name",
                    "durationDays",
                    "price",
                    "marketPrice",
                    "status",
                    "sort",
                    "monthlyCredits",
                    "createTime",
                    "updateTime");

    private final SubscriptionPlanRepository planRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final EntitlementDefRepository entitlementDefRepository;

    @Override
    protected SubscriptionPlanRepository getRepository() {
        return planRepository;
    }

    @Override
    protected Specification<SubscriptionPlan> buildSpec(SubscriptionPlanPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(request.getKeyword())) {
                var pattern = "%" + request.getKeyword().trim() + "%";
                predicates.add(
                        cb.or(
                                cb.like(root.get("code"), pattern),
                                cb.like(root.get("name"), pattern)));
            }
            if (StringUtils.hasText(request.getStatus())) {
                predicates.add(cb.equal(root.get("status"), request.getStatus().trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected Specification<SubscriptionPlan> buildOptionSpec(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) return null;
            var pattern = "%" + keyword.trim() + "%";
            return cb.or(cb.like(root.get("code"), pattern), cb.like(root.get("name"), pattern));
        };
    }

    @Override
    protected SubscriptionPlanVO toVO(SubscriptionPlan plan) {
        return toVOList(List.of(plan), "detail").getFirst();
    }

    @Override
    protected List<SubscriptionPlanVO> toVOList(List<SubscriptionPlan> plans, String fieldSet) {
        if (plans.isEmpty()) return List.of();
        var planIds = plans.stream().map(SubscriptionPlan::getId).collect(Collectors.toSet());
        Map<Long, List<PlanEntitlement>> entitlementsByPlan =
                planEntitlementRepository.findByPlanIdIn(planIds).stream()
                        .collect(Collectors.groupingBy(PlanEntitlement::getPlanId));
        var entitlementIds =
                entitlementsByPlan.values().stream()
                        .flatMap(List::stream)
                        .map(PlanEntitlement::getEntId)
                        .collect(Collectors.toSet());
        Map<Long, EntitlementDef> definitions =
                entitlementDefRepository.findAllById(entitlementIds).stream()
                        .collect(Collectors.toMap(EntitlementDef::getId, definition -> definition));
        return plans.stream()
                .map(
                        plan ->
                                toVO(
                                        plan,
                                        entitlementsByPlan.getOrDefault(plan.getId(), List.of()),
                                        definitions))
                .toList();
    }

    @Override
    protected SubscriptionPlan toEntity(SubscriptionPlanCreateDTO dto) {
        planRepository
                .findByCode(dto.code())
                .ifPresent(
                        plan -> {
                            throw new BusinessException(
                                    GlobalErrorCode.BAD_REQUEST, "套餐编码已存在: " + dto.code());
                        });
        var plan = new SubscriptionPlan();
        plan.setCode(dto.code().trim());
        applyCreate(plan, dto);
        return plan;
    }

    @Override
    protected void updateEntity(SubscriptionPlan plan, SubscriptionPlanUpdateDTO dto) {
        if (StringUtils.hasText(dto.name())) plan.setName(dto.name().trim());
        if (dto.durationDays() != null) plan.setDurationDays(dto.durationDays());
        if (dto.price() != null) plan.setPrice(dto.price());
        if (dto.marketPrice() != null) plan.setMarketPrice(dto.marketPrice());
        if (StringUtils.hasText(dto.status())) plan.setStatus(dto.status().trim());
        if (dto.sort() != null) plan.setSort(dto.sort());
        if (dto.monthlyCredits() != null) plan.setMonthlyCredits(dto.monthlyCredits());
        if (dto.ext() != null) plan.setExt(dto.ext());
    }

    /** 面向客户的启用套餐目录。 */
    public List<SubscriptionPlanVO> catalog() {
        return toVOList(planRepository.findByStatusOrderBySortAsc("ENABLED"), "catalog");
    }

    private SubscriptionPlanVO toVO(
            SubscriptionPlan plan,
            List<PlanEntitlement> entitlements,
            Map<Long, EntitlementDef> definitions) {
        var items =
                entitlements.stream()
                        .map(
                                entitlement -> {
                                    var definition = definitions.get(entitlement.getEntId());
                                    return new SubscriptionPlanVO.PlanEntitlementVO(
                                            definition == null ? null : definition.getCode(),
                                            definition == null ? null : definition.getName(),
                                            definition == null ? null : definition.getType(),
                                            definition == null ? null : definition.getUnit(),
                                            entitlement.getQuota(),
                                            entitlement.getResetCycle(),
                                            entitlement.getRefillPrice());
                                })
                        .toList();
        var yearlyPrice = Math.round(plan.getPrice() * 12 * 0.8);
        return new SubscriptionPlanVO(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDurationDays(),
                plan.getPrice(),
                yearlyPrice,
                plan.getMarketPrice(),
                plan.getMonthlyCredits(),
                plan.getExt(),
                items,
                plan.getStatus(),
                plan.getSort(),
                plan.getCreateTime(),
                plan.getUpdateTime());
    }

    private void applyCreate(SubscriptionPlan plan, SubscriptionPlanCreateDTO dto) {
        plan.setName(dto.name().trim());
        plan.setDurationDays(dto.durationDays());
        plan.setPrice(dto.price());
        plan.setMarketPrice(dto.marketPrice());
        plan.setStatus(StringUtils.hasText(dto.status()) ? dto.status().trim() : "ENABLED");
        plan.setSort(dto.sort() == null ? 100 : dto.sort());
        plan.setMonthlyCredits(dto.monthlyCredits() == null ? 0L : dto.monthlyCredits());
        plan.setExt(dto.ext());
    }
}
