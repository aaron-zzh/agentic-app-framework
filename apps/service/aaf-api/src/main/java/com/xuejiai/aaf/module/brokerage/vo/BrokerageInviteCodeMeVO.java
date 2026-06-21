package com.xuejiai.aaf.module.brokerage.vo;

/**
 * 当前用户的邀请码视图 VO。
 *
 * @param code 短码（如 AAF-X8K2）
 * @param channel 推广渠道，null=默认
 * @param usedCount 已被绑定次数
 * @param maxInvites 最多可获奖励次数（来自 credit_grant_rule.INVITE.ext.maxInvites）
 * @author AaronZZH &amp; Kiro
 */
public record BrokerageInviteCodeMeVO(
        String code, String channel, Integer usedCount, Integer maxInvites) {}
