package com.xuejiai.aaf.module.billing.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;
import com.xuejiai.aaf.module.billing.repository.CreditRedeemCodeRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionSkuRepository;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuCreateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuUpdateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuVO;

import lombok.RequiredArgsConstructor;

/** 订阅 SKU 受控管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionPlanSkuCrudService
        extends BaseCrudService<
                SubscriptionSku,
                SubscriptionSkuVO,
                SubscriptionSkuCreateDTO,
                SubscriptionSkuUpdateDTO,
                SubscriptionSkuPageParam> {

    private final SubscriptionSkuRepository skuRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRecordRepository recordRepository;
    private final CreditRedeemCodeRepository redeemCodeRepository;

    @Override
    protected SubscriptionSkuRepository getRepository() {
        return skuRepository;
    }

    @Override
    protected Specification<SubscriptionSku> buildSpec(SubscriptionSkuPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getPlanId() != null) {
                predicates.add(cb.equal(root.get("planId"), request.getPlanId()));
            }
            if (StringUtils.hasText(request.getSkuCode())) {
                predicates.add(cb.equal(root.get("code"), request.getSkuCode().trim()));
            }
            if (StringUtils.hasText(request.getBillingCycle())) {
                predicates.add(
                        cb.equal(root.get("billingCycle"), request.getBillingCycle().trim()));
            }
            if (StringUtils.hasText(request.getStatus())) {
                predicates.add(cb.equal(root.get("status"), request.getStatus().trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected Specification<SubscriptionSku> buildOptionSpec(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        var normalized = "%" + keyword.trim() + "%";
        return (root, query, cb) -> cb.like(root.get("code"), normalized);
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("code");
    }

    @Override
    protected SubscriptionSku toEntity(SubscriptionSkuCreateDTO dto) {
        requireUniqueCode(dto.skuCode(), null);
        var sku = new SubscriptionSku();
        sku.setPlanId(dto.planId());
        sku.setCode(normalize(dto.skuCode()));
        sku.setBillingCycle(normalize(dto.billingCycle()));
        sku.setCycleMonths(dto.cycleMonths());
        sku.setPrice(dto.price());
        sku.setMarketPrice(dto.marketPrice());
        sku.setStatus(StringUtils.hasText(dto.status()) ? normalize(dto.status()) : "ENABLED");
        sku.setSort(dto.sort() == null ? 100 : dto.sort());
        sku.setExt(dto.ext());
        validateCommercialShape(sku);
        return sku;
    }

    @Override
    protected void beforeUpdate(SubscriptionSku sku, SubscriptionSkuUpdateDTO dto) {
        if (!isReferenced(sku.getId())) return;
        if ((dto.planId() != null && !dto.planId().equals(sku.getPlanId()))
                || (StringUtils.hasText(dto.skuCode())
                        && !normalize(dto.skuCode()).equals(sku.getCode()))
                || (StringUtils.hasText(dto.billingCycle())
                        && !normalize(dto.billingCycle()).equals(sku.getBillingCycle()))
                || (dto.cycleMonths() != null && !dto.cycleMonths().equals(sku.getCycleMonths()))) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已被引用的 SKU 不允许修改归属、编码或周期");
        }
    }

    @Override
    protected void updateEntity(SubscriptionSku sku, SubscriptionSkuUpdateDTO dto) {
        if (dto.planId() != null) sku.setPlanId(dto.planId());
        if (StringUtils.hasText(dto.skuCode())) {
            requireUniqueCode(dto.skuCode(), sku.getId());
            sku.setCode(normalize(dto.skuCode()));
        }
        if (StringUtils.hasText(dto.billingCycle())) {
            sku.setBillingCycle(normalize(dto.billingCycle()));
        }
        if (dto.cycleMonths() != null) sku.setCycleMonths(dto.cycleMonths());
        if (dto.price() != null) sku.setPrice(dto.price());
        if (dto.marketPrice() != null) sku.setMarketPrice(dto.marketPrice());
        if (StringUtils.hasText(dto.status())) sku.setStatus(normalize(dto.status()));
        if (dto.sort() != null) sku.setSort(dto.sort());
        if (dto.ext() != null) sku.setExt(dto.ext());
        validateCommercialShape(sku);
    }

    @Override
    protected void beforeDelete(SubscriptionSku sku) {
        if (sku.isFree() || isReferenced(sku.getId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "系统或已被引用的 SKU 不允许删除");
        }
    }

    @Override
    protected SubscriptionSkuVO toVO(SubscriptionSku sku) {
        var plan = planRepository.findById(sku.getPlanId()).orElse(null);
        return new SubscriptionSkuVO(
                sku.getId(),
                plan == null ? null : new ResourceRefDTO(plan.getId(), plan.getName(), null),
                sku.getCode(),
                sku.getBillingCycle(),
                sku.getCycleMonths(),
                sku.getPrice(),
                sku.getMarketPrice(),
                sku.getStatus(),
                sku.getSort(),
                sku.getExt(),
                sku.getCreateTime(),
                sku.getUpdateTime());
    }

    private void validateCommercialShape(SubscriptionSku sku) {
        var plan = requirePlan(sku.getPlanId());
        if (!List.of("ENABLED", "DISABLED").contains(sku.getStatus())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "SKU 状态必须为 ENABLED 或 DISABLED");
        }
        if (sku.isFree()) {
            if (!"FREE".equals(plan.getCode())
                    || !"PERPETUAL".equals(sku.getBillingCycle())
                    || sku.getCycleMonths() != 0
                    || sku.getPrice() != 0
                    || sku.getMarketPrice() != 0) {
                throw new BusinessException(
                        com.xuejiai.aaf.module.billing.ErrorCodeConstants
                                .SUBSCRIPTION_SKU_PLAN_INVALID);
            }
            return;
        }
        var validCycle =
                ("MONTH".equals(sku.getBillingCycle()) && sku.getCycleMonths() == 1)
                        || ("QUARTER".equals(sku.getBillingCycle()) && sku.getCycleMonths() == 3)
                        || ("YEAR".equals(sku.getBillingCycle()) && sku.getCycleMonths() == 12);
        if ("FREE".equals(plan.getCode())
                || !validCycle
                || sku.getPrice() <= 0
                || sku.getMarketPrice() < sku.getPrice()) {
            throw new BusinessException(
                    com.xuejiai.aaf.module.billing.ErrorCodeConstants
                            .SUBSCRIPTION_SKU_PLAN_INVALID);
        }
    }

    private SubscriptionPlan requirePlan(Long planId) {
        return planRepository
                .findById(planId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        com.xuejiai.aaf.module.billing.ErrorCodeConstants
                                                .SUBSCRIPTION_PLAN_NOT_FOUND));
    }

    private void requireUniqueCode(String code, Long currentId) {
        skuRepository
                .findByCode(normalize(code))
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(
                        existing -> {
                            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "SKU 编码已存在");
                        });
    }

    private boolean isReferenced(Long skuId) {
        return subscriptionRepository.existsBySkuIdOrPendingSkuId(skuId, skuId)
                || recordRepository.existsBySkuId(skuId)
                || redeemCodeRepository.existsBySkuId(skuId);
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
