package com.xuejiai.aaf.framework.engine.valuerule;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 默认价值规则引擎——使用数据库可配置规则，规则服务不可用时 fail-closed。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultValueRuleEngine implements ValueRuleEngine {

    private final ValueRuleRepository valueRuleRepository;

    @Override
    public ValidationResult validate(String content) {
        if (content == null || content.isBlank()) return ValidationResult.pass();

        try {
            var normalizedContent = normalizeForMatch(content);
            var rules = valueRuleRepository.findEnabledForbiddenRules();
            for (var rule : rules) {
                var condition = normalizeForMatch(rule.getCondition());
                if (!condition.isEmpty() && normalizedContent.contains(condition)) {
                    log.debug("价值规则拦截: 命中规则 [{}]", rule.getName());
                    return ValidationResult.reject("内容违反价值规则: " + rule.getName());
                }
            }
            return ValidationResult.pass();
        } catch (RuntimeException e) {
            log.error("价值规则查询失败，按安全策略拒绝内容", e);
            return ValidationResult.reject("内容安全规则服务暂不可用");
        }
    }

    @Override
    public <T> List<T> filter(List<T> contents, ContentExtractor<T> extractor) {
        if (contents == null || contents.isEmpty()) return contents;
        return contents.stream()
                .filter(item -> validate(extractor.extract(item)).passed())
                .toList();
    }

    private String normalizeForMatch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        var result = new StringBuilder(normalized.length());
        normalized.codePoints().filter(Character::isLetterOrDigit).forEach(result::appendCodePoint);
        return result.toString();
    }
}
