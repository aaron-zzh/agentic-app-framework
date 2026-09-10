package com.xuejiai.aaf.module.billing.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.MembershipCheckoutGuardRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 到期提醒在统一会员锁序内执行并按服务世代幂等。 */
class SubscriptionExpiryReminderSchedulerTest extends BaseMockitoUnitTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private MembershipCheckoutGuardRepository checkoutGuardRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private NotificationService notificationService;

    @InjectMocks private SubscriptionExpiryReminderScheduler scheduler;

    private Subscription subscription;

    @BeforeEach
    void setUp() {
        subscription = new Subscription();
        subscription.setId(500L);
        subscription.setUserId(100L);
        subscription.setPlanId(20L);
        subscription.setStartAt(LocalDateTime.now().minusDays(25));
        subscription.setEndAt(LocalDateTime.now().plusDays(5));
        subscription.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());

        var plan = new SubscriptionPlan();
        plan.setId(20L);
        plan.setName("专业版");
        lenient().when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
        lenient()
                .when(
                        subscriptionRepository.findByUserIdAndStatusForUpdate(
                                100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(subscription));
        lenient()
                .when(subscriptionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Given 当前订阅即将到期且未提醒 When 执行 Then 持 guard 发送并记录提醒事实")
    void sendsReminderUnderMembershipGuard() {
        var now = LocalDateTime.now();

        var sent = scheduler.sendReminderIfNeeded(subscription, now);

        assertThat(sent).isTrue();
        verify(checkoutGuardRepository).ensureGuard(100L);
        verify(checkoutGuardRepository).lockGuard(100L);
        verify(notificationService)
                .send(
                        eq(100L),
                        eq("SUBSCRIPTION_EXPIRY_REMINDER"),
                        eq("订阅即将到期"),
                        anyString(),
                        eq("/settings/subscription"),
                        eq("SUBSCRIPTION"),
                        eq(500L));
        assertThat(subscription.getLastReminderAt()).isEqualTo(now);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    @DisplayName("Given 当前周期已提醒 When 再次执行 Then 不重复发送")
    void skipsAlreadyRemindedSubscription() {
        subscription.setLastReminderAt(subscription.getStartAt().plusDays(1));

        var sent = scheduler.sendReminderIfNeeded(subscription, LocalDateTime.now());

        assertThat(sent).isFalse();
        verify(notificationService, never())
                .send(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given 候选已不是当前 ACTIVE 世代 When 执行 Then 不发送提醒")
    void skipsStaleCandidate() {
        var current = new Subscription();
        current.setId(501L);
        when(subscriptionRepository.findByUserIdAndStatusForUpdate(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(current));

        var sent = scheduler.sendReminderIfNeeded(subscription, LocalDateTime.now());

        assertThat(sent).isFalse();
        verify(notificationService, never())
                .send(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong());
    }
}
