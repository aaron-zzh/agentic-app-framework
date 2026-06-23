package com.xuejiai.aaf.module.brokerage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.billing.domain.CreditGrantRule;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.repository.CreditGrantRuleRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageInviteCode;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRule;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRuleRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodeMeVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteRewardConfigVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInvitedUserVO;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/**
 * BrokerageMeService 单元测试——覆盖三个 user-facing 能力。
 *
 * @author AaronZZH &amp; Kiro
 */
class BrokerageMeServiceTest extends BaseMockitoUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private BrokerageInviteCodeService inviteCodeService;
    @Mock private BrokerageUserRepository brokerageUserRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CreditGrantRuleRepository creditGrantRuleRepository;
    @Mock private BrokerageRuleRepository brokerageRuleRepository;

    @Mock
    private com.xuejiai.aaf.module.system.contact.repository.ContactRepository contactRepository;

    @InjectMocks private BrokerageMeService brokerageMeService;

    @Test
    @DisplayName("取/生成我的邀请码：返回短码 + maxInvites")
    void getOrCreateMyInviteCode_normal() {
        Long userId = 100L;
        Long contactId = 200L;
        User user = newUser(userId, contactId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        BrokerageInviteCode invite = new BrokerageInviteCode();
        invite.setCode("AAF-X8K2A");
        invite.setContactId(contactId);
        invite.setUsedCount(3);
        when(inviteCodeService.getOrCreate(eq(contactId), any())).thenReturn(invite);

        when(creditGrantRuleRepository.findByCodeAndStatus("INVITE", "ENABLED"))
                .thenReturn(Optional.of(newInviteRule(500L, 30, "{\"maxInvites\":20}")));

        BrokerageInviteCodeMeVO vo = brokerageMeService.getOrCreateMyInviteCode(userId, null);
        assertThat(vo.code()).isEqualTo("AAF-X8K2A");
        assertThat(vo.usedCount()).isEqualTo(3);
        assertThat(vo.maxInvites()).isEqualTo(20);
    }

    @Test
    @DisplayName("用户未关联 contact 时兜底创建（管理员场景）")
    void getOrCreateMyInviteCode_noContact_lazyCreate() {
        Long userId = 101L;
        User user = newUser(userId, null);
        user.setNickname("Admin");
        user.setEmail("admin@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 兜底创建 Contact 时模拟 save 行为：分配 id 并回写
        when(contactRepository.save(
                        any(com.xuejiai.aaf.module.system.contact.domain.Contact.class)))
                .thenAnswer(
                        inv -> {
                            var c =
                                    inv.getArgument(
                                            0,
                                            com.xuejiai.aaf.module.system.contact.domain.Contact
                                                    .class);
                            org.springframework.test.util.ReflectionTestUtils.setField(
                                    c, "id", 999L);
                            return c;
                        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BrokerageInviteCode invite = new BrokerageInviteCode();
        invite.setCode("AAF-NEW01");
        invite.setContactId(999L);
        invite.setUsedCount(0);
        when(inviteCodeService.getOrCreate(eq(999L), any())).thenReturn(invite);

        when(creditGrantRuleRepository.findByCodeAndStatus("INVITE", "ENABLED"))
                .thenReturn(Optional.of(newInviteRule(500L, 30, "{\"maxInvites\":20}")));

        BrokerageInviteCodeMeVO vo = brokerageMeService.getOrCreateMyInviteCode(userId, null);
        assertThat(vo.code()).isEqualTo("AAF-NEW01");
        assertThat(user.getContactId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("奖励配置：INVITE + SUBSCRIBE 兜底规则均启用")
    void getRewardConfig_bothEnabled() {
        when(creditGrantRuleRepository.findByCodeAndStatus("INVITE", "ENABLED"))
                .thenReturn(Optional.of(newInviteRule(500L, 30, "{\"maxInvites\":20}")));

        BrokerageRule subscribeRule = new BrokerageRule();
        subscribeRule.setBizType("SUBSCRIBE");
        subscribeRule.setLevel1Rate(new BigDecimal("0.0500"));
        subscribeRule.setLevel2Rate(new BigDecimal("0.0100"));
        subscribeRule.setFrozenDays(30);
        subscribeRule.setStatus("ENABLED");
        when(brokerageRuleRepository.findByBizTypeAndStatusOrderByPriorityAsc(
                        "SUBSCRIBE", "ENABLED"))
                .thenReturn(List.of(subscribeRule));

        BrokerageInviteRewardConfigVO vo = brokerageMeService.getRewardConfig();
        assertThat(vo.registerReward().enabled()).isTrue();
        assertThat(vo.registerReward().creditAmount()).isEqualTo(500L);
        assertThat(vo.registerReward().expireDays()).isEqualTo(30);
        assertThat(vo.registerReward().maxInvites()).isEqualTo(20);
        assertThat(vo.subscribeReward().enabled()).isTrue();
        assertThat(vo.subscribeReward().level1Rate()).isEqualByComparingTo("0.0500");
        assertThat(vo.subscribeReward().frozenDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("奖励配置：INVITE/SUBSCRIBE 缺失时返回 disabled")
    void getRewardConfig_disabled() {
        when(creditGrantRuleRepository.findByCodeAndStatus("INVITE", "ENABLED"))
                .thenReturn(Optional.empty());
        when(brokerageRuleRepository.findByBizTypeAndStatusOrderByPriorityAsc(
                        "SUBSCRIBE", "ENABLED"))
                .thenReturn(List.of());
        BrokerageInviteRewardConfigVO vo = brokerageMeService.getRewardConfig();
        assertThat(vo.registerReward().enabled()).isFalse();
        assertThat(vo.subscribeReward().enabled()).isFalse();
    }

    @Test
    @DisplayName("我邀请的好友列表：序数 < maxInvites 计奖励金额，超额计 0")
    void listMyInvitedUsers_creditByOrdinal() {
        Long userId = 100L;
        Long contactId = 200L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(newUser(userId, contactId)));

        when(creditGrantRuleRepository.findByCodeAndStatus("INVITE", "ENABLED"))
                .thenReturn(Optional.of(newInviteRule(500L, 30, "{\"maxInvites\":2}")));

        // 三个被邀请人：A 最早绑定，B 次之，C 最晚——按 maxInvites=2 应只有 A、B 拿奖励
        BrokerageUser a = newBrokerageUser(1L, 1001L, contactId, LocalDateTime.now().minusDays(3));
        BrokerageUser b = newBrokerageUser(2L, 1002L, contactId, LocalDateTime.now().minusDays(2));
        BrokerageUser c = newBrokerageUser(3L, 1003L, contactId, LocalDateTime.now().minusDays(1));

        // 全量按时间升序（用于 ordinal 计算）
        when(brokerageUserRepository.findByReferrerContactId(contactId))
                .thenReturn(List.of(a, b, c));

        // 分页结果（按时间降序：C, B, A）
        Page<BrokerageUser> page = new PageImpl<>(List.of(c, b, a), Pageable.ofSize(20), 3);
        when(brokerageUserRepository.findByReferrerContactIdOrderByReferrerBindTimeDesc(
                        eq(contactId), any(Pageable.class)))
                .thenReturn(page);

        // 对应的 user 信息
        User uA = newUser(2001L, 1001L);
        uA.setNickname("Alice");
        uA.setAvatar("a.png");
        User uB = newUser(2002L, 1002L);
        uB.setNickname("Bob");
        User uC = newUser(2003L, 1003L);
        uC.setNickname("Cathy");
        when(userRepository.findByContactIdIn(any())).thenReturn(List.of(uA, uB, uC));

        // 默认所有人都不是会员
        lenient()
                .when(subscriptionRepository.findByUserIdAndStatus(any(Long.class), anyString()))
                .thenReturn(Optional.empty());
        // B 是会员
        when(subscriptionRepository.findByUserIdAndStatus(uB.getId(), "ACTIVE"))
                .thenReturn(Optional.of(new Subscription()));

        PageResult<BrokerageInvitedUserVO> result =
                brokerageMeService.listMyInvitedUsers(userId, 0, 20);
        List<BrokerageInvitedUserVO> content = result.list();
        assertThat(content).hasSize(3);
        // 顺序：C, B, A
        assertThat(content.get(0).contactId()).isEqualTo(1003L); // C 第三个，超额
        assertThat(content.get(0).rewardCredits()).isZero();
        assertThat(content.get(0).isMember()).isFalse();
        assertThat(content.get(1).contactId()).isEqualTo(1002L); // B 第二个，发奖励
        assertThat(content.get(1).rewardCredits()).isEqualTo(500L);
        assertThat(content.get(1).isMember()).isTrue();
        assertThat(content.get(2).contactId()).isEqualTo(1001L); // A 第一个，发奖励
        assertThat(content.get(2).rewardCredits()).isEqualTo(500L);
        assertThat(content.get(2).nickname()).isEqualTo("Alice");
    }

    // ==================== 辅助 ====================

    private User newUser(Long id, Long contactId) {
        User u = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
        u.setContactId(contactId);
        return u;
    }

    private CreditGrantRule newInviteRule(Long amount, Integer expireDays, String ext) {
        CreditGrantRule r = new CreditGrantRule();
        r.setCode("INVITE");
        r.setName("邀请注册奖励");
        r.setAmount(amount);
        r.setExpireDays(expireDays);
        r.setStatus("ENABLED");
        r.setTrigger("EVENT");
        r.setExt(ext);
        return r;
    }

    private BrokerageUser newBrokerageUser(
            Long id, Long contactId, Long referrerContactId, LocalDateTime bindTime) {
        BrokerageUser bu = new BrokerageUser();
        org.springframework.test.util.ReflectionTestUtils.setField(bu, "id", id);
        bu.setContactId(contactId);
        bu.setReferrerContactId(referrerContactId);
        bu.setReferrerBindTime(bindTime);
        return bu;
    }
}
