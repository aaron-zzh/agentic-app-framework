package com.xuejiai.aaf.framework.engine.valuerule;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认价值规则引擎——基于关键词黑名单的内容过滤。
 * 后续可升级为 LLM 驱动的语义级价值判断。
 */
@Slf4j
@Component
public class DefaultValueRuleEngine implements ValueRuleEngine {

    /** 黑名单关键词（后续从数据库/配置加载） */
    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "暴力", "色情", "赌博", "毒品", "自杀", "恐怖主义");

    @Override
    public ValidationResult validate(String content) {
        if (content == null || content.isBlank()) return ValidationResult.pass();
        for (var keyword : BLOCKED_KEYWORDS) {
            if (content.contains(keyword)) {
                log.debug("价值规则拦截: 命中关键词 [{}]", keyword);
                return ValidationResult.reject("内容包含违规关键词: " + keyword);
            }
        }
        return ValidationResult.pass();
    }

    @Override
    public <T> List<T> filter(List<T> contents, ContentExtractor<T> extractor) {
        if (contents == null || contents.isEmpty()) return contents;
        return contents.stream()
                .filter(item -> validate(extractor.extract(item)).passed())
                .toList();
    }
}
