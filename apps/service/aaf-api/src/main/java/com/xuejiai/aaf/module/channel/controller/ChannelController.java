package com.xuejiai.aaf.module.channel.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.channel.domain.ChannelConfig;
import com.xuejiai.aaf.module.channel.domain.WebhookConfig;
import com.xuejiai.aaf.module.channel.service.ChannelConfigService;
import com.xuejiai.aaf.module.channel.service.ChannelMessageRouter;
import com.xuejiai.aaf.module.channel.service.MiniAppLoginService;
import com.xuejiai.aaf.module.channel.service.WebhookService;
import com.xuejiai.aaf.module.channel.service.adapter.DingtalkBotChannelAdapter;
import com.xuejiai.aaf.module.channel.service.adapter.FeishuBotChannelAdapter;
import com.xuejiai.aaf.module.channel.vo.ChannelStatsVO;
import com.xuejiai.aaf.module.channel.vo.MiniAppLoginDTO;
import com.xuejiai.aaf.module.channel.vo.MiniAppPhoneLoginDTO;
import com.xuejiai.aaf.module.channel.vo.MiniAppSessionVO;

import lombok.RequiredArgsConstructor;

/**
 * 渠道接入控制器。
 *
 * <p>提供各渠道 Webhook 回调入口、渠道配置管理、监控统计接口。
 */
