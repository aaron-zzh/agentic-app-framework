package com.xuejiai.aaf.framework.engine.valuerule;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 默认价值规则引擎——优先从数据库加载规则，降级为内置关键词黑名单。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultValueRuleEngine implements ValueRuleEngine {

    private final ValueRuleRepository valueRuleRepository;

    /** 内置兜底黑名单（数据库不可用时使用） */
    private static final List<String> FALLBACK_KEYWORDS =
            List.of("暴力", "色情", "赌博", "毒品", "自杀", "恐怖主义");

    @Override
    public ValidationResult validate(String content) {
        if (content == null || content.isBlank()) return ValidationResult.pass();

        // 优先从数据库加载 FORBIDDEN 规则
        try {
            var rules = valueRuleRepository.findEnabledForbiddenRules();
            for (var rule : rules) {
                if (content.contains(rule.getCondition())) {
                    log.debug("价值规则拦截: 命中规则 [{}]", rule.getName());
                    return ValidationResult.reject("内容违反价值规则: " + rule.getName());
                }
            }
            return ValidationResult.pass();
        } catch (Exception e) {
            // 降级：使用内置关键词
            log.warn("价值规则数据库查询失败，使用内置黑名单: {}", e.getMessage());
            for (var keyword : FALLBACK_KEYWORDS) {
                if (content.contains(keyword)) {
                    return ValidationResult.reject("内容包含违规关键词: " + keyword);
                }
            }
            return ValidationResult.pass();
        }
    }

    @Override
    public <T> List<T> filter(List<T> contents, ContentExtractor<T> extractor) {
        if (contents == null || contents.isEmpty()) return contents;
        return contents.stream()
                .filter(item -> validate(extractor.extract(item)).passed())
                .toList();
    }
}
