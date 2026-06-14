package com.xuejiai.aaf.framework.integration.wecom;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpOaService;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.WxCpUserService;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;

/**
 * 企业微信客户端——封装 WxCpService，供上层业务统一调用。
 *
 * <p>屏蔽 SDK 细节，提供语义化 API。需配置 aaf.integration.wecom.corp-id 后激活。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(WxCpService.class)
public class WecomClient {

    private final WxCpService wxCpService;

    // ── 消息推送 ──────────────────────────────────────────────

    /**
     * 发送文本工作通知给指定用户列表。
     *
     * @param userIds 企业微信 userId 列表
     * @param content 文本内容
     */
    public void sendText(List<String> userIds, String content) {
        send(WxCpMessage.TEXT().toUser(String.join("|", userIds)).content(content).build());
    }

    /**
     * 发送 Markdown 工作通知。
     *
     * @param userIds 企业微信 userId 列表
     * @param content Markdown 内容
     */
    public void sendMarkdown(List<String> userIds, String content) {
        send(WxCpMessage.MARKDOWN().toUser(String.join("|", userIds)).content(content).build());
    }

    /** 发送工作通知（底层，支持所有消息类型）。 */
    public void send(WxCpMessage message) {
        try {
            wxCpService.getMessageService().send(message);
            log.info(
                    "企业微信消息发送成功: toUser={}, msgType={}", message.getToUser(), message.getMsgType());
        } catch (WxErrorException e) {
            log.error(
                    "企业微信消息发送失败: errCode={}, errMsg={}",
                    e.getError().getErrorCode(),
                    e.getError().getErrorMsg());
            throw new RuntimeException("企业微信消息发送失败: " + e.getError().getErrorMsg(), e);
        }
    }

    // ── 能力暴露 ──────────────────────────────────────────────

    /** OA 服务（审批/日程/汇报等） */
    public WxCpOaService oa() {
        return wxCpService.getOaService();
    }

    /** 通讯录服务（用户/部门/标签等） */
    public WxCpUserService user() {
        return wxCpService.getUserService();
    }

    /** 原始 WxCpService，用于访问未封装的 API */
    public WxCpService raw() {
        return wxCpService;
    }
}
