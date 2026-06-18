package com.xuejiai.aaf.framework.intelligent.ai.chat;

/**
 * 积分余额低于预警阈值事件。
 *
 * <p>由 {@link com.xuejiai.aaf.framework.engine.credit.AiCreditGuard#precheck} 异步发布，
 * 供通知模块监听后向用户推送充值提醒，不阻塞 AI 调用。
 *
 * <p><b>当前暂无监听者</b>，事件发布后无实际处理。 后续可添加 {@code @EventListener} 实现站内信、短信或 SSE 推送等充值提醒。
 *
 * @param userId 用户 ID
 * @param balance 当前可用余额
 * @param threshold 预警阈值
 */
public record CreditLowEvent(Long userId, long balance, long threshold) {}