@RestController
@RequestMapping("/api/channel")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelMessageRouter router;
    private final MiniAppLoginService miniAppLoginService;
    private final ChannelConfigService channelConfigService;
    private final WebhookService webhookService;

    /** 可选注入——仅 aaf.channel.dingtalk.enabled=true 时存在 */
    @Autowired(required = false)
    private DingtalkBotChannelAdapter dingtalkAdapter;

    /** 可选注入——仅 aaf.channel.feishu.enabled=true 时存在 */
    @Autowired(required = false)
    private FeishuBotChannelAdapter feishuAdapter;

    // ==================== 微信回调 ====================

    /** 微信公众号消息回调（POST） */
    @PostMapping("/wx/mp/callback")
    public String wxMpCallback(@RequestBody String xmlPayload) {
        var reply = router.routeInbound(ChannelTypeEnum.WECHAT_MP, xmlPayload);
        return reply != null ? "success" : "success";
    }

    /** 微信公众号验证（GET） */
    @GetMapping("/wx/mp/callback")
    public String wxMpVerify(@RequestParam String echostr) {
        return echostr;
    }

    /** 微信小程序客服消息回调 */
    @PostMapping("/wx/mini/callback")
    public String wxMiniCallback(@RequestBody String xmlPayload) {
        router.routeInbound(ChannelTypeEnum.WECHAT_MINI, xmlPayload);
        return "success";
    }

    /** 微信小程序登录 */
    @PostMapping("/wx/mini/login")
    public Result<MiniAppSessionVO> wxMiniLogin(@Validated @RequestBody MiniAppLoginDTO dto) {
        return Result.success(miniAppLoginService.login(dto));
    }

    /** 微信小程序手机号一键登录 */
    @PostMapping("/wx/mini/phone-login")
    public Result<MiniAppSessionVO> wxMiniPhoneLogin(
            @Validated @RequestBody MiniAppPhoneLoginDTO dto) {
        return Result.success(miniAppLoginService.phoneLogin(dto));
    }

    // ==================== 钉钉机器人回调 ====================

    /** 钉钉机器人消息回调 */
    @PostMapping("/dingtalk/callback")
    public Result<String> dingtalkCallback(
            @RequestHeader(value = "timestamp", required = false) String timestamp,
            @RequestHeader(value = "sign", required = false) String sign,
            @RequestBody String jsonPayload) {
        if (dingtalkAdapter == null) {
            return Result.error(GlobalErrorCode.BAD_REQUEST, "钉钉渠道未启用");
        }
        // 加签验证
        if (timestamp != null && sign != null) {
            if (!dingtalkAdapter.verifySign(timestamp, sign)) {
                return Result.error(GlobalErrorCode.FORBIDDEN, "签名验证失败");
            }
        }
        router.routeInbound(ChannelTypeEnum.DINGTALK, jsonPayload);
        return Result.success("ok");
    }

    // ==================== 飞书机器人回调 ====================

    /** 飞书事件订阅回调 */
    @PostMapping("/feishu/callback")
    public Map<String, Object> feishuCallback(
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
            @RequestBody String jsonPayload) {
        if (feishuAdapter == null) {
            return Map.of("error", "飞书渠道未启用");
        }
        // 签名验证
        if (signature != null) {
            if (!feishuAdapter.verifySign(timestamp, nonce, jsonPayload, signature)) {
                return Map.of("error", "签名验证失败");
            }
        }
        // 飞书 URL 验证需要返回 challenge
        var inbound = feishuAdapter.receive(jsonPayload);
        if (inbound.extra() != null && Boolean.TRUE.equals(inbound.extra().get("isChallenge"))) {
            return Map.of("challenge", inbound.extra().get("challenge"));
        }
        router.routeInbound(ChannelTypeEnum.FEISHU, jsonPayload);
        return Map.of("code", 0);
    }

    // ==================== Webhook 回调 ====================

    /** 入站 Webhook 接收 */
    @PostMapping("/webhook/inbound")
    public Result<String> webhookInbound(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "X-Webhook-Id", required = false) Long webhookId,
            @RequestBody String jsonPayload) {
        if (webhookId != null
                && !webhookService.verifyInboundSignature(webhookId, signature, jsonPayload)) {
            return Result.error(GlobalErrorCode.FORBIDDEN, "签名验证失败");
        }
        webhookService.receiveInbound(jsonPayload);
        return Result.success("ok");
    }

    // ==================== 渠道配置管理（#7505） ====================

    /** 创建渠道配置 */
    @PostMapping("/config")
    public Result<ChannelConfig> createConfig(@RequestBody ChannelConfig config) {
        return Result.success(channelConfigService.create(config));
    }

    /** 更新渠道配置 */
    @PutMapping("/config/{id}")
    public Result<ChannelConfig> updateConfig(
            @PathVariable Long id, @RequestBody ChannelConfig config) {
        return Result.success(channelConfigService.update(id, config));
    }

    /** 删除渠道配置 */
    @DeleteMapping("/config/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        channelConfigService.delete(id);
        return Result.success(null);
    }

    /** 获取渠道配置详情 */
    @GetMapping("/config/{id}")
    public Result<ChannelConfig> getConfig(@PathVariable Long id) {
        return Result.success(channelConfigService.getById(id));
    }

    /** 获取所有启用的渠道配置 */
    @GetMapping("/config/list")
    public Result<List<ChannelConfig>> listConfigs() {
        return Result.success(channelConfigService.listEnabled());
    }

    // ==================== Webhook 配置管理 ====================

    /** 创建 Webhook 配置 */
    @PostMapping("/webhook/config")
    public Result<WebhookConfig> createWebhook(@RequestBody WebhookConfig config) {
        return Result.success(webhookService.create(config));
    }

    /** 更新 Webhook 配置 */
    @PutMapping("/webhook/config/{id}")
    public Result<WebhookConfig> updateWebhook(
            @PathVariable Long id, @RequestBody WebhookConfig config) {
        return Result.success(webhookService.update(id, config));
    }

    /** 删除 Webhook 配置 */
    @DeleteMapping("/webhook/config/{id}")
    public Result<Void> deleteWebhook(@PathVariable Long id) {
        webhookService.delete(id);
        return Result.success(null);
    }

    /** 获取活跃 Webhook 列表 */
    @GetMapping("/webhook/config/list")
    public Result<List<WebhookConfig>> listWebhooks() {
        return Result.success(webhookService.listActive());
    }

    // ==================== 监控与统计（#7505） ====================

    /** 渠道状态监控 */
    @GetMapping("/stats")
    public Result<List<ChannelStatsVO>> getChannelStats() {
        return Result.success(channelConfigService.getChannelStats());
    }

    /** 渠道连通性测试 */
    @PostMapping("/test/{channelType}")
    public Result<String> testConnection(@PathVariable String channelType) {
        return Result.success(channelConfigService.testConnection(channelType));
    }

    /** 消息统计面板 */
    @GetMapping("/stats/messages")
    public Result<Map<String, Object>> getMessageStats(
            @RequestParam String channelType,
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime) {
        return Result.success(
                channelConfigService.getMessageStats(channelType, startTime, endTime));
    }
}
