package com.xuejiai.aaf.module.brokerage.vo;

import java.time.LocalDateTime;

/**
 * 我邀请的好友列表项。
 *
 * @param contactId 被邀请人 contact_id
 * @param nickname 昵称（脱敏后）
 * @param avatar 头像 URL
 * @param registerTime 注册时间（即绑定推荐人时间）
 * @param isMember 是否当前为会员（存在 ACTIVE 订阅）
 * @param rewardCredits 我从该好友获得的累计邀请奖励积分（INVITE 来源），未发放则为 0
 * @author AaronZZH &amp; Kiro
 */
public record BrokerageInvitedUserVO(
        Long contactId,
        String nickname,
        String avatar,
        LocalDateTime registerTime,
        Boolean isMember,
        Long rewardCredits) {}
