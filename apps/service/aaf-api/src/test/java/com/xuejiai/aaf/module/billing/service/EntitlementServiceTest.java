package com.xuejiai.aaf.module.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.billing.EntitlementTypeEnum;
import com.xuejiai.aaf.common.exception.QuotaExceededException;
import com.xuejiai.aaf.module.billing.domain.EntitlementDef;
import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;
import com.xuejiai.aaf.module.billing.repository.EntitlementDefRepository;
import com.xuejiai.aaf.module.billing.repository.EntitlementLedgerRepository;
import com.xuejiai.aaf.module.billing.repository.EntitlementQuotaRepository;
import com.xuejiai.aaf.module.billing.repository.PlanEntitlementRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** M4 权益扣减并发保护单元测试：consume 必须走行锁读取并在持锁后重校验剩余额度。 */
class EntitlementServiceTest extends BaseMockitoUnitTest {

    @Mock private EntitlementDefRepository defRepository;
    @Mock private EntitlementQuotaRepository quotaRepository;
    @Mock private EntitlementLedgerRepository ledgerRepository;
    @Mock private PlanEntitlementRepository planEntitlementRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    @InjectMocks private EntitlementService entitlementService;

    private EntitlementDef counterDef() {
        var def = new EntitlementDef();
        def.setId(7L);
        def.setCode("AI_CALL");
        def.setType(EntitlementTypeEnum.COUNTABLE.getCode());
        return def;
    }

    private EntitlementQuota quota(long total, long remain) {
        var quota = new EntitlementQuota();
        quota.setId(70L);
        quota.setUserId(100L);
        quota.setEntId(7L);
        quota.setTotal(total);
        quota.setUsed(total - remain);
        quota.setRemain(remain);
        return quota;
    }

    @Test
    @DisplayName("Given 额度充足 When consume Then 走行锁读取并扣减")
    void consume_readsQuotaWithRowLock() {
        when(defRepository.findByCode("AI_CALL")).thenReturn(Optional.of(counterDef()));
        var quota = quota(10L, 10L);
        when(quotaRepository.findByUserIdAndEntIdForUpdate(100L, 7L))
                .thenReturn(Optional.of(quota));

        entitlementService.consume(100L, "AI_CALL", 3L);

        assertThat(quota.getRemain()).isEqualTo(7L);
        assertThat(quota.getUsed()).isEqualTo(3L);
        verify(quotaRepository, never()).findByUserIdAndEntId(any(), any());
    }

    @Test
    @DisplayName("Given check 后额度被并发消耗 When consume Then 持锁重校验拒绝，remain 不变负")
    void consume_rechecksRemainAfterLock() {
        when(defRepository.findByCode("AI_CALL")).thenReturn(Optional.of(counterDef()));
        var quota = quota(10L, 1L);
        when(quotaRepository.findByUserIdAndEntIdForUpdate(100L, 7L))
                .thenReturn(Optional.of(quota));

        assertThatThrownBy(() -> entitlementService.consume(100L, "AI_CALL", 3L))
                .isInstanceOf(QuotaExceededException.class);

        assertThat(quota.getRemain()).isEqualTo(1L);
        verify(quotaRepository, never()).save(any());
    }
}
