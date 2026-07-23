package com.xuejiai.aaf.framework.bizlog.parse;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

import com.xuejiai.aaf.framework.bizlog.beans.MethodExecuteResult;
import com.xuejiai.aaf.framework.bizlog.service.impl.DiffParseFunction;
import com.xuejiai.aaf.framework.logging.SafeLogValueResolver;

/**
 * 操作日志安全模板解析基类。
 *
 * <p>模板语法：
 *
 * <ul>
 *   <li>{@code {{#param}}} — 读取参数或上下文变量
 *   <li>{@code {functionName{#param}}} — 将安全路径读取的值交给已注册函数
 *   <li>{@code {_DIFF{#newObj}}} 或 {@code {_DIFF{#oldObj, #newObj}}} — diff 函数
 * </ul>
 *
 * <p>路径只允许变量名、只读属性和 {@code size()}，不支持类型、Bean、构造器、运算符或任意方法调用。
 */
public class LogRecordValueParser implements BeanFactoryAware {

    /** 匹配 {functionName{path}} 格式，functionName 可为空。 */
    private static final Pattern PATTERN = Pattern.compile("\\{\\s*(\\w*)\\s*\\{(.*?)}}");

    public static final String COMMA = ",";

    private final SafeLogValueResolver valueResolver = new SafeLogValueResolver();
    protected BeanFactory beanFactory;
    protected boolean diffSameWhetherSaveLog;

    private LogFunctionParser logFunctionParser;
    private DiffParseFunction diffParseFunction;

    public String singleProcessTemplate(
            MethodExecuteResult methodExecuteResult,
            String template,
            Map<String, String> beforeFunctionNameAndReturnMap) {
        return processTemplate(
                        List.of(template), methodExecuteResult, beforeFunctionNameAndReturnMap)
                .get(template);
    }

    public Map<String, String> processTemplate(
            Collection<String> templates,
            MethodExecuteResult methodExecuteResult,
            Map<String, String> beforeFunctionNameAndReturnMap) {
        var expressionValues = new HashMap<String, String>();
        var variables = createVariables(methodExecuteResult);

        for (var expressionTemplate : templates) {
            if (!expressionTemplate.contains("{")) {
                expressionValues.put(expressionTemplate, expressionTemplate);
                continue;
            }
            var matcher = PATTERN.matcher(expressionTemplate);
            var parsedStr = new StringBuffer();
            boolean sameDiff = false;
            while (matcher.find()) {
                var expression = matcher.group(2);
                var functionName = matcher.group(1);
                if (DiffParseFunction.diffFunctionName.equals(functionName)) {
                    expression = getDiffFunctionValue(variables, expression);
                    sameDiff = Objects.equals("", expression);
                } else {
                    var value = valueResolver.resolve(expression, variables);
                    expression =
                            logFunctionParser.getFunctionReturnValue(
                                    beforeFunctionNameAndReturnMap,
                                    value,
                                    expression,
                                    functionName);
                }
                matcher.appendReplacement(
                        parsedStr, Matcher.quoteReplacement(expression == null ? "" : expression));
            }
            matcher.appendTail(parsedStr);
            // diff 未变化且不强制记录时，保留模板原文（saveLog 阶段据此跳过）
            expressionValues.put(
                    expressionTemplate,
                    shouldRecord(sameDiff) ? parsedStr.toString() : expressionTemplate);
        }
        return expressionValues;
    }

    public Map<String, String> processBeforeExecuteFunctionTemplate(
            Collection<String> templates, Class<?> targetClass, Method method, Object[] args) {
        var functionNameAndReturnValueMap = new HashMap<String, String>();
        var variables = valueResolver.createVariables(method, args, targetClass, null, null);

        for (var expressionTemplate : templates) {
            if (!expressionTemplate.contains("{")) {
                continue;
            }
            var matcher = PATTERN.matcher(expressionTemplate);
            while (matcher.find()) {
                var expression = matcher.group(2);
                if (expression.contains("#_ret") || expression.contains("#_errorMsg")) {
                    continue;
                }
                var functionName = matcher.group(1);
                if (logFunctionParser.beforeFunction(functionName)) {
                    var value = valueResolver.resolve(expression, variables);
                    var functionReturnValue =
                            logFunctionParser.getFunctionReturnValue(
                                    null, value, expression, functionName);
                    functionNameAndReturnValueMap.put(
                            logFunctionParser.getFunctionCallInstanceKey(functionName, expression),
                            functionReturnValue);
                }
            }
        }
        return functionNameAndReturnValueMap;
    }

    private Map<String, Object> createVariables(MethodExecuteResult methodExecuteResult) {
        return valueResolver.createVariables(
                methodExecuteResult.getMethod(),
                methodExecuteResult.getArgs(),
                methodExecuteResult.getTargetClass(),
                methodExecuteResult.getResult(),
                methodExecuteResult.getErrorMsg());
    }

    private String getDiffFunctionValue(Map<String, Object> variables, String expression) {
        var params = parseDiffFunction(expression);
        if (params.length == 1) {
            return diffParseFunction.diff(valueResolver.resolve(params[0], variables));
        }
        var source = valueResolver.resolve(params[0], variables);
        var target = valueResolver.resolve(params[1], variables);
        return diffParseFunction.diff(source, target);
    }

    private String[] parseDiffFunction(String expression) {
        if (expression.contains(COMMA) && countOccurrences(expression, COMMA) == 1) {
            return expression.split(COMMA);
        }
        return new String[] {expression};
    }

    private boolean shouldRecord(boolean sameDiff) {
        return diffSameWhetherSaveLog || !sameDiff;
    }

    private static int countOccurrences(String src, String find) {
        int count = 0;
        int index = 0;
        while ((index = src.indexOf(find, index)) != -1) {
            index += find.length();
            count++;
        }
        return count;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    public void setLogFunctionParser(LogFunctionParser logFunctionParser) {
        this.logFunctionParser = logFunctionParser;
    }

    public void setDiffParseFunction(DiffParseFunction diffParseFunction) {
        this.diffParseFunction = diffParseFunction;
    }
}
