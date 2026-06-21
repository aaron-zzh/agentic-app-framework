package com.xuejiai.aaf.module.billing.service;

import com.xuejiai.aaf.module.billing.domain.Subscription;

/**
 * 订阅自动续费服务（接口预留——AAF-099 v0.2.0）。
 *
 * <p>当前阶段：本期不实现渠道代扣，{@link SubscriptionExpireScheduler} 不调用此接口， 付费订阅到期未付费即冻结至 FREE。auto_renew
 * 字段仅作为"用户意图位"记录。
 *
 * <p>未来接入步骤（参考 docs/design/apps/service/membership-completion.md 自动续费扩展点章节）：
 *
 * <ol>
 *   <li>新增 {@code pay_signed_contract} 表存储渠道代扣签约信息（微信支付 / 支付宝代扣）
 *   <li>实现本接口（调代扣 SDK + 写 BizOrder/PayOrder + 触发 onPaySuccess 走升级/续费流程）
 *   <li>{@code SubscriptionExpireScheduler.expireAndSwitch} 在付费档分支调用 {@link
 *       #tryAutoCharge(Subscription)}
 *       <ul>
 *         <li>成功：激活 pendingPlan（或同 plan 续费）
 *         <li>失败 / 未签约：走 freeze 路径（旧订阅 EXPIRED + 自动激活 FREE）
 *       </ul>
 *   <li>用户在订阅时勾选"开通自动续费"调代扣 SDK 拉协议号并写 pay_signed_contract
 *   <li>FAQ Q4 文案恢复"会自动续费"
 * </ol>
 */
public interface SubscriptionAutoRenewService {

    /**
     * 尝试通过渠道代扣自动续费订阅（占位扩展点）。
     *
     * @param sub 待续费的订阅实体（status=ACTIVE 且 end_at &lt;= now）
     * @return true 表示扣款成功并已激活新周期；false 表示扣款失败 / 未签约 / 不支持
     */
    boolean tryAutoCharge(Subscription sub);
}
