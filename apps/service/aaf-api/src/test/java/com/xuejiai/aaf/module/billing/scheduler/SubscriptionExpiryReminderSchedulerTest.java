package com.xuejiai.aaf.module.billing.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** AAF-099 SubscriptionExpiryReminderScheduler 单元测试。 */
class SubscriptionExpiryReminderSchedulerTest extends BaseMockitoUnitTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private NotificationService notificationService;

    @InjectMocks private SubscriptionExpiryReminderScheduler scheduler;

    private Subscription sub;
    private SubscriptionPlan proPlan;

    @BeforeEach
    void setUp() {
        sub = new Subscription();
        sub.setId(500L);
        sub.setUserId(100L);
        sub.setPlanId(20L);
        sub.setStartAt(LocalDateTime.now().minusDays(25));
        sub.setEndAt(LocalDateTime.now().plusDays(5)); // 5 天后到期，在 7 天提醒窗口内

        proPlan = new SubscriptionPlan();
        proPlan.setId(20L);
        proPlan.setCode("PRO");
        proPlan.setName("PRO");

        lenient().when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(planRepository.findById(20L)).thenReturn(Optional.of(proPlan));
    }

    @Test
    @DisplayName("Given 订阅 5 天后到期且本周期未发提醒 When 调度器运行 Then 发送站内信 + 写 last_reminder_at")
    void sendsReminderForExpiringSubscription() {
        var now = LocalDateTime.now();

        boolean sent = scheduler.sendReminderIfNeeded(sub, now);

        assertThat(sent).isTrue();
        verify(notificationService)
                .send(
                        eq(100L),
                        eq("SUBSCRIPTION_EXPIRY_REMINDER"),
                        anyString(),
                        anyString(),
                        eq("/settings/subscription"),
                        eq("SUBSCRIPTION"),
                        eq(500L));
        assertThat(sub.getLastReminderAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Given 本周期已发过提醒（last_reminder_at > start_at） When 再次调度 Then 不重复发送")
    void skipsAlreadyRemindedInCurrentPeriod() {
        // 上次提醒在当前周期内（start_at 之后）
        sub.setLastReminderAt(sub.getStartAt().plusDays(1));
        var now = LocalDateTime.now();

        boolean sent = scheduler.sendReminderIfNeeded(sub, now);

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

    @Test
    @DisplayName("Given lastReminderAt 在上一周期 When 新周期内调度 Then 重新发送")
    void resendsForNewPeriod() {
        // last_reminder_at 在 start_at 之前（属上周期）
        sub.setLastReminderAt(sub.getStartAt().minusDays(10));
        var now = LocalDateTime.now();

        boolean sent = scheduler.sendReminderIfNeeded(sub, now);

        assertThat(sent).isTrue();
        verify(notificationService, times(1))
                .send(
                        eq(100L),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong());
    }
}
