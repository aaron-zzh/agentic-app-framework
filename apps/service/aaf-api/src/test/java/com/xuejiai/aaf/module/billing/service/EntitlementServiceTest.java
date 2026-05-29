package com.xuejiai.aaf.module.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.QuotaExceededException;
import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;
import com.xuejiai.aaf.module.billing.repository.EntitlementLedgerRepository;
import com.xuejiai.aaf.module.billing.repository.EntitlementQuotaRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 权益额度服务单元测试。 */
class EntitlementServiceTest extends BaseMockitoUnitTest {

    @Mock
    private EntitlementQuotaRepository quotaRepository;
    @Mock
    private EntitlementLedgerRepository ledgerRepository;

    @InjectMocks
    private EntitlementService entitlementService;

    @Test
    void consume_额度充足时应扣减成功() {
        var quota = new EntitlementQuota();
        quota.setId(1L);
        quota.setRemain(100L);
        quota.setTotal(200L);
        when(quotaRepository.findByUserIdAndEntId(1L, 10L)).thenReturn(Optional.of(quota));
        when(quotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        entitlementService.consume(1L, 10L, 5L, "测试消费");

        assertThat(quota.getRemain()).isEqualTo(95L);
    }

    @Test
    void consume_额度不足时应抛异常() {
        var quota = new EntitlementQuota();
        quota.setId(1L);
        quota.setRemain(2L);
        quota.setTotal(200L);
        when(quotaRepository.findByUserIdAndEntId(1L, 10L)).thenReturn(Optional.of(quota));

        assertThatThrownBy(() -> entitlementService.consume(1L, 10L, 5L, "测试"))
                .isInstanceOf(QuotaExceededException.class);
    }
}
