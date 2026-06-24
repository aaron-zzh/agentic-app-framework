package com.xuejiai.aaf.module.brokerage.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageRecordStatusEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.repository.CreditGrantRuleRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.service.CreditGrantService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRecord;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRule;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageInviteCodeRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageLevelBonusRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRecordRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRuleRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 分销核心业务服务。
 *
 * <p>负责：佣金触发计算（两级分佣）、退款冲回、佣金解冻定时任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerageService {

    private final BrokerageUserRepository brokerageUserRepository;
    private final BrokerageRuleRepository brokerageRuleRepository;
    private final BrokerageLevelBonusRepository brokerageLevelBonusRepository;
    private final BrokerageRecordRepository brokerageRecordRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final BrokerageInviteCodeRepository inviteCodeRepository;
    private final SystemConfigService systemConfigService;
    private final StringRedisTemplate redisTemplate;
    private final CreditGrantService creditGrantService;
    private final CreditGrantRuleRepository creditGrantRuleRepository;
    private final NotificationService notificationService;

    /** INVITE 规则默认每人最多奖励次数（ext.maxInvites 缺失时兜底） */
    private static final int DEFAULT_MAX_INVITES = 20;

    /**
     * 触发佣金计算。
     *
     * <p>由支付成功回调、订阅成功等业务事件调用。内部查找消费者的推荐链并按规则生成流水。
     *
     * @param consumerContactId 消费者 contact_id
     * @param bizType 业务类型（ORDER/SUBSCRIBE/RECHARGE/INVITE）
     * @param bizTargetType 目标类型（PRODUCT/PLAN/PACKAGE，可为 null）
     * @param bizTargetId 目标 ID（可为 null）
     * @param bizId 业务记录 ID
     * @param title 佣金标题
     * @param paidAmount 实付金额（分）
     */
    @Transactional
    public void calculateBrokerage(
            Long consumerContactId,
            String bizType,
            String bizTargetType,
            String bizTargetId,
            String bizId,
            String title,
            Long paidAmount) {
        // 查找消费者的分销员记录
        var consumerBrokerageUser =
                brokerageUserRepository.findByContactId(consumerContactId).orElse(null);
        if (consumerBrokerageUser == null || consumerBrokerageUser.getReferrerContactId() == null) {
            return; // 消费者无推荐人，不产生佣金
        }

        // 匹配佣金规则
        var rule = matchRule(bizType, bizTargetType, bizTargetId);
        if (rule == null) {
            return; // 未命中规则，不产生佣金
        }

        // 一级佣金：直属推荐人（需有分销资格）
        var level1ContactId = consumerBrokerageUser.getReferrerContactId();
        var level1Broker = brokerageUserRepository.findByContactId(level1ContactId).orElse(null);
        if (level1Broker == null || !Boolean.TRUE.equals(level1Broker.getBrokerageEnabled())) {
            return; // 推荐人没有分销资格
        }
        createRecord(
                level1ContactId,
                consumerContactId,
                (short) 1,
                bizType,
                bizId,
                title,
                paidAmount,
                rule,
                level1ContactId);

        // 二级佣金：推荐人的推荐人（也需有分销资格）
        if (rule.getLevel2Rate().compareTo(BigDecimal.ZERO) > 0) {
            var level2ContactId = level1Broker.getReferrerContactId();
            if (level2ContactId != null) {
                var level2Broker =
                        brokerageUserRepository.findByContactId(level2ContactId).orElse(null);
                if (level2Broker != null
                        && Boolean.TRUE.equals(level2Broker.getBrokerageEnabled())) {
                    createRecord(
                            level2ContactId,
                            consumerContactId,
                            (short) 2,
                            bizType,
                            bizId,
                            title,
                            paidAmount,
                            rule,
                            level1ContactId);
                }
            }
        }
    }

    /**
     * 退款冲回佣金。
     *
     * <p>退款时调用：FROZEN 状态直接取消，VALID 状态写负数流水并扣减余额。
     *
     * @param refundBizType 原始业务类型（如 ORDER）
     * @param bizId 原始业务 ID
     */
    @Transactional
    public void cancelBrokerage(String refundBizType, String bizId) {
        var records = brokerageRecordRepository.findByBizTypeAndBizId(refundBizType, bizId);
        for (var record : records) {
            if (BrokerageRecordStatusEnum.FROZEN == record.getStatus()) {
                // 冻结中，直接取消
                record.setStatus(BrokerageRecordStatusEnum.CANCELLED);
                brokerageRecordRepository.save(record);
                // 原子减少冻结金额
                brokerageUserRepository.reduceFrozen(record.getContactId(), record.getAmount());
            } else if (BrokerageRecordStatusEnum.VALID == record.getStatus()) {
                // 已解冻，写负数冲回流水
                var refundRecord = new BrokerageRecord();
                refundRecord.setContactId(record.getContactId());
                refundRecord.setSourceContactId(record.getSourceContactId());
                refundRecord.setSourceLevel(record.getSourceLevel());
                refundRecord.setBizType(refundBizType + "_REFUND");
                refundRecord.setBizId(bizId);
                refundRecord.setTitle("退款冲回：" + record.getTitle());
                refundRecord.setAmount(-record.getAmount());
                refundRecord.setStatus(BrokerageRecordStatusEnum.VALID);
                refundRecord.setFrozenDays(0);
                refundRecord.setRuleId(record.getRuleId());
                refundRecord.setAppliedRate(record.getAppliedRate());
                refundRecord.setCalcBaseAmount(record.getCalcBaseAmount());
                brokerageRecordRepository.save(refundRecord);
                // 原子扣减可用余额
                brokerageUserRepository.reduceBalance(record.getContactId(), record.getAmount());
            }
        }
    }

    private static final String UNFREEZE_LOCK_KEY = "brokerage:unfreeze:lock";

    /**
     * 佣金解冻定时任务，每小时执行一次。
     *
     * <p>查找 unfreeze_time <= now() 的 FROZEN 记录，批量解冻并更新分销员余额。 使用 Redis 分布式锁防止多节点重复执行。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void unfreezeExpiredRecords() {
        // 分布式锁：5分钟过期，防多节点重复执行
        Boolean locked =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(UNFREEZE_LOCK_KEY, "1", java.time.Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("佣金解冻：其他节点正在执行，跳过");
            return;
        }
        try {
            doUnfreeze();
        } finally {
            redisTemplate.delete(UNFREEZE_LOCK_KEY);
        }
    }

    @Transactional
    private void doUnfreeze() {
        var now = LocalDateTime.now();
        var frozenRecords =
                brokerageRecordRepository.findByStatusAndUnfreezeTimeLessThanEqual(
                        BrokerageRecordStatusEnum.FROZEN, now);
        if (frozenRecords.isEmpty()) {
            return;
        }
        log.info("佣金解冻：共 {} 条待解冻流水", frozenRecords.size());

        var ids = frozenRecords.stream().map(BrokerageRecord::getId).toList();
        brokerageRecordRepository.batchUpdateStatus(
                ids, BrokerageRecordStatusEnum.FROZEN, BrokerageRecordStatusEnum.VALID);

        // 按分销员汇总并原子更新余额（防并发超卖）
        frozenRecords.stream()
                .collect(
                        java.util.stream.Collectors.groupingBy(
                                BrokerageRecord::getContactId,
                                java.util.stream.Collectors.summingLong(
                                        BrokerageRecord::getAmount)))
                .forEach(brokerageUserRepository::addBalanceAndReduceFrozen);
    }

    /**
     * 按配置尝试自动开通分销资格。
     *
     * @param contactId 联系人 contact_id
     * @param trigger 触发来源：REGISTER=注册 / PAID=付费套餐激活
     */
    @Transactional
    public void tryEnableBrokerage(Long contactId, String trigger) {
        String condition = systemConfigService.getString("brokerage.enabled_condition", "MANUAL");
        boolean shouldEnable =
                switch (condition) {
                    case "ALL" -> true;
                    case "PAID" -> "PAID".equals(trigger);
                    default -> false; // MANUAL，不自动开通
                };
        if (!shouldEnable) return;

        var bu =
                brokerageUserRepository
                        .findByContactId(contactId)
                        .orElseGet(
                                () -> {
                                    var newBu = new BrokerageUser();
                                    newBu.setContactId(contactId);
                                    return newBu;
                                });
        if (Boolean.TRUE.equals(bu.getBrokerageEnabled())) return; // 已开通
        bu.setBrokerageEnabled(true);
        bu.setBrokerageTime(LocalDateTime.now());
        brokerageUserRepository.save(bu);
        log.info("分销资格自动开通: contactId={}, trigger={}", contactId, trigger);
    }

    /**
     * 通过邀请码绑定推荐人。
     *
     * <p>注册时调用：解析邀请码 → 找到分销员 → 绑定，同时累加邀请码使用次数。
     *
     * @param contactId 新注册用户的 contact_id
     * @param inviteCode 邀请码（如 AAF-X8K2）或 contactId 字符串（直接绑定）
     */
    @Transactional
    public void bindReferrerByCode(Long contactId, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return;
        }
        // 先按邀请码短码查
        var invite = inviteCodeRepository.findByCode(inviteCode).orElse(null);
        if (invite != null) {
            bindReferrer(contactId, invite.getContactId());
            invite.setUsedCount(invite.getUsedCount() + 1);
            inviteCodeRepository.save(invite);
            grantInviteRewardIfPossible(invite.getContactId(), contactId, invite.getUsedCount());
            return;
        }
        // 再尝试直接解析为 contactId（兼容链接 ?ref=123 形式）
        try {
            Long referrerContactId = Long.parseLong(inviteCode);
            bindReferrer(contactId, referrerContactId);
            // 补充邀请奖励：直接用 contactId 形式也应给推荐人发放奖励
            int usedCount = brokerageUserRepository.findByReferrerContactId(referrerContactId).size();
            grantInviteRewardIfPossible(referrerContactId, contactId, usedCount);
        } catch (NumberFormatException ignored) {
            log.warn("邀请码无效，跳过绑定: code={}", inviteCode);
        }
    }

    /**
     * 在邀请码绑定成功后，按 credit_grant_rule.INVITE 给推荐人发放注册奖励积分。
     *
     * <p>规则： 1) 用 ext.maxInvites（默认 20）做发放上限——超过则不再发放； 2) bizId="INVITE_"+inviteeContactId
     * 用于幂等溯源，前端可按此查询每个被邀请人的奖励金额； 3) 找不到推荐人 user 或规则被禁用时静默跳过，不影响主绑定流程。
     *
     * <p>实名风控不在此处守门：积分是站内代币，发放无门槛；推荐人的实名校验在提现接口 (AAF-098) 处统一拦截，符合"出钱时实名"的行业共识。
     */
    private void grantInviteRewardIfPossible(
            Long referrerContactId, Long inviteeContactId, int newUsedCount) {
        try {
            int maxInvites = readMaxInvites();
            if (newUsedCount > maxInvites) {
                log.info(
                        "邀请奖励已达上限，跳过发放: referrerContactId={}, used={}, max={}",
                        referrerContactId,
                        newUsedCount,
                        maxInvites);
                return;
            }
            var referrerUser = userRepository.findByContactId(referrerContactId).orElse(null);
            if (referrerUser == null) {
                log.warn("推荐人无对应 user，跳过邀请奖励: contactId={}", referrerContactId);
                return;
            }
            String bizId = "INVITE_" + inviteeContactId;
            long granted = creditGrantService.grant(referrerUser.getId(), "INVITE", bizId);
            if (granted > 0) {
                notificationService.sendSystemNotification(
                        referrerUser.getId(),
                        "邀请奖励已到账",
                        "好友通过你的邀请链接完成注册，+" + granted + " 积分已存入账户");
            }
        } catch (Exception e) {
            // 不影响绑定推荐人主流程
            log.warn(
                    "发放邀请注册奖励失败: referrerContactId={}, inviteeContactId={}",
                    referrerContactId,
                    inviteeContactId,
                    e);
        }
    }

    /** 读取 credit_grant_rule.INVITE.ext.maxInvites，缺失/异常时兜底默认值 */
    private int readMaxInvites() {
        var rule = creditGrantRuleRepository.findByCodeAndStatus("INVITE", "ENABLED").orElse(null);
        if (rule == null || rule.getExt() == null || rule.getExt().isBlank()) {
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
     * 绑定推荐人。
     *
     * <p>首次调用时绑定，已绑定后不可更改（与芋道设计一致）。
     *
     * @param contactId 被推荐人 contact_id
     * @param referrerContactId 推荐人 contact_id
     */
    @Transactional
    public void bindReferrer(Long contactId, Long referrerContactId) {
        // 防止自推自
        if (contactId.equals(referrerContactId)) {
            return;
        }
        var brokerageUser =
                brokerageUserRepository
                        .findByContactId(contactId)
                        .orElseGet(
                                () -> {
                                    var bu = new BrokerageUser();
                                    bu.setContactId(contactId);
                                    return bu;
                                });
        // 已绑定则不覆盖
        if (brokerageUser.getReferrerContactId() != null) {
            return;
        }
        brokerageUser.setReferrerContactId(referrerContactId);
        brokerageUser.setReferrerBindTime(LocalDateTime.now());
        brokerageUserRepository.save(brokerageUser);
    }

    // ========== 私有方法 ==========

    /** 按精确度降序匹配规则：biz_target_id > biz_target_type > biz_type 全匹配。 */
    private BrokerageRule matchRule(String bizType, String bizTargetType, String bizTargetId) {
        var rules =
                brokerageRuleRepository.findByBizTypeAndStatusOrderByPriorityAsc(
                        bizType, "ENABLED");
        // 精确匹配 targetType + targetId
        if (bizTargetType != null && bizTargetId != null) {
            var exact =
                    rules.stream()
                            .filter(
                                    r ->
                                            bizTargetType.equals(r.getBizTargetType())
                                                    && bizTargetId.equals(r.getBizTargetId()))
                            .findFirst();
            if (exact.isPresent()) return exact.get();
        }
        // 匹配 targetType（id为空）
        if (bizTargetType != null) {
            var byType =
                    rules.stream()
                            .filter(
                                    r ->
                                            bizTargetType.equals(r.getBizTargetType())
                                                    && r.getBizTargetId() == null)
                            .findFirst();
            if (byType.isPresent()) return byType.get();
        }
        // 兜底：全匹配（targetType 和 targetId 均为空）
        return rules.stream()
                .filter(r -> r.getBizTargetType() == null && r.getBizTargetId() == null)
                .findFirst()
                .orElse(null);
    }

    /** 计算佣金金额并创建流水。 */
    private void createRecord(
            Long recipientContactId,
            Long consumerContactId,
            short sourceLevel,
            String bizType,
            String bizId,
            String title,
            Long paidAmount,
            BrokerageRule rule,
            Long brokerContactId) {
        // 查询分销员订阅套餐，是否有会员等级加成
        var appliedRate = resolveAppliedRate(rule, brokerContactId, sourceLevel);
        if (appliedRate.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        long amount;
        if ("FIXED".equals(rule.getCalcBase()) && rule.getFixedAmount() != null) {
            amount = rule.getFixedAmount();
        } else {
            amount =
                    BigDecimal.valueOf(paidAmount)
                            .multiply(appliedRate)
                            .setScale(0, RoundingMode.DOWN)
                            .longValue();
        }
        if (amount <= 0) {
            return;
        }

        var unfreezeTime =
                rule.getFrozenDays() > 0
                        ? LocalDateTime.now().plusDays(rule.getFrozenDays())
                        : LocalDateTime.now();

        var record = new BrokerageRecord();
        record.setContactId(recipientContactId);
        record.setSourceContactId(consumerContactId);
        record.setSourceLevel(sourceLevel);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setTitle(title);
        record.setAmount(amount);
        record.setStatus(
                rule.getFrozenDays() > 0
                        ? BrokerageRecordStatusEnum.FROZEN
                        : BrokerageRecordStatusEnum.VALID);
        record.setFrozenDays(rule.getFrozenDays());
        record.setUnfreezeTime(unfreezeTime);
        record.setRuleId(rule.getId());
        record.setAppliedRate(appliedRate);
        record.setCalcBaseAmount(paidAmount);
        brokerageRecordRepository.save(record);

        // 原子更新分销员余额（防并发超卖）
        if (rule.getFrozenDays() > 0) {
            brokerageUserRepository.addFrozen(recipientContactId, amount);
        } else {
            brokerageUserRepository.addBalance(recipientContactId, amount);
        }
    }

    /**
     * 解析实际佣金比例：优先查会员等级加成，未命中则用规则基础比例。
     *
     * <p>链路：brokerContactId → sys_user.contact_id → billing_subscription（活跃套餐） →
     * brokerage_level_bonus（rule_id + plan_id）→ 命中则覆盖，否则用基础比例。
     */
    private BigDecimal resolveAppliedRate(BrokerageRule rule, Long brokerContactId, short level) {
        var baseRate = level == 1 ? rule.getLevel1Rate() : rule.getLevel2Rate();
        // 通过 contact_id 找到系统用户
        var user = userRepository.findByContactId(brokerContactId).orElse(null);
        if (user == null) {
            return baseRate;
        }
        // 查找活跃订阅套餐
        var subscription =
                subscriptionRepository.findByUserIdAndStatus(user.getId(), "ACTIVE").orElse(null);
        if (subscription == null) {
            return baseRate;
        }
        // 查找会员等级加成
        return brokerageLevelBonusRepository
                .findByRuleIdAndPlanId(rule.getId(), subscription.getPlanId())
                .map(bonus -> level == 1 ? bonus.getLevel1Rate() : bonus.getLevel2Rate())
                .orElse(baseRate);
    }
}
