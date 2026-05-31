package com.xuejiai.aaf.module.developer.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.developer.domain.DeveloperSubscriptionPlan;
import com.xuejiai.aaf.module.developer.repository.DeveloperSubscriptionPlanRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanCreateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanPageParam;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanUpdateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 开发者订阅套餐管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperSubscriptionPlanCrudService
        extends BaseCrudService<
                DeveloperSubscriptionPlan,
                DeveloperSubscriptionPlanVO,
                DeveloperSubscriptionPlanCreateDTO,
                DeveloperSubscriptionPlanUpdateDTO,
                DeveloperSubscriptionPlanPageParam> {

    private final DeveloperSubscriptionPlanRepository planRepository;

    @Override
    protected JpaRepository<DeveloperSubscriptionPlan, Long> getRepository() {
        return planRepository;
    }

    @Override
    protected JpaSpecificationExecutor<DeveloperSubscriptionPlan> getSpecExecutor() {
        return planRepository;
    }

    @Override
    protected DeveloperSubscriptionPlanVO toVO(DeveloperSubscriptionPlan plan) {
        return new DeveloperSubscriptionPlanVO(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDurationDays(),
                plan.getPrice(),
                plan.getIncludedTokens(),
                plan.getAllowManagedGateway(),
                plan.getAllowSubProxy(),
                plan.getMaxProxyDepth(),
                plan.getStatus(),
                plan.getSortOrder(),
                plan.getCreateTime());
    }

    @Override
    protected DeveloperSubscriptionPlan toEntity(DeveloperSubscriptionPlanCreateDTO dto) {
        planRepository
                .findByCode(dto.code())
                .ifPresent(
                        plan -> {
                            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "开发者套餐编码已存在");
                        });
        var plan = new DeveloperSubscriptionPlan();
        plan.setCode(dto.code());
        applyCreateDTO(plan, dto);
        return plan;
    }

    @Override
    protected void updateEntity(DeveloperSubscriptionPlan plan, DeveloperSubscriptionPlanUpdateDTO dto) {
        if (dto.name() != null) {
            plan.setName(dto.name());
        }
        if (dto.durationDays() != null) {
            plan.setDurationDays(dto.durationDays());
        }
        if (dto.price() != null) {
            plan.setPrice(dto.price());
        }
        if (dto.includedTokens() != null) {
            plan.setIncludedTokens(dto.includedTokens());
        }
        if (dto.allowManagedGateway() != null) {
            plan.setAllowManagedGateway(dto.allowManagedGateway());
        }
        if (dto.allowSubProxy() != null) {
            plan.setAllowSubProxy(dto.allowSubProxy());
        }
        if (dto.maxProxyDepth() != null) {
            plan.setMaxProxyDepth(dto.maxProxyDepth());
        }
        if (dto.status() != null) {
            plan.setStatus(dto.status());
        }
        if (dto.sortOrder() != null) {
            plan.setSortOrder(dto.sortOrder());
        }
    }

    @Override
    protected Specification<DeveloperSubscriptionPlan> buildSpec(
            DeveloperSubscriptionPlanPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                var pattern = "%" + request.getKeyword().trim() + "%";
                predicates.add(
                        cb.or(cb.like(root.get("code"), pattern), cb.like(root.get("name"), pattern)));
            }
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), request.getStatus().trim()));
            }
            if (request.getAllowManagedGateway() != null) {
                predicates.add(
                        cb.equal(root.get("allowManagedGateway"), request.getAllowManagedGateway()));
            }
            if (request.getAllowSubProxy() != null) {
                predicates.add(cb.equal(root.get("allowSubProxy"), request.getAllowSubProxy()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    protected Specification<DeveloperSubscriptionPlan> buildOptionSpec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            var pattern = "%" + keyword.trim() + "%";
            return cb.or(cb.like(root.get("code"), pattern), cb.like(root.get("name"), pattern));
        };
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));
    }

    @Override
    protected String entityName() {
        return "开发者订阅套餐";
    }

    @Override
    protected String entitySlug() {
        return "developer-subscription-plan";
    }

    @Override
    protected String permissionModule() {
        return "developer";
    }

    @Override
    protected String permissionResource() {
        return "subscription-plan";
    }

    @Override
    protected List<String> fieldSets() {
        return List.of("list", "detail", "picker", "export");
    }

    private void applyCreateDTO(DeveloperSubscriptionPlan plan, DeveloperSubscriptionPlanCreateDTO dto) {
        plan.setName(dto.name());
        plan.setDurationDays(dto.durationDays());
        plan.setPrice(dto.price());
        plan.setIncludedTokens(dto.includedTokens());
        plan.setAllowManagedGateway(Boolean.TRUE.equals(dto.allowManagedGateway()));
        plan.setAllowSubProxy(Boolean.TRUE.equals(dto.allowSubProxy()));
        plan.setMaxProxyDepth(dto.maxProxyDepth() == null ? 0 : dto.maxProxyDepth());
        plan.setStatus(dto.status() == null || dto.status().isBlank() ? "ENABLED" : dto.status());
        plan.setSortOrder(dto.sortOrder() == null ? 100 : dto.sortOrder());
    }
}
