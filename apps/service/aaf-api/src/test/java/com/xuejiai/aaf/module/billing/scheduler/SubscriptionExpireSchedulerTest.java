package com.xuejiai.aaf.module.billing.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** AAF-099 SubscriptionExpireScheduler 单元测试。 */
class SubscriptionExpireSchedulerTest extends BaseMockitoUnitTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private SubscriptionExpireScheduler scheduler;

    private Subscription expiredPaidSub;
    private SubscriptionPlan freePlan;
    private SubscriptionPlan proPlan;

    @BeforeEach
    void setUp() {
        expiredPaidSub = new Subscription();
        expiredPaidSub.setId(500L);
        expiredPaidSub.setUserId(100L);
        expiredPaidSub.setPlanId(20L); // PRO
        expiredPaidSub.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());
        expiredPaidSub.setStartAt(LocalDateTime.now().minusDays(35));
        expiredPaidSub.setEndAt(LocalDateTime.now().minusHours(1)); // 已过期 1 小时
        expiredPaidSub.setAutoRenew(false); // 用户已取消

        freePlan = new SubscriptionPlan();
        freePlan.setId(10L);
        freePlan.setCode("FREE");
        freePlan.setName("免费版");
        freePlan.setPrice(0L);
        freePlan.setDurationDays(0);
        freePlan.setMonthlyCredits(0L);

        proPlan = new SubscriptionPlan();
        proPlan.setId(20L);
        proPlan.setCode("PRO");
        proPlan.setPrice(2900L);
        proPlan.setMonthlyCredits(200L);

        lenient().when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Given 订阅 pending=FREE 且已到期 When 调度器运行 Then 旧订阅 EXPIRED + 激活 FREE")
    void expiresAndSwitchesToPendingFree() {
        expiredPaidSub.setPendingPlanId(freePlan.getId());
        expiredPaidSub.setPendingYearly(false);
        when(planRepository.findById(freePlan.getId())).thenReturn(Optional.of(freePlan));

        scheduler.processSubscription(expiredPaidSub);

        assertThat(expiredPaidSub.getStatus()).isEqualTo(SubscriptionStatusEnum.EXPIRED.getCode());
        verify(subscriptionService)
                .activateSubscription(eq(100L), eq(freePlan.getId()), eq(null), eq(false));
        verify(subscriptionService, never())
                .activateSubscription(eq(100L), eq(proPlan.getId()), any(), anyBoolean());
    }

    @Test
    @DisplayName("Given 订阅无 pending 已到期 When 调度器运行 Then 冻结=旧EXPIRED + 自动激活 FREE")
    void expiresAndFreezesWhenNoPending() {
        expiredPaidSub.setPendingPlanId(null);
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

        scheduler.processSubscription(expiredPaidSub);

        assertThat(expiredPaidSub.getStatus()).isEqualTo(SubscriptionStatusEnum.EXPIRED.getCode());
        verify(subscriptionService)
                .activateSubscription(eq(100L), eq(freePlan.getId()), eq(null), eq(false));
    }

    @Test
    @DisplayName("Given 订阅 pending=付费档（TEAM）已到期 + 无自动续费 When 调度器运行 Then 走冻结分支")
    void expiresAndFreezesWhenPendingPaid() {
        // pending = TEAM（付费档）
        var teamPlan = new SubscriptionPlan();
        teamPlan.setId(30L);
        teamPlan.setCode("TEAM");
        teamPlan.setPrice(9900L);
        expiredPaidSub.setPendingPlanId(teamPlan.getId());
        expiredPaidSub.setPendingYearly(false);
        when(planRepository.findById(teamPlan.getId())).thenReturn(Optional.of(teamPlan));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

        // 本期没有 SubscriptionAutoRenewService（autoRenewService=null）
        scheduler.processSubscription(expiredPaidSub);

        // 走冻结分支：旧 EXPIRED + 激活 FREE
        assertThat(expiredPaidSub.getStatus()).isEqualTo(SubscriptionStatusEnum.EXPIRED.getCode());
        verify(subscriptionService, times(1))
                .activateSubscription(eq(100L), eq(freePlan.getId()), eq(null), eq(false));
        // 没有激活 TEAM
        verify(subscriptionService, never())
                .activateSubscription(eq(100L), eq(teamPlan.getId()), anyLong(), anyBoolean());
    }
}
