/**
 * 访客线索（Guest Lead）模块。
 *
 * <p>记录未登录用户在公开页的所有动作流水：
 *
 * <ul>
 *   <li>{@code CHAT} —— 匿名 AI 客服对话续聊（保存 threadId/agentRole）
 *   <li>{@code NEWSLETTER} —— 邮箱订阅（如即将上线通知）
 *   <li>{@code CONTACT} —— 联系我们表单
 *   <li>{@code FEEDBACK} —— 用户反馈
 * </ul>
 *
 * <p>同一访客通过 {@code anonymousId}（前端 localStorage 持久 UUID）关联多次动作。 访客转正后可填充 {@code contact_id} 关联到正式
 * {@link com.xuejiai.aaf.module.system.contact.domain.Contact}。
 *
 * <p>对外暴露两套接口：
 *
 * <ul>
 *   <li>{@code /api/system/leads/**} —— 管理端，标准 BaseCrud + 鉴权
 *   <li>{@code /api/public/leads/**} —— 访客自助，匿名可访问，IP 速率限制
 * </ul>
 */
package com.xuejiai.aaf.module.system.lead;
