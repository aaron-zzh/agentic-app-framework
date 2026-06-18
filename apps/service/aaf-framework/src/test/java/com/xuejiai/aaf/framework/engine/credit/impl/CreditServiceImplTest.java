package com.xuejiai.aaf.framework.engine.credit.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.credit.CreditAccount;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class CreditServiceImplTest extends BaseMockitoUnitTest {

    @Mock private CreditAccountRepository accountRepository;
    @Mock private CreditTransactionRepository transactionRepository;

    @InjectMocks private CreditServiceImpl creditService;

    private CreditAccount account;

    @BeforeEach
    void setUp() {
        account = new CreditAccount();
        account.setId(1L);
        account.setUserId(100L);
        account.setBalance(5L);
        account.setTotalSpent(0L);
        account.setTotalEarned(5L);
        when(accountRepository.findByUserIdForUpdate(100L)).thenReturn(Optional.of(account));
        lenient()
                .when(transactionRepository.findActiveBatchesByAccountId(1L))
                .thenReturn(List.of());
        lenient().when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void spend_正常扣减() {
        creditService.spend(100L, 3L, "test", null);
        assertThat(account.getBalance()).isEqualTo(2L);
    }

    @Test
    void spend_余额不足_抛异常() {
        assertThatThrownBy(() -> creditService.spend(100L, 10L, "test", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分余额不足");
    }

    @Test
    void spendAllowOverdraft_余额不足但在透支上限内_扣成负数() {
        // 余额5，消费8，透支上限10 → 允许，余额变 -3
        creditService.spendAllowOverdraft(100L, 8L, "chat", null, 10L);
        assertThat(account.getBalance()).isEqualTo(-3L);
    }

    @Test
    void spendAllowOverdraft_超出透支上限_抛异常() {
        // 余额5，消费20，透支上限10 → 5+10=15 < 20，拒绝
        assertThatThrownBy(() -> creditService.spendAllowOverdraft(100L, 20L, "chat", null, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分余额不足");
    }

    @Test
    void spendAllowOverdraft_透支上限为0_等同于普通spend() {
        assertThatThrownBy(() -> creditService.spendAllowOverdraft(100L, 10L, "chat", null, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分余额不足");
    }

    @Test
    void spendAllowOverdraft_透支后充值_余额自然回正() {
        // 先透支到 -3
        creditService.spendAllowOverdraft(100L, 8L, "chat", null, 10L);
        assertThat(account.getBalance()).isEqualTo(-3L);

        // 充值 10，earn 直接加余额，负数自动被抵消——无需额外还款逻辑
        creditService.earn(100L, 10L, "topup", null);
        assertThat(account.getBalance()).isEqualTo(7L);
    }

    @Test
    void spendAllowOverdraft_透支后订阅发放积分_同样自动还款() {
        // 透支到 -20
        creditService.spendAllowOverdraft(100L, 25L, "chat", null, 30L);
        assertThat(account.getBalance()).isEqualTo(-20L);

        // 月度订阅发放 100 积分（earnBatch 路径）
        creditService.earnBatch(100L, 100L, "SUBSCRIPTION", "monthly-grant", null, null);
        assertThat(account.getBalance()).isEqualTo(80L);
    }
}
