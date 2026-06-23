package com.xuejiai.aaf.module.brokerage.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.sys.ContactSourceEnum;
import com.xuejiai.aaf.common.enums.sys.ContactStatusEnum;
import com.xuejiai.aaf.common.enums.sys.ContactTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.module.billing.domain.CreditGrantRule;
import com.xuejiai.aaf.module.billing.repository.CreditGrantRuleRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageInviteCode;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRule;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRuleRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageWithdrawRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodeMeVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteRewardConfigVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInvitedUserVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawVO;
import com.xuejiai.aaf.module.brokerage.vo.WithdrawApplyDTO;
import com.xuejiai.aaf.module.system.contact.domain.Contact;
import com.xuejiai.aaf.module.system.contact.repository.ContactRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 当前用户视角的邀请奖励服务（user-facing）。
 *
 * <p>聚合三种能力：
 *
 * <ul>
 *   <li>取/生成我的邀请码
 *   <li>列表查询我邀请的好友（含会员状态、奖励积分）
 *   <li>读取奖励规则配置（前端展示）
 * </ul>
 *
 * <p>所有方法以 {@code currentUserId} 为入参，由 Controller 从 OperatorContext 注入。
 *
 * @author AaronZZH &amp; Kiro
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BrokerageMeService {

    private static final int DEFAULT_MAX_INVITES = 20;

    private final UserRepository userRepository;
    private final BrokerageInviteCodeService inviteCodeService;
    private final BrokerageUserRepository brokerageUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CreditGrantRuleRepository creditGrantRuleRepository;
    private final BrokerageRuleRepository brokerageRuleRepository;
    private final BrokerageWithdrawRepository brokerageWithdrawRepository;
    private final NotificationService notificationService;
    private final MessageService messageService;
    private final ContactRepository contactRepository;

    @Autowired
    public BrokerageMeService(
            UserRepository userRepository,
            BrokerageInviteCodeService inviteCodeService,
            BrokerageUserRepository brokerageUserRepository,
            SubscriptionRepository subscriptionRepository,
            CreditGrantRuleRepository creditGrantRuleRepository,
            BrokerageRuleRepository brokerageRuleRepository,
            BrokerageWithdrawRepository brokerageWithdrawRepository,
            NotificationService notificationService,
            MessageService messageService,
            ContactRepository contactRepository) {
        this.userRepository = userRepository;
        this.inviteCodeService = inviteCodeService;
        this.brokerageUserRepository = brokerageUserRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.creditGrantRuleRepository = creditGrantRuleRepository;
        this.brokerageRuleRepository = brokerageRuleRepository;
        this.brokerageWithdrawRepository = brokerageWithdrawRepository;
        this.notificationService = notificationService;
        this.messageService = messageService;
        this.contactRepository = contactRepository;
    }

    /** 取/生成我的邀请码（一人一码，幂等）。 */
    @Transactional
    public BrokerageInviteCodeMeVO getOrCreateMyInviteCode(Long currentUserId, String channel) {
        Long contactId = requireContactId(currentUserId);
        BrokerageInviteCode invite =
                inviteCodeService.getOrCreate(contactId, normalizeChannel(channel));
        Integer maxInvites = readMaxInvitesFromInviteRule();
        return new BrokerageInviteCodeMeVO(
                invite.getCode(), invite.getChannel(), invite.getUsedCount(), maxInvites);
    }

    /**
     * 我邀请的好友列表。
     *
     * <p>按 referrer_bind_time 倒序分页；每条记录附带：昵称、头像、注册时间（=绑定时间）、是否会员、获得的奖励积分。
     *
     * <p>"获得的奖励积分"取自 {@code credit_grant_rule.INVITE.amount}：按 referrer_bind_time 升序的下标 i， i &lt;
     * maxInvites 视为已发放（i 从 0 起算），否则计为 0。该口径是简化实现，避免 N+1 查询 credit_transaction； 长期可在 framework 层
     * CreditTransactionRepository 增加 findByAccountIdAndBizId 后改为精确反查。
     *
     * <p>使用写事务（覆盖类级 {@code readOnly = true}）：因为内部调用 {@code requireContactId} 可能为缺联系人的 用户兜底创建
     * Contact（管理员场景）。
     */
    @Transactional
    public PageResult<BrokerageInvitedUserVO> listMyInvitedUsers(
            Long currentUserId, int page, int size) {
        Long contactId = requireContactId(currentUserId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<BrokerageUser> brokeragePage =
                brokerageUserRepository.findByReferrerContactIdOrderByReferrerBindTimeDesc(
                        contactId, pageable);
        if (brokeragePage.isEmpty()) {
            return new PageResult<>(
                    List.of(),
                    brokeragePage.getTotalElements(),
                    pageable.getPageNumber() + 1,
                    pageable.getPageSize(),
                    List.of(),
                    null,
                    null,
                    brokeragePage.hasNext());
        }

        // 一次性预取奖励金额、maxInvites、被邀请人 user 信息、订阅状态，避免 N+1
        long rewardAmount = readInviteRewardAmount();
        int maxInvites = readMaxInvitesFromInviteRule();

        // 获取所有被邀请人（按时间升序）的 contactId，用于按"序数"判断是否在奖励额度内
        List<BrokerageUser> ascending =
                brokerageUserRepository.findByReferrerContactId(contactId).stream()
                        .filter(b -> b.getReferrerBindTime() != null)
                        .sorted(java.util.Comparator.comparing(BrokerageUser::getReferrerBindTime))
                        .toList();
        Map<Long, Integer> contactIdToOrdinal = new HashMap<>();
        for (int i = 0; i < ascending.size(); i++) {
            contactIdToOrdinal.put(ascending.get(i).getContactId(), i);
        }

        List<Long> contactIds = brokeragePage.stream().map(BrokerageUser::getContactId).toList();
        List<User> users =
                contactIds.isEmpty()
                        ? Collections.emptyList()
                        : userRepository.findByContactIdIn(contactIds);
        Map<Long, User> contactIdToUser = new HashMap<>();
        for (User u : users) {
            if (u.getContactId() != null) {
                contactIdToUser.put(u.getContactId(), u);
            }
        }

        List<BrokerageInvitedUserVO> items =
                brokeragePage.stream()
                        .map(
                                bu -> {
                                    User u = contactIdToUser.get(bu.getContactId());
                                    boolean isMember = false;
                                    if (u != null) {
                                        isMember =
                                                subscriptionRepository
                                                        .findByUserIdAndStatus(u.getId(), "ACTIVE")
                                                        .isPresent();
                                    }
                                    Integer ordinal = contactIdToOrdinal.get(bu.getContactId());
                                    long credits =
                                            ordinal != null
                                                            && ordinal < maxInvites
                                                            && rewardAmount > 0
                                                    ? rewardAmount
                                                    : 0L;
                                    return new BrokerageInvitedUserVO(
                                            bu.getContactId(),
                                            u != null ? u.getNickname() : null,
                                            u != null ? u.getAvatar() : null,
                                            bu.getReferrerBindTime(),
                                            isMember,
                                            credits);
                                })
                        .toList();
        return new PageResult<>(
                items,
                brokeragePage.getTotalElements(),
                pageable.getPageNumber() + 1,
                pageable.getPageSize(),
                List.of(),
                null,
                null,
                brokeragePage.hasNext());
    }

    /**
     * 申请提现。
     *
     * <p>规则： 1) 推荐人必须已绑定手机号（KYC 实名，提现侧守门）； 2) 余额必须充足； 3) 创建 PENDING 申请后冻结对应金额（balance - amount,
     * frozen + amount），防重复申请。
     */
    @Transactional
    public BrokerageWithdrawVO applyWithdraw(Long currentUserId, WithdrawApplyDTO dto) {
        User user =
                userRepository
                        .findById(currentUserId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));

        // 实名校验：提现侧守门
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "请先绑定手机号后再申请提现");
        }
        if (user.getContactId() == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "账号未关联联系人，无法提现");
        }

        Long contactId = user.getContactId();
        var bu =
                brokerageUserRepository
                        .findByContactId(contactId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.BAD_REQUEST, "暂无可用佣金"));

        if (bu.getBalance() < dto.amount()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "可用余额不足，当前余额: " + bu.getBalance() + " 分");
        }

        // 冻结金额（balance - amount，frozen + amount），防重复申请
        brokerageUserRepository.reduceBalance(contactId, dto.amount());
        brokerageUserRepository.addFrozen(contactId, dto.amount());

        var withdraw = new BrokerageWithdraw();
        withdraw.setContactId(contactId);
        withdraw.setAmount(dto.amount());
        withdraw.setType(dto.type());
        withdraw.setAccountName(dto.accountName());
        withdraw.setAccountNo(dto.accountNo());
        brokerageWithdrawRepository.save(withdraw);

        // 发站内消息：申请已提交（与审核结果通知共用 type/entity，前端可按 entity 关联汇总）
        notificationService.send(
                currentUserId,
                "BROKERAGE_WITHDRAW",
                "提现申请已提交",
                String.format("您的提现申请（¥%.2f）已提交，预计 1-3 个工作日内审核完成。", dto.amount() / 100.0),
                "/settings/withdraw",
                "BROKERAGE_WITHDRAW",
                withdraw.getId());

        // 推送钉钉运营群（待审核任务），静默失败不影响主流程
        notifyDingtalkNewWithdraw(withdraw, user);

        return toWithdrawVO(withdraw);
    }

    /** 我的提现历史（分页，按创建时间倒序）。 */
    public PageResult<BrokerageWithdrawVO> listMyWithdraws(Long currentUserId, int page, int size) {
        User user =
                userRepository
                        .findById(currentUserId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        if (user.getContactId() == null) {
            return new PageResult<>(java.util.List.of(), 0);
        }
        var pageable =
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 50),
                        org.springframework.data.domain.Sort.by("createTime").descending());
        var pageResult = brokerageWithdrawRepository.findByContactId(user.getContactId(), pageable);
        return new PageResult<>(
                pageResult.stream().map(this::toWithdrawVO).toList(),
                pageResult.getTotalElements(),
                pageable.getPageNumber() + 1,
                pageable.getPageSize(),
                java.util.List.of(),
                null,
                null,
                pageResult.hasNext());
    }

    /** 当前可用佣金余额 */
    public long getMyBalance(Long currentUserId) {
        User user =
                userRepository
                        .findById(currentUserId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        if (user.getContactId() == null) return 0L;
        return brokerageUserRepository
                .findByContactId(user.getContactId())
                .map(BrokerageUser::getBalance)
                .orElse(0L);
    }

    // ==================== 私有方法（提现） ====================

    private BrokerageWithdrawVO toWithdrawVO(BrokerageWithdraw w) {
        return new BrokerageWithdrawVO(
                w.getId(),
                w.getContactId(),
                w.getAmount(),
                w.getFee(),
                w.getType(),
                w.getAccountName(),
                w.getAccountNo(),
                w.getQrCodeUrl(),
                w.getStatus(),
                w.getAuditReason(),
                w.getAuditTime(),
                w.getPayTransferId(),
                w.getTransferTime(),
                w.getCreateTime(),
                w.getUpdateTime());
    }

    /**
     * 推送钉钉运营群「新提现申请待审核」（静默失败，不影响申请流程）。
     *
     * <p>内容包含申请人、金额、收款方式与账号；运营据此前往后台「提现审核」处理。
     */
    private void notifyDingtalkNewWithdraw(BrokerageWithdraw withdraw, User user) {
        try {
            String applicant =
                    user.getUsername() != null && !user.getUsername().isBlank()
                            ? user.getUsername()
                            : ("user#" + user.getId());
            String content =
                    String.format(
                            "**新提现申请待审核** %n%n> 申请人：%s  %n> 金额：¥%.2f  %n> 类型：%s"
                                    + "  %n> 收款人：%s  %n> 收款账号：%s",
                            applicant,
                            withdraw.getAmount() / 100.0,
                            withdraw.getType() != null ? withdraw.getType() : "-",
                            withdraw.getAccountName() != null ? withdraw.getAccountName() : "-",
                            withdraw.getAccountNo() != null ? withdraw.getAccountNo() : "-");
            messageService.send(
                    MessageRequest.direct(
                            MessageChannel.DINGTALK, "新提现申请", content, List.of("all")));
        } catch (Exception e) {
            log.warn("钉钉新提现申请通知失败，不影响申请流程: withdrawId={}", withdraw.getId(), e);
        }
    }

    /** 邀请奖励配置（前端展示用）。 */
    public BrokerageInviteRewardConfigVO getRewardConfig() {
        return new BrokerageInviteRewardConfigVO(buildRegisterReward(), buildSubscribeReward());
    }

    // ==================== 私有方法（邀请奖励）====================

    private BrokerageInviteRewardConfigVO.RegisterReward buildRegisterReward() {
        var rule = creditGrantRuleRepository.findByCodeAndStatus("INVITE", "ENABLED").orElse(null);
        if (rule == null) {
            return new BrokerageInviteRewardConfigVO.RegisterReward(false, 0L, 0, 0);
        }
        return new BrokerageInviteRewardConfigVO.RegisterReward(
                true, rule.getAmount(), rule.getExpireDays(), readMaxInvites(rule));
    }

    private BrokerageInviteRewardConfigVO.SubscribeReward buildSubscribeReward() {
        // 取 SUBSCRIBE 类型的兜底规则（biz_target_type/biz_target_id 均为 NULL）
        BrokerageRule rule =
                brokerageRuleRepository
                        .findByBizTypeAndStatusOrderByPriorityAsc("SUBSCRIBE", "ENABLED")
                        .stream()
                        .filter(r -> r.getBizTargetType() == null && r.getBizTargetId() == null)
                        .findFirst()
                        .orElse(null);
        if (rule == null) {
            return new BrokerageInviteRewardConfigVO.SubscribeReward(false, BigDecimal.ZERO, 0);
        }
        return new BrokerageInviteRewardConfigVO.SubscribeReward(
                true, rule.getLevel1Rate(), rule.getFrozenDays());
    }

    private long readInviteRewardAmount() {
        return creditGrantRuleRepository
                .findByCodeAndStatus("INVITE", "ENABLED")
                .map(CreditGrantRule::getAmount)
                .orElse(0L);
    }

    private int readMaxInvitesFromInviteRule() {
        return creditGrantRuleRepository
                .findByCodeAndStatus("INVITE", "ENABLED")
                .map(this::readMaxInvites)
                .orElse(DEFAULT_MAX_INVITES);
    }

    private int readMaxInvites(CreditGrantRule rule) {
        if (rule.getExt() == null || rule.getExt().isBlank()) {
            return DEFAULT_MAX_INVITES;
        }
        try {
            JsonNode node = JsonUtils.readTree(rule.getExt());
            JsonNode maxNode = node.get("maxInvites");
            return maxNode != null && maxNode.canConvertToInt()
                    ? maxNode.asInt()
                    : DEFAULT_MAX_INVITES;
        } catch (Exception e) {
            log.warn(
                    "解析 credit_grant_rule.INVITE.ext 失败，使用默认 maxInvites={}",
                    DEFAULT_MAX_INVITES,
                    e);
            return DEFAULT_MAX_INVITES;
        }
    }

    /**
     * 取当前用户的 contactId；如果用户尚未绑定联系人则懒创建。
     *
     * <p>普通用户在注册流程中（{@code AuthService.createContactForUser}）已自动创建 Contact， 因此通常 {@code
     * user.contactId} 不会为 null。但管理员账号可能由初始化 SQL / 后台直接创建， 不走注册流程，{@code contactId} 为
     * NULL。本方法在此场景下为其兜底创建 Contact， 保证邀请码 / 邀请历史等接口对所有已登录用户可用。
     *
     * <p>该方法使用调用方所在事务（{@code @Transactional}），创建失败会回滚整个调用。
     */
    private Long requireContactId(Long currentUserId) {
        User user =
                userRepository
                        .findById(currentUserId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        if (user.getContactId() != null) {
            return user.getContactId();
        }
        return ensureContactForUser(user);
    }

    /**
     * 为没有 Contact 的用户兜底创建一个 PERSON 联系人，并写回 user.contactId。
     *
     * <p>与 {@code AuthService.createContactForUser} 行为一致，是其在管理员等非注册路径下的镜像实现； 未来如需要复用到第三方场景，可统一抽到
     * ContactService 公共方法。
     */
    private Long ensureContactForUser(User user) {
        var contact = new Contact();
        contact.setName(user.getNickname() != null ? user.getNickname() : user.getUsername());
        contact.setEmail(user.getEmail());
        contact.setPhone(user.getPhone());
        contact.setType(ContactTypeEnum.PERSON);
        contact.setSource(ContactSourceEnum.REGISTER);
        contact.setStatus(ContactStatusEnum.ACTIVE);
        contactRepository.save(contact);
        user.setContactId(contact.getId());
        userRepository.save(user);
        log.info(
                "为用户兜底创建 Contact：userId={}, contactId={}（首次访问邀请相关接口触发）",
                user.getId(),
                contact.getId());
        return contact.getId();
    }

    private static String normalizeChannel(String channel) {
        return channel == null || channel.isBlank() ? null : channel.trim();
    }
}
