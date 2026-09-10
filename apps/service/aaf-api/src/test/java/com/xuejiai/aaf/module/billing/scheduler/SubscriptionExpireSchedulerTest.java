package com.xuejiai.aaf.module.billing.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 到期候选统一委托会员服务按 guard→ACTIVE 锁序处理。 */
class SubscriptionExpireSchedulerTest extends BaseMockitoUnitTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private SubscriptionExpireScheduler scheduler;

    @Test
    @DisplayName("Given 到期候选仍是当前世代 When 处理 Then 委托会员服务并返回成功")
    void processSubscription_delegatesToMembershipService() {
        var candidate = candidate(500L, 100L);
        when(subscriptionService.expireAndSwitchToFree(100L, 500L)).thenReturn(true);

        var processed = scheduler.processSubscription(candidate);

        assertThat(processed).isTrue();
        verify(subscriptionService).expireAndSwitchToFree(100L, 500L);
    }

    @Test
    @DisplayName("Given 到期候选已不是当前世代 When 处理 Then 返回未处理")
    void processSubscription_returnsFalseForStaleCandidate() {
        var candidate = candidate(501L, 100L);
        when(subscriptionService.expireAndSwitchToFree(100L, 501L)).thenReturn(false);

        var processed = scheduler.processSubscription(candidate);

        assertThat(processed).isFalse();
        verify(subscriptionService).expireAndSwitchToFree(100L, 501L);
    }

    private Subscription candidate(Long id, Long userId) {
        var subscription = new Subscription();
        subscription.setId(id);
        subscription.setUserId(userId);
        return subscription;
    }
}
