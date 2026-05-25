package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据清洗步骤——去重、过滤缺失字段、基础格式化。
 */
@Component
@Order(20)
public class DataCleaner implements ProcessingStep {

    @Override
    public String name() {
        return "DataCleaner";
    }

    @Override
    public ProcessingContext execute(ProcessingContext context) {
        var rules = context.getConfig().getCleanRules();
        if (rules == null) {
            return context;
        }

        var items = context.getItems();

        // 1. 去重
        if (rules.getDeduplicateBy() != null) {
            var seen = new HashSet<>();
            items = items.stream()
                    .filter(item -> {
                        var key = item.get(rules.getDeduplicateBy());
                        return key != null && seen.add(key);
                    })
                    .collect(Collectors.toList());
        }

        // 2. 必填字段过滤
        if (rules.getRequiredFields() != null) {
            items = items.stream()
                    .filter(item -> rules.getRequiredFields().stream()
                            .allMatch(f -> item.get(f) != null && !item.get(f).toString().isBlank()))
                    .collect(Collectors.toList());
        }

        // 3. 条件过滤（简单数值比较）
        if (rules.getFilters() != null) {
            for (var filter : rules.getFilters()) {
                items = items.stream()
                        .filter(item -> evaluateFilter(item, filter))
                        .collect(Collectors.toList());
            }
        }

        context.setItems(items);
        return context;
    }

    /**
     * 简单过滤表达式：field > value / field < value / field = value
     */
    private boolean evaluateFilter(Map<String, Object> item, String filter) {
        try {
            if (filter.contains(">")) {
                var parts = filter.split(">");
                var value = resolveNumber(item, parts[0].trim());
                var threshold = Long.parseLong(parts[1].trim());
                return value > threshold;
            } else if (filter.contains("<")) {
                var parts = filter.split("<");
                var value = resolveNumber(item, parts[0].trim());
                var threshold = Long.parseLong(parts[1].trim());
                return value < threshold;
            }
        } catch (Exception e) {
            // 解析失败则保留
        }
        return true;
    }

    private long resolveNumber(Map<String, Object> item, String field) {
        var value = item.get(field);
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        return 0;
    }
}
