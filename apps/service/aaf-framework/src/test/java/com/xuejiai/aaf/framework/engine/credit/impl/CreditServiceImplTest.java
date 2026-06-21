package com.xuejiai.aaf.framework.engine.credit.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.credit.CreditAccount;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransaction;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionType;
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
        lenient()
                .when(accountRepository.findByUserIdForUpdate(100L))
                .thenReturn(Optional.of(account));
        lenient()
                .when(transactionRepository.findActiveBatchesByAccountId(1L))
                .thenReturn(List.of());
        lenient()
                .when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            CreditTransaction tx = inv.getArgument(0);
                            if (tx.getId() == null) {
                                tx.setId(System.nanoTime());
                            }
                            return tx;
                        });
        lenient().when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void spend_正常扣减() {
        creditService.spend(100L, 3L, "test", null, null, 0L, null, null);
        assertThat(account.getBalance()).isEqualTo(2L);
    }

    @Test
    void spend_余额不足_抛异常() {
        assertThatThrownBy(() -> creditService.spend(100L, 10L, "test", null, null, 0L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分余额不足");
    }

    @Test
    void spendAllowOverdraft_余额不足但在透支上限内_扣成负数() {
        // 余额5，消费8，透支上限10 → 允许，余额变 -3
        creditService.spend(100L, 8L, "chat", null, null, 10L, null, null);
        assertThat(account.getBalance()).isEqualTo(-3L);
    }

    @Test
    void spendAllowOverdraft_超出透支上限_抛异常() {
        // 余额5，消费20，透支上限10 → 5+10=15 < 20，拒绝
        assertThatThrownBy(
                        () -> creditService.spend(100L, 20L, "chat", null, null, 10L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分余额不足");
    }

    @Test
    void spendAllowOverdraft_透支上限为0_等同于普通spend() {
        assertThatThrownBy(() -> creditService.spend(100L, 10L, "chat", null, null, 0L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分余额不足");
    }

    @Test
    void spendAllowOverdraft_透支后充值_余额自然回正() {
        creditService.spend(100L, 8L, "chat", null, null, 10L, null, null);
        assertThat(account.getBalance()).isEqualTo(-3L);

        creditService.earn(100L, 10L, "topup", null);
        assertThat(account.getBalance()).isEqualTo(7L);
    }

    @Test
    void spendAllowOverdraft_透支后订阅发放积分_同样自动还款() {
        creditService.spend(100L, 25L, "chat", null, null, 30L, null, null);
        assertThat(account.getBalance()).isEqualTo(-20L);

        creditService.earnBatch(100L, 100L, "SUBSCRIPTION", "monthly-grant", null, null);
        assertThat(account.getBalance()).isEqualTo(80L);
    }

    // ========== refund 测试（AAF-099 F3） ==========

    @Test
    @DisplayName("Given SPEND 流水存在 When refund Then 写反向 EARN 流水并回补余额")
    void refund_writesReverseEarn() {
        // 准备：原扣款流水
        var original = new CreditTransaction();
        original.setId(500L);
        original.setAccountId(1L);
        original.setType(CreditTransactionType.SPEND);
        original.setAmount(120L);
        original.setBizType("AI_USAGE");
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(original));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsRefundForOriginalTx("REFUND_AIGC_FAIL", "500"))
                .thenReturn(false);
        when(transactionRepository.findActiveBatchesByAccountId(1L)).thenReturn(List.of());

        // 调用
        Long refundTxId = creditService.refund(500L, "AIGC 任务失败");

        // 断言
        assertThat(refundTxId).isNotNull();
        assertThat(account.getBalance()).isEqualTo(5L + 120L);
        assertThat(account.getTotalEarned()).isEqualTo(5L + 120L);
        ArgumentCaptor<CreditTransaction> txCaptor =
                ArgumentCaptor.forClass(CreditTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        var saved = txCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(CreditTransactionType.EARN);
        assertThat(saved.getAmount()).isEqualTo(120L);
        assertThat(saved.getSource()).isEqualTo("REFUND_AIGC_FAIL");
        assertThat(saved.getBizId()).isEqualTo("500");
        assertThat(saved.getBatchType()).isEqualTo("REFUND");
        assertThat(saved.getRemark()).isEqualTo("AIGC 任务失败");
    }

    @Test
    @DisplayName("Given 同一原流水 When refund 第二次 Then 幂等返回 null 不重复写")
    void refund_idempotentByBizId() {
        var original = new CreditTransaction();
        original.setId(500L);
        original.setAccountId(1L);
        original.setType(CreditTransactionType.SPEND);
        original.setAmount(120L);
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(original));
        when(transactionRepository.existsRefundForOriginalTx("REFUND_AIGC_FAIL", "500"))
                .thenReturn(true);

        Long refundTxId = creditService.refund(500L, "重复退还");

        assertThat(refundTxId).isNull();
        verify(transactionRepository, never()).save(any());
        // 余额不变
        assertThat(account.getBalance()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Given creditTxId=null 或不存在或非 SPEND When refund Then 返回 null")
    void refund_invalidTxIdReturnsNull() {
        // null
        assertThat(creditService.refund(null, "test")).isNull();

        // 不存在
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThat(creditService.refund(999L, "test")).isNull();

        // 非 SPEND 类型
        var nonSpend = new CreditTransaction();
        nonSpend.setId(800L);
        nonSpend.setAccountId(1L);
        nonSpend.setType(CreditTransactionType.EARN);
        nonSpend.setAmount(50L);
        when(transactionRepository.findById(800L)).thenReturn(Optional.of(nonSpend));
        assertThat(creditService.refund(800L, "test")).isNull();
    }

    @Test
    @DisplayName("Given 退还时账户有活跃批次 When refund Then expireAt 取最近到期批次")
    void refund_inheritsNearestExpireAt() {
        var original = new CreditTransaction();
        original.setId(500L);
        original.setAccountId(1L);
        original.setType(CreditTransactionType.SPEND);
        original.setAmount(80L);
        when(transactionRepository.findById(500L)).thenReturn(Optional.of(original));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsRefundForOriginalTx("REFUND_AIGC_FAIL", "500"))
                .thenReturn(false);

        var nearestExpire = LocalDateTime.now().plusDays(5);
        var batch = new CreditTransaction();
        batch.setId(200L);
        batch.setAccountId(1L);
        batch.setExpireAt(nearestExpire);
        batch.setRemain(50L);
        when(transactionRepository.findActiveBatchesByAccountId(1L)).thenReturn(List.of(batch));

        creditService.refund(500L, "test");

        ArgumentCaptor<CreditTransaction> txCaptor =
                ArgumentCaptor.forClass(CreditTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getExpireAt()).isEqualTo(nearestExpire);
    }

    // ========== settleSubscriptionUpgrade 测试（AAF-099 F2） ==========

    @Test
    @DisplayName("Given 旧月度 200 已用 100 When 升级到月度 400 Then 三笔流水 + balance=300")
    void settleSubscriptionUpgrade_writesThreeCreditTransactions() {
        // 准备：账户 balance=100（旧用了 100）
        account.setBalance(100L);

        // 旧 SUBSCRIPTION 批次：amount=200, remain=100
        var oldBatch = new CreditTransaction();
        oldBatch.setId(200L);
        oldBatch.setAccountId(1L);
        oldBatch.setType(CreditTransactionType.EARN);
        oldBatch.setAmount(200L);
        oldBatch.setRemain(100L);
        oldBatch.setBatchType("SUBSCRIPTION");
        when(transactionRepository.findActiveBatchesByAccountId(1L)).thenReturn(List.of(oldBatch));

        // 调用：升级到月度 400
        var newSubId = 999L;
        var expireAt = LocalDateTime.now().plusDays(30);
        var result = creditService.settleSubscriptionUpgrade(100L, 400L, newSubId, expireAt);

        // 验证三笔流水语义
        // Step 1 EXPIRE: oldBatch.remain=0, balance -= 100 → 0
        // Step 2 EARN: balance += 400 → 400
        // Step 3 SPEND: balance -= 100 → 300, newBatch.remain = 400 - 100 = 300
        assertThat(account.getBalance()).isEqualTo(300L);
        assertThat(oldBatch.getRemain()).isZero();
        assertThat(account.getTotalEarned()).isEqualTo(5L + 400L);
        assertThat(account.getTotalSpent()).isEqualTo(100L);
        assertThat(result.expireTxId()).isNotNull();
        assertThat(result.earnTxId()).isNotNull();
        assertThat(result.spendTxId()).isNotNull();
        assertThat(result.oldUsed()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Given 旧批次完全未用 When 升级 Then EXPIRE 旧 amount + EARN 新 amount，oldUsed=0")
    void settleSubscriptionUpgrade_noOldUsage_skipsSpendStep() {
        // 准备：旧批次完全未用 amount=200, remain=200，账户 balance=200
        account.setBalance(200L);
        var oldBatch = new CreditTransaction();
        oldBatch.setId(200L);
        oldBatch.setAccountId(1L);
        oldBatch.setType(CreditTransactionType.EARN);
        oldBatch.setAmount(200L);
        oldBatch.setRemain(200L);
        oldBatch.setBatchType("SUBSCRIPTION");
        when(transactionRepository.findActiveBatchesByAccountId(1L)).thenReturn(List.of(oldBatch));

        var result =
                creditService.settleSubscriptionUpgrade(
                        100L, 400L, 999L, LocalDateTime.now().plusDays(30));

        // EXPIRE 200 → balance 0；EARN 400 → balance 400；oldUsed=0 → 不写 SPEND
        assertThat(account.getBalance()).isEqualTo(400L);
        assertThat(result.oldUsed()).isZero();
        assertThat(result.spendTxId()).isNull();
    }

    @Test
    @DisplayName("Given newAmount<=0 When settleSubscriptionUpgrade Then 抛参数异常")
    void settleSubscriptionUpgrade_zeroAmount_throws() {
        assertThatThrownBy(
                        () ->
                                creditService.settleSubscriptionUpgrade(
                                        100L, 0L, 999L, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class);
    }
}
