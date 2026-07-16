package com.xuejiai.aaf.module.ai.aigc.copywriting;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.intelligent.ai.vision.VisionAttachment;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.ai.skill.SkillService;
import com.xuejiai.aaf.module.ai.vision.VisionMediaResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import tools.jackson.core.type.TypeReference;

/** 文案生成服务——通过 ResilientChatService 流式调用，模型由 CapabilityRouter 从 ai_model 表解析。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopywritingService {

    private final ResilientChatService chatService;
    private final OperatorContext operatorContext;
    private final SystemConfigService systemConfigService;
    private final VisionMediaResolver visionMediaResolver;
    private final SkillService skillService;

    /**
     * 流式生成文案（常规场景：口播/小红书/自定义技能）。
     *
     * @param modelId 显式指定模型（null 则路由决策；附带参考图时路由会优先选 VISION 模型）
     * @param type 技能 code（如 voiceover=口播 / redbook=小红书），由前端直传；未配置对应技能时回退到内置提示词
     * @param prompt 创作提示词（主题词或完整写作指令）
     * @param template 风格模板名（可为空）
     * @param length 长度（short / medium / long）
     * @param referenceImageKeys 参考图 fileKey 列表（OSS 内部 key），可为空；非空时模型按图理解风格、配色、构图
     * @return 文字 token 流
     */
    public Flux<String> generate(
            String modelId,
            String type,
            String prompt,
            String template,
            String length,
            String translateTo,
            List<String> referenceImageKeys) {
        if (isMockEnabled()) return mockTextStream();
        Long userId = operatorContext.currentUserId().orElse(null);

        // 解析参考图：fileKey → 签名 GET URL + mimeType
        List<VisionAttachment> attachments = visionMediaResolver.resolve(referenceImageKeys);
        List<Media> media =
                attachments.stream()
                        .map(
                                att ->
                                        new Media(
                                                MimeType.valueOf(att.mimeType()),
                                                URI.create(att.signedUrl())))
                        .toList();

        // 构造路由上下文：有参考图时设 hasImage=true，让选择器优先选 VISION 模型
        Map<String, Object> features = null;
        if (!media.isEmpty()) {
            features = new HashMap<>();
            features.put(CapabilityRoutingContext.FEATURE_HAS_IMAGE, true);
        }
        var ctx =
                new CapabilityRoutingContext(
                        userId, CapabilityRoutingContext.CAP_CHAT, modelId, null, features);

        // 按 skill code 动态加载系统提示词；有则用，无则用内置常量
        String systemPrompt = resolveSystemPrompt(type);
        boolean hasSkillPrompt = !CopywritingConstants.SYS_GENERATE.equals(systemPrompt);

        String userPrompt =
                buildGeneratePrompt(type, prompt, template, length, translateTo, hasSkillPrompt);
        UserMessage userMessage =
                media.isEmpty()
                        ? new UserMessage(userPrompt)
                        : UserMessage.builder().text(userPrompt).media(media).build();

        var messages = List.<Message>of(new SystemMessage(systemPrompt), userMessage);
        log.info(
                "[文案生成] type={}, length={}, translateTo={}, modelId={}, refImageCount={}",
                type,
                length,
                translateTo,
                modelId,
                media.size());
        return chatService.stream(messages, ctx)
                .onErrorContinue(
                        com.openai.errors.OpenAIInvalidDataException.class,
                        (e, o) -> log.debug("[LLM流] 跳过无效 chunk: {}", e.getMessage()))
                .mapNotNull(r -> r.getResult() != null ? r.getResult().getOutput().getText() : null)
                .filter(text -> text != null && !text.isEmpty())
                .onErrorMap(this::mapLlmError);
    }

    /**
     * 流式生成文案（爆款复制场景专用）：参考爆款结构分析结果创作，不涉及 type/skill/template。
     *
     * @param modelId 显式指定模型（null 则路由决策）
     * @param analysis 爆款结构分析结果（{@link #analyze} 的输出）
     * @param userNotes 本次创作的主题/调整说明（可为空）
     * @return 文字 token 流
     */
    public Flux<String> generateFromAnalysis(String modelId, String analysis, String userNotes) {
        if (isMockEnabled()) return mockTextStream();
        Long userId = operatorContext.currentUserId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId);

        var sb = new StringBuilder();
        if (userNotes != null && !userNotes.isBlank()) {
            sb.append("创作主题：").append(userNotes).append("\n");
        }
        sb.append("参考以下爆款结构分析来组织内容：\n").append(analysis);

        var messages =
                List.<Message>of(
                        new SystemMessage(CopywritingConstants.SYS_GENERATE),
                        new UserMessage(sb.toString()));
        log.info("[爆款复制生成] modelId={}, analysisLength={}", modelId, analysis.length());
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
            String prompt,
            String template,
            String length,
            String translateTo,
            boolean hasSkillPrompt) {
        boolean isVoiceoverOrRedbook = "voiceover".equals(type) || "redbook".equals(type);
        String lengthDesc =
                switch (length != null ? length : "medium") {
                    case "short" -> "短篇（200字以内）";
                    case "long" -> "长篇（优先保证内容完整，不超过3000字）";
                    default -> "中篇（200-500字）";
                };
        var sb = new StringBuilder();
        sb.append("创作提示词：").append(prompt).append("\n");

        // 技能未配置/未命中时的内置格式兜底（正常情况下 voiceover/redbook 技能始终存在，此分支基本不触发）
        if (!hasSkillPrompt && isVoiceoverOrRedbook) {
            appendFormatRule(sb, type);
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
        log.debug("[文案生成] 最终 prompt:\n{}", sb);
        return sb.toString();
    }

    /** 追加 voiceover/redbook 内置回退的固定格式规则（Markdown vs emoji 纯文本）；技能命中时格式规则已写入技能提示词本身，不走这里。 */
    private void appendFormatRule(StringBuilder sb, String type) {
        if ("voiceover".equals(type)) {
            sb.append("格式要求：使用标准 Markdown 格式，用 `##` 分段标题、`-` 列表组织结构，自然流畅，适合视频配音。\n");
        } else {
            sb.append("格式要求：活泼有趣，多用 emoji，有吸引力的标题，直接输出纯文本，不要使用 Markdown 语法。\n");
        }
    }

    private String resolveSystemPrompt(String type) {
        if (type != null && !type.isBlank()) {
            // type 即 skill code（前端直传，如 voiceover/redbook/rich-text-write），未配置或已禁用时回退到内置常量
            String prompt = skillService.getSystemPromptByCode(type);
            if (prompt != null && !prompt.isBlank()) {
                log.debug("[文案生成] 使用 skill[{}] 的系统提示词", type);
                return prompt;
            }
        }
        log.debug("[文案生成] skill[{}] 未配置系统提示词，回退到内置常量", type);
        return CopywritingConstants.SYS_GENERATE;
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
                        JsonUtils.parseObject(
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
