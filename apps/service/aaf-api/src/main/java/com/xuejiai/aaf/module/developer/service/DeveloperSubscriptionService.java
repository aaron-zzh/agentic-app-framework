package com.xuejiai.aaf.module.developer.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.developer.domain.DeveloperSubscription;
import com.xuejiai.aaf.module.developer.domain.DeveloperSubscriptionPlan;
import com.xuejiai.aaf.module.developer.repository.DeveloperAccountRepository;
import com.xuejiai.aaf.module.developer.repository.DeveloperSubscriptionPlanRepository;
import com.xuejiai.aaf.module.developer.repository.DeveloperSubscriptionRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeveloperSubscriptionService {

    private final DeveloperSubscriptionPlanRepository planRepository;
    private final DeveloperSubscriptionRepository subscriptionRepository;
    private final DeveloperAccountRepository accountRepository;
    private final DeveloperTokenService tokenService;

    @Transactional(readOnly = true)
    public List<DeveloperSubscriptionPlan> listPlans() {
        return planRepository.findByStatusOrderBySortOrderAsc("ENABLED");
    }

    @Transactional
    public Long subscribe(Long developerId, String planCode) {
        var plan =
                planRepository
                        .findByCode(planCode)
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "开发者套餐不存在"));
        subscriptionRepository
                .findByDeveloperIdAndStatus(developerId, "ACTIVE")
                .ifPresent(
                        old -> {
                            old.setStatus("CANCELLED");
                            subscriptionRepository.save(old);
                        });
        var subscription = new DeveloperSubscription();
        subscription.setDeveloperId(developerId);
        subscription.setPlanId(plan.getId());
        subscription.setStartAt(LocalDateTime.now());
        subscription.setEndAt(
                plan.getDurationDays() > 0
                        ? LocalDateTime.now().plusDays(plan.getDurationDays())
                        : null);
        subscriptionRepository.save(subscription);

        var account =
                accountRepository
                        .findById(developerId)
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "开发者账户不存在"));
        account.setLicenseTier(plan.getCode());
        account.setAllowManagedGateway(plan.getAllowManagedGateway());
        account.setAllowSubProxy(plan.getAllowSubProxy());
        account.setMaxProxyDepth(plan.getMaxProxyDepth());
        accountRepository.save(account);

        if (plan.getIncludedTokens() > 0) {
            tokenService.earn(developerId, plan.getIncludedTokens(), "DEVELOPER_SUBSCRIPTION", plan.getCode());
        }
        return subscription.getId();
    }

    @Transactional(readOnly = true)
    public DeveloperSubscriptionVO current(Long developerId) {
        var subscription = subscriptionRepository.findByDeveloperIdAndStatus(developerId, "ACTIVE").orElse(null);
        if (subscription == null) {
            return null;
        }
        var plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        return new DeveloperSubscriptionVO(
                subscription.getId(),
                plan == null ? null : plan.getCode(),
                plan == null ? null : plan.getName(),
                subscription.getStartAt(),
                subscription.getEndAt(),
                subscription.getStatus());
    }
}
