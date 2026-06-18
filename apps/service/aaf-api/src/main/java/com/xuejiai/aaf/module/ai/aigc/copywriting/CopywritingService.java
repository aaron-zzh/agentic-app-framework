package com.xuejiai.aaf.module.ai.aigc.copywriting;

import java.util.List;
import java.util.concurrent.CompletionException;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

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
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

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
            String modelId,
            String type,
            String topic,
            String template,
            String length,
            String translateTo,
            String referenceAnalysis,
            String userNotes) {
        if (isMockEnabled()) return mockTextStream();
        Long userId = operatorContext.currentUserId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId);
        var messages =
                List.<Message>of(
                        new SystemMessage(CopywritingConstants.SYS_GENERATE),
                        new UserMessage(
                                buildGeneratePrompt(
                                        type,
                                        topic,
                                        template,
                                        length,
                                        translateTo,
                                        referenceAnalysis,
                                        userNotes)));
        log.info(
                "[文案生成] type={}, length={}, translateTo={}, modelId={}",
                type,
                length,
                translateTo,
                modelId);
        return chatService.stream(messages, ctx)
                .onErrorContinue(
                        com.openai.errors.OpenAIInvalidDataException.class,
                        (e, o) -> log.debug("[LLM流] 跳过无效 chunk: {}", e.getMessage()))
                .mapNotNull(r -> r.getResult() != null ? r.getResult().getOutput().getText() : null)
                .filter(text -> text != null && !text.isEmpty())
                .onErrorMap(this::mapLlmError);
    }

    /**
     * 流式改写文案。
     *
     * @param modelId 显式指定模型（null 则路由决策）
     * @param content 原始文案
     * @return 文字 token 流
     */
    public Flux<String> rewrite(String modelId, String content) {
        if (isMockEnabled()) return mockTextStream();
        Long userId = operatorContext.currentUserId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId);
        var messages =
                List.<Message>of(
                        new SystemMessage(CopywritingConstants.SYS_REWRITE),
                        new UserMessage("请改写以下文案：\n\n" + content));
        log.info("[文案改写] modelId={}, length={}", modelId, content.length());
        return chatService.stream(messages, ctx)
                .onErrorContinue(
                        com.openai.errors.OpenAIInvalidDataException.class,
                        (e, o) -> log.debug("[LLM流] 跳过无效 chunk: {}", e.getMessage()))
                .mapNotNull(r -> r.getResult() != null ? r.getResult().getOutput().getText() : null)
                .filter(text -> text != null && !text.isEmpty())
                .onErrorMap(this::mapLlmError);
    }

    /** 将 LLM 客户端异常统一映射为 BusinessException，使 SSE 错误信息可读。 */
    private Throwable mapLlmError(Throwable e) {
        Throwable cause =
                (e instanceof CompletionException && e.getCause() != null) ? e.getCause() : e;
        String msg = cause.getMessage();
        if (msg != null
                && (msg.contains("401")
                        || msg.contains("Authentication")
                        || msg.contains("Unauthorized"))) {
            log.error("[AI调用] API Key 无效或未授权: {}", msg);
            return new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "AI 服务认证失败，请检查 API Key 配置");
        }
        log.error("[AI调用] 调用失败: {}", msg, cause);
        return new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "AI 服务调用失败，请稍后重试");
    }

    public Flux<String> analyze(String modelId, String content) {
        if (isMockEnabled()) return mockTextStream();
        Long userId = operatorContext.currentUserId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId);
        var messages =
                List.<Message>of(
                        new SystemMessage(CopywritingConstants.SYS_ANALYZE),
                        new UserMessage("请分析以下爆款内容：\n\n" + content));
        log.info("[爆款分析] modelId={}, length={}", modelId, content.length());
        return chatService.stream(messages, ctx)
                .onErrorContinue(
                        com.openai.errors.OpenAIInvalidDataException.class,
                        (e, o) -> log.debug("[LLM流] 跳过无效 chunk: {}", e.getMessage()))
                .mapNotNull(r -> r.getResult() != null ? r.getResult().getOutput().getText() : null)
                .filter(text -> text != null)
                .onErrorMap(this::mapLlmError);
    }

    private String buildGeneratePrompt(
            String type,
            String topic,
            String template,
            String length,
            String translateTo,
            String referenceAnalysis,
            String userNotes) {
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
        if ("oral".equals(type)) {
            sb.append("格式要求：使用标准 Markdown 格式，用 `##` 分段标题、`-` 列表组织结构，自然流畅，适合视频配音。\n");
        } else {
            sb.append("格式要求：活泼有趣，多用 emoji，有吸引力的标题，直接输出纯文本，不要使用 Markdown 语法。\n");
        }
        if (template != null && !template.isBlank()) {
            String templateLabel =
                    switch (template) {
                        case "product-launch" -> "新品上市";
                        case "promotion" -> "促销活动";
                        case "brand-story" -> "品牌故事";
                        case "tutorial" -> "教程攻略";
                        case "review" -> "测评分享";
                        default -> template;
                    };
            sb.append("风格模板：").append(templateLabel).append("\n");
        }
        sb.append("长度要求：").append(lengthDesc);
        if (translateTo != null && !translateTo.isBlank()) {
            String langName =
                    switch (translateTo) {
                        case "en" -> "英文";
                        case "ja" -> "日文";
                        case "ko" -> "韩文";
                        case "fr" -> "法文";
                        case "es" -> "西班牙文";
                        default -> translateTo;
                    };
            sb.append("\n翻译要求：生成完成后将内容翻译为").append(langName);
        }
        if (referenceAnalysis != null && !referenceAnalysis.isBlank()) {
            sb.append("\n\n参考爆款结构分析：\n").append(referenceAnalysis);
        }
        if (userNotes != null && !userNotes.isBlank()) {
            sb.append("\n\n创作要求补充：\n").append(userNotes);
        }
        return sb.toString();
    }

    // ========== Mock 辅助方法 ==========

    private boolean isMockEnabled() {
        return systemConfigService.getBoolean(SysConfigKeys.Aigc.MOCK_ENABLED, false);
    }

    /** 将 mock 固定文本拆成单字符逐个 emit，模拟流式输出效果。 固定文本从 {@code aigc.mock_data} 的 {@code text} 字段读取。 */
    private Flux<String> mockTextStream() {
        var json = systemConfigService.getString(SysConfigKeys.Aigc.MOCK_DATA);
        String mockText = "这是一段 Mock 固定文字内容。";
        if (json != null && !json.isBlank()) {
            try {
                var map =
                        objectMapper.readValue(
                                json, new TypeReference<java.util.Map<String, String>>() {});
                var val = map.get("text");
                if (val != null && !val.isBlank()) mockText = val;
            } catch (Exception e) {
                log.warn("[mock] 解析 aigc.mock_data 失败: {}", e.getMessage());
            }
        }
        // 按字符拆分，模拟 token 逐字输出
        var chars = mockText.chars().mapToObj(c -> String.valueOf((char) c)).toList();
        log.info("[mock] 文案流式 mock，共 {} 字", chars.size());
        return Flux.fromIterable(chars);
    }
}
