package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 增强步骤——调用 LLM 对数据进行摘要/分类/情感分析/标签提取。
 *
 * <p>支持的增强类型：
 * <ul>
 *   <li>summary — 生成摘要</li>
 *   <li>classification — 分类（需在 params 中指定 categories）</li>
 *   <li>sentiment — 情感分析（正面/中性/负面）</li>
 *   <li>tags — 标签提取</li>
 * </ul>
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class AiEnricher implements ProcessingStep {

    private final ResilientChatService chatService;

    @Override
    public String name() {
        return "AiEnricher";
    }

    @Override
    public ProcessingContext execute(ProcessingContext context) {
        var enrichments = context.getConfig().getEnrichments();
        if (enrichments == null || enrichments.isEmpty()) {
            return context;
        }

        for (var item : context.getItems()) {
            for (var config : enrichments) {
                try {
                    enrich(item, config);
                } catch (Exception e) {
                    log.warn("AI 增强失败 [{}]: {}", config.getType(), e.getMessage());
                }
            }
        }
        return context;
    }

    private void enrich(Map<String, Object> item, PipelineConfig.EnrichmentConfig config) {
        var inputValue = item.get(config.getInputField());
        if (inputValue == null || inputValue.toString().isBlank()) {
            return;
        }
        var text = inputValue.toString();
        var prompt = buildPrompt(config.getType(), text, config.getParams());
        var messages = List.of((org.springframework.ai.chat.messages.Message) new UserMessage(prompt));
        var response = chatService.call(messages, null, null);
        var result = response.getResult().getOutput().getText();
        item.put(config.getOutputField(), result != null ? result.trim() : "");
    }

    private String buildPrompt(String type, String text, Map<String, String> params) {
        return switch (type) {
            case "summary" -> {
                var maxLen = params != null ? params.getOrDefault("max_length", "100") : "100";
                yield "请用不超过%s字概括以下内容，只输出摘要：\n\n%s".formatted(maxLen, text);
            }
            case "classification" -> {
                var categories = params != null ? params.getOrDefault("categories", "其他") : "其他";
                yield "请将以下内容分类到这些类别之一：[%s]。只输出类别名称：\n\n%s".formatted(categories, text);
            }
            case "sentiment" -> "请分析以下内容的情感倾向，只输出一个词（正面/中性/负面）：\n\n" + text;
            case "tags" -> "请从以下内容中提取3-5个关键标签，用逗号分隔，只输出标签：\n\n" + text;
            default -> "请处理以下内容：\n\n" + text;
        };
    }
}
