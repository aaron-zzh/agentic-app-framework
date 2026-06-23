package com.xuejiai.aaf.module.brokerage.service;

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

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.module.billing.repository.CreditGrantRuleRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRuleRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageWithdrawRepository;
import com.xuejiai.aaf.module.brokerage.vo.WithdrawApplyDTO;
import com.xuejiai.aaf.module.system.contact.repository.ContactRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/**
 * 提现申请实名校验单元测试。
 *
 * @author AaronZZH &amp; Kiro
 */
class WithdrawApplyTest extends BaseMockitoUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private BrokerageInviteCodeService inviteCodeService;
    @Mock private BrokerageUserRepository brokerageUserRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CreditGrantRuleRepository creditGrantRuleRepository;
    @Mock private BrokerageRuleRepository brokerageRuleRepository;
    @Mock private BrokerageWithdrawRepository brokerageWithdrawRepository;
    @Mock private NotificationService notificationService;
    @Mock private MessageService messageService;
    @Mock private ContactRepository contactRepository;

    @InjectMocks private BrokerageMeService brokerageMeService;

    @Test
    @DisplayName("未绑手机 → 抛出 BAD_REQUEST，拒绝提现")
    void applyWithdraw_rejectWhenNoPhone() {
        User user = newUser(1L, 100L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(
                        () ->
                                brokerageMeService.applyWithdraw(
                                        1L,
                                        new WithdrawApplyDTO(
                                                500L,
                                                BrokerageWithdrawTypeEnum.ALIPAY,
                                                "张三",
                                                "13800138000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机");

        verify(brokerageWithdrawRepository, never()).save(any());
    }

    @Test
    @DisplayName("已绑手机但余额不足 → 抛出 BAD_REQUEST")
    void applyWithdraw_rejectWhenBalanceInsufficient() {
        User user = newUser(1L, 100L, "13800138000");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        BrokerageUser bu = new BrokerageUser();
        bu.setContactId(100L);
        bu.setBalance(200L); // 只有 2 元
        when(brokerageUserRepository.findByContactId(100L)).thenReturn(Optional.of(bu));

        assertThatThrownBy(
                        () ->
                                brokerageMeService.applyWithdraw(
                                        1L,
                                        new WithdrawApplyDTO(
                                                500L,
                                                BrokerageWithdrawTypeEnum.ALIPAY,
                                                "张三",
                                                "13800138000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额不足");

        verify(brokerageWithdrawRepository, never()).save(any());
    }

    @Test
    @DisplayName("已绑手机且余额充足 → 创建提现申请 + 冻结余额")
    void applyWithdraw_successWhenPhoneAndBalanceSufficient() {
        User user = newUser(1L, 100L, "13800138000");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        BrokerageUser bu = new BrokerageUser();
        bu.setContactId(100L);
        bu.setBalance(1000L); // 10 元
        when(brokerageUserRepository.findByContactId(100L)).thenReturn(Optional.of(bu));

        BrokerageWithdraw saved = new BrokerageWithdraw();
        org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 99L);
        saved.setContactId(100L);
        saved.setAmount(500L);
        saved.setType(BrokerageWithdrawTypeEnum.ALIPAY);
        saved.setAccountName("张三");
        saved.setAccountNo("13800138000");
        when(brokerageWithdrawRepository.save(any())).thenReturn(saved);

        var result =
                brokerageMeService.applyWithdraw(
                        1L,
                        new WithdrawApplyDTO(
                                500L, BrokerageWithdrawTypeEnum.ALIPAY, "张三", "13800138000"));

        // 验证余额扣减 + 冻结
        verify(brokerageUserRepository).reduceBalance(100L, 500L);
        verify(brokerageUserRepository).addFrozen(100L, 500L);
        verify(brokerageWithdrawRepository).save(any());
        // 返回 VO 不为 null
        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
        org.assertj.core.api.Assertions.assertThat(result.amount()).isEqualTo(500L);
    }

    // ==================== 辅助 ====================

    private User newUser(Long id, Long contactId, String phone) {
        User u = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
        u.setContactId(contactId);
        u.setPhone(phone);
        return u;
    }
}
