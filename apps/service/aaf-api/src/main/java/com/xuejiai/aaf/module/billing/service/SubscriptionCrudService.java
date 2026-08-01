package com.xuejiai.aaf.module.billing.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionVO;
import com.xuejiai.aaf.module.system.user.api.UserRelationService;

import lombok.RequiredArgsConstructor;

/** 用户订阅实例只读 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionCrudService
        extends ReadonlyCrudService<Subscription, SubscriptionVO, SubscriptionPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "userId",
                    "planId",
                    "status",
                    "startAt",
                    "endAt",
                    "createTime",
                    "updateTime");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRelationService userRelationService;

    @Override
    protected SubscriptionRepository getRepository() {
        return subscriptionRepository;
    }

    @Override
    protected Specification<Subscription> buildSpec(SubscriptionPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getUserId() != null)
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            if (request.getPlanId() != null)
                predicates.add(cb.equal(root.get("planId"), request.getPlanId()));
            if (request.getStatus() != null && !request.getStatus().isBlank())
                predicates.add(cb.equal(root.get("status"), request.getStatus().trim()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected SubscriptionVO toVO(Subscription subscription) {
        return toVOList(List.of(subscription), "detail").getFirst();
    }

    @Override
    protected List<SubscriptionVO> toVOList(List<Subscription> subscriptions, String fieldSet) {
        if (subscriptions.isEmpty()) return List.of();
        var users =
                userRelationService.findRefs(
                        subscriptions.stream()
                                .map(Subscription::getUserId)
                                .collect(Collectors.toSet()));
        Map<Long, SubscriptionPlan> plans =
                planRepository
                        .findAllById(
                                subscriptions.stream()
                                        .map(Subscription::getPlanId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(SubscriptionPlan::getId, plan -> plan));
        Map<Long, SubscriptionPlan> pendingPlans =
                planRepository
                        .findAllById(
                                subscriptions.stream()
                                        .map(Subscription::getPendingPlanId)
                                        .filter(java.util.Objects::nonNull)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(SubscriptionPlan::getId, plan -> plan));
        return subscriptions.stream()
                .map(
                        subscription ->
                                toVO(
                                        subscription,
                                        users.get(subscription.getUserId()),
                                        plans.get(subscription.getPlanId()),
                                        pendingPlans.get(subscription.getPendingPlanId())))
                .toList();
    }

    public SubscriptionVO getActiveForUser(Long userId) {
        var subscription =
                subscriptionRepository
                        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElse(null);
        return subscription == null ? null : toVO(subscription);
    }

    public SubscriptionVO toManagementVO(Subscription subscription) {
        return toVO(subscription);
    }

    private SubscriptionVO toVO(
            Subscription subscription,
            ResourceRefDTO user,
            SubscriptionPlan plan,
            SubscriptionPlan pendingPlan) {
        var planRef = plan == null ? null : new ResourceRefDTO(plan.getId(), plan.getName(), null);
        return new SubscriptionVO(
                subscription.getId(),
                user,
                planRef,
                plan == null ? null : plan.getCode(),
                plan == null ? null : plan.getName(),
                subscription.getStartAt(),
                subscription.getEndAt(),
                subscription.getStatus(),
                subscription.getAutoRenew(),
                subscription.getCancelledAt(),
                pendingPlan == null ? null : pendingPlan.getCode(),
                subscription.getPendingYearly(),
                subscription.getLastReminderAt(),
                subscription.getSourceId(),
                subscription.getCreateTime());
    }
}
