package com.xuejiai.aaf.module.brokerage.vo;

import java.math.BigDecimal;

/**
 * 邀请奖励配置 VO（前端展示用）。
 *
 * <p>左侧"邀请注册奖励"取自 credit_grant_rule.INVITE，右侧"购买会员分销奖励"取自 brokerage_rule.SUBSCRIBE 的兜底规则。 任一项
 * disabled / 不存在则对应 enabled=false，前端隐藏该卡片。
 *
 * @author AaronZZH &amp; Kiro
 */
public record BrokerageInviteRewardConfigVO(
        RegisterReward registerReward, SubscribeReward subscribeReward) {

    /** 邀请注册奖励（左卡片） */
    public record RegisterReward(
            boolean enabled, Long creditAmount, Integer expireDays, Integer maxInvites) {}

    /** 购买会员分销奖励（右卡片） */
    public record SubscribeReward(boolean enabled, BigDecimal level1Rate, Integer frozenDays) {}
}
