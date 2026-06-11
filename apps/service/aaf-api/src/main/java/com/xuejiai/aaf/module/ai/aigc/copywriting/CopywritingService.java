package com.xuejiai.aaf.module.ai.aigc.copywriting;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/** 文案生成服务——通过 ResilientChatService 流式调用，模型由 CapabilityRouter 从 ai_model 表解析。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopywritingService {

    private final ResilientChatService chatService;
    private final OperatorContext operatorContext;

    /**
     * 流式生成文案。
     *
     * @param modelId 显式指定模型（null 则路由决策）
     * @param type 文案类型（oral / xiaohongshu）
     * @param topic 主题或关键词
     * @param template 模板名（可为空）
     * @param length 长度（short / medium / long）
     * @return 文字 token 流
     */
    public Flux<String> generate(
            String modelId, String type, String topic, String template, String length) {
        Long userId = operatorContext.currentUserId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId);
        var messages =
                List.<Message>of(
                        new SystemMessage(CopywritingConstants.SYS_GENERATE),
                        new UserMessage(buildGeneratePrompt(type, topic, template, length)));
        log.info("[文案生成] type={}, length={}, modelId={}", type, length, modelId);
        return chatService.stream(messages, ctx)
                .mapNotNull(r -> r.getResult() != null ? r.getResult().getOutput().getText() : null)
                .filter(text -> text != null && !text.isEmpty());
    }

    /**
     * 流式改写文案。
     *
     * @param modelId 显式指定模型（null 则路由决策）
     * @param content 原始文案
     * @return 文字 token 流
     */
    public Flux<String> rewrite(String modelId, String content) {
        Long userId = operatorContext.currentUserId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId);
        var messages =
                List.<Message>of(
                        new SystemMessage(CopywritingConstants.SYS_REWRITE),
                        new UserMessage("请改写以下文案：\n\n" + content));
        log.info("[文案改写] modelId={}, length={}", modelId, content.length());
        return chatService.stream(messages, ctx)
                .mapNotNull(r -> r.getResult() != null ? r.getResult().getOutput().getText() : null)
                .filter(text -> text != null && !text.isEmpty());
    }

    private String buildGeneratePrompt(String type, String topic, String template, String length) {
        String typeName = "oral".equals(type) ? "口播" : "小红书";
        String lengthDesc =
                switch (length != null ? length : "medium") {
                    case "short" -> "短篇（200字以内）";
                    case "long" -> "长篇（500字以上）";
                    default -> "中篇（200-500字）";
                };
        var sb = new StringBuilder();
        sb.append("请生成一篇").append(typeName).append("文案。\n");
        sb.append("主题：").append(topic).append("\n");
        if (template != null && !template.isBlank()) {
            sb.append("风格模板：").append(template).append("\n");
        }
        sb.append("长度要求：").append(lengthDesc);
        return sb.toString();
    }
}
