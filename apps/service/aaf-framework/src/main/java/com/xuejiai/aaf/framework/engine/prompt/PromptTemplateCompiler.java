package com.xuejiai.aaf.framework.engine.prompt;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;

/**
 * 提示词变量安全编译器。
 *
 * <p>只识别受限的 ${name} 占位符并执行字面量替换，不解释 SpEL、脚本或嵌套表达式。变量声明、调用参数和模板占位符会在服务端交叉校验。
 */
@Component
public class PromptTemplateCompiler {

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}");
    private static final int MAX_VARIABLES = 50;
    private static final int MAX_VALUE_LENGTH = 20_000;
    private static final int MAX_COMPILED_LENGTH = 100_000;

    /** 校验模板并返回按首次出现顺序排列的变量声明。 */
    public List<String> normalizeDeclarations(
            String content, String negativePrompt, List<String> declaredVariables) {
        var placeholders = new LinkedHashSet<String>();
        collectPlaceholders(content, placeholders);
        collectPlaceholders(negativePrompt, placeholders);
        if (placeholders.size() > MAX_VARIABLES) {
            throw new IllegalArgumentException("模板变量数量不能超过 " + MAX_VARIABLES);
        }

        if (declaredVariables == null || declaredVariables.isEmpty()) {
            return List.copyOf(placeholders);
        }
        var declarations = new LinkedHashSet<String>();
        for (var variable : declaredVariables) {
            if (variable == null || !VARIABLE_NAME.matcher(variable).matches()) {
                throw new IllegalArgumentException("非法模板变量名: " + variable);
            }
            if (!declarations.add(variable)) {
                throw new IllegalArgumentException("模板变量重复声明: " + variable);
            }
        }
        if (!declarations.equals(placeholders)) {
            var missing = new LinkedHashSet<>(placeholders);
            missing.removeAll(declarations);
            var unused = new LinkedHashSet<>(declarations);
            unused.removeAll(placeholders);
            throw new IllegalArgumentException(
                    "模板变量声明与占位符不一致，缺少=%s，未使用=%s".formatted(missing, unused));
        }
        return List.copyOf(declarations);
    }

    /** 将规范化变量声明序列化为持久化 JSON。 */
    public String serializeDeclarations(
            String content, String negativePrompt, List<String> declaredVariables) {
        return JsonUtils.toJsonString(
                normalizeDeclarations(content, negativePrompt, declaredVariables));
    }

    /** 读取持久化变量声明；空声明按模板占位符安全推导。 */
    public List<String> readDeclarations(
            String content, String negativePrompt, String declarationsJson) {
        List<String> declared =
                declarationsJson == null || declarationsJson.isBlank()
                        ? List.of()
                        : JsonUtils.parseArray(declarationsJson, String.class);
        return normalizeDeclarations(content, negativePrompt, declared);
    }

    /** 使用持久化声明编译正向或反向提示词。 */
    public String compile(
            String content,
            String negativePrompt,
            String declarationsJson,
            Map<String, String> variables,
            boolean compileNegativePrompt) {
        var declarations = readDeclarations(content, negativePrompt, declarationsJson);
        var values =
                variables == null
                        ? Map.<String, String>of()
                        : new LinkedHashMap<String, String>(variables);
        requireExactVariables(declarations, values);
        return interpolate(compileNegativePrompt ? negativePrompt : content, values);
    }

    private void collectPlaceholders(String content, Set<String> placeholders) {
        if (content == null || content.isEmpty()) {
            return;
        }
        var searchFrom = 0;
        while (true) {
            var start = content.indexOf("${", searchFrom);
            if (start < 0) {
                return;
            }
            var end = content.indexOf('}', start + 2);
            if (end < 0) {
                throw new IllegalArgumentException("模板占位符未闭合: " + content.substring(start));
            }
            var variable = content.substring(start + 2, end);
            if (!VARIABLE_NAME.matcher(variable).matches()) {
                throw new IllegalArgumentException("非法模板占位符: " + content.substring(start, end + 1));
            }
            placeholders.add(variable);
            searchFrom = end + 1;
        }
    }

    private void requireExactVariables(List<String> declarations, Map<String, String> variables) {
        var declared = new LinkedHashSet<>(declarations);
        var provided = new LinkedHashSet<>(variables.keySet());
        var missing = new LinkedHashSet<>(declared);
        missing.removeAll(provided);
        var unknown = new LinkedHashSet<>(provided);
        unknown.removeAll(declared);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalArgumentException("模板变量不匹配，缺少=%s，未知=%s".formatted(missing, unknown));
        }
        for (var entry : variables.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException("模板变量不能为空白: " + entry.getKey());
            }
            if (entry.getValue().length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("模板变量过长: " + entry.getKey());
            }
        }
    }

    private String interpolate(String content, Map<String, String> variables) {
        if (content == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(content);
        var result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(variables.get(matcher.group(1))));
            if (result.length() > MAX_COMPILED_LENGTH) {
                throw new IllegalArgumentException("编译后的提示词过长");
            }
        }
        matcher.appendTail(result);
        if (result.length() > MAX_COMPILED_LENGTH) {
            throw new IllegalArgumentException("编译后的提示词过长");
        }
        return result.toString();
    }
}
