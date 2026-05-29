package com.xuejiai.aaf.module.channel.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 统一消息路由器。
 *
 * <p>入站：原始报文 → ChannelAdapter.receive() → UnifiedMessage → MessageHandler 链处理 → 回复路由。
 * <p>出站：内部回复 → 路由到对应 ChannelAdapter.reply()。
 * <p>降级：首选渠道不可用时尝试备选渠道。
 */
@Slf4j
@Service
public class ChannelMessageRouter {

    private final Map<ChannelTypeEnum, ChannelAdapter> adapterMap;
    private final List<MessageHandler> handlers;

    public ChannelMessageRouter(
            List<ChannelAdapter> adapters, List<MessageHandler> handlers) {
        this.adapterMap =
                adapters.stream()
                        .collect(Collectors.toMap(ChannelAdapter::channelType, Function.identity()));
        this.handlers =
                handlers.stream()
                        .sorted(Comparator.comparingInt(MessageHandler::order))
                        .toList();
    }

    /**
     * 入站路由：接收原始报文，解析为 UnifiedMessage，分发给 handler 处理，回复结果。
     *
     * @param channelType 渠道类型
     * @param rawPayload 原始报文
     * @return 被动回复内容（部分渠道需要同步返回，如微信公众号）
     */
    public String routeInbound(ChannelTypeEnum channelType, String rawPayload) {
        var adapter = getAdapter(channelType);
        var inbound = adapter.receive(rawPayload);
        log.info("入站消息: channel={}, user={}, type={}",
                channelType.getCode(), inbound.externalUserId(), inbound.messageType().getCode());

        // 分发给 handler 链
        UnifiedMessage reply = dispatch(inbound);
        if (reply != null) {
            adapter.reply(reply);
            return reply.content();
        }
        return null;
    }

    /**
     * 出站路由：将内部回复消息路由到对应渠道。
     * 支持渠道降级——首选不可用时尝试备选。
     *
     * @param message 出站消息
     * @param fallbackChannels 备选渠道（按优先级排序）
     */
    public void routeOutbound(UnifiedMessage message, List<ChannelTypeEnum> fallbackChannels) {
        var adapter = adapterMap.get(message.channelType());
        if (adapter != null && adapter.isAvailable()) {
            adapter.reply(message);
            return;
        }
        // 降级到备选渠道
        for (var fallback : fallbackChannels) {
            var fb = adapterMap.get(fallback);
            if (fb != null && fb.isAvailable()) {
                log.warn("渠道降级: {} → {}", message.channelType().getCode(), fallback.getCode());
                var degraded = new UnifiedMessage(
                        fallback,
                        message.direction(),
                        message.messageType(),
                        message.externalUserId(),
                        message.content(),
                        message.mediaUrl(),
                        message.eventType(),
                        message.eventKey(),
                        message.extra(),
                        message.rawPayload(),
                        message.timestamp());
                fb.reply(degraded);
                return;
            }
        }
        log.error("所有渠道不可用，消息丢弃: channel={}, user={}",
                message.channelType().getCode(), message.externalUserId());
    }

    /** 出站路由（无降级） */
    public void routeOutbound(UnifiedMessage message) {
        routeOutbound(message, List.of());
    }

    /** 获取指定渠道的适配器 */
    public ChannelAdapter getAdapter(ChannelTypeEnum channelType) {
        var adapter = adapterMap.get(channelType);
        if (adapter == null) {
            throw new IllegalArgumentException("未注册的渠道类型: " + channelType.getCode());
        }
        return adapter;
    }

    private UnifiedMessage dispatch(UnifiedMessage inbound) {
        for (var handler : handlers) {
            if (handler.supports(inbound)) {
                var reply = handler.handle(inbound);
                if (reply != null) {
                    return reply;
                }
            }
        }
        log.debug("无 handler 处理消息: channel={}, type={}",
                inbound.channelType().getCode(), inbound.messageType().getCode());
        return null;
    }
}
