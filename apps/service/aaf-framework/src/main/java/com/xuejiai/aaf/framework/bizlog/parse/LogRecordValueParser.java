package com.xuejiai.aaf.framework.bizlog.parse;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.expression.EvaluationContext;

import com.xuejiai.aaf.framework.bizlog.beans.MethodExecuteResult;
import com.xuejiai.aaf.framework.bizlog.service.impl.DiffParseFunction;

/**
 * 操作日志 SpEL 模板解析基类。
 *
 * <p>模板语法：
 *
 * <ul>
 *   <li>{@code {{#param}}} — 普通 SpEL 表达式
 *   <li>{@code {functionName{#param}}} — 调用注册的 IParseFunction
 *   <li>{@code {_DIFF{#newObj}}} 或 {@code {_DIFF{#oldObj, #newObj}}} — diff 函数
 * </ul>
 */
public class LogRecordValueParser implements BeanFactoryAware {

    /** 匹配 {functionName{expression}} 格式，functionName 可为空（普通 SpEL）。 */
    private static final Pattern PATTERN = Pattern.compile("\\{\\s*(\\w*)\\s*\\{(.*?)}}");

    public static final String COMMA = ",";

    private final LogRecordExpressionEvaluator expressionEvaluator =
            new LogRecordExpressionEvaluator();
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
        Map<String, String> expressionValues = new HashMap<>();
        EvaluationContext evaluationContext =
                expressionEvaluator.createEvaluationContext(
                        methodExecuteResult.getMethod(), methodExecuteResult.getArgs(),
                        methodExecuteResult.getTargetClass(), methodExecuteResult.getResult(),
                        methodExecuteResult.getErrorMsg(), beanFactory);

        for (String expressionTemplate : templates) {
            if (!expressionTemplate.contains("{")) {
                expressionValues.put(expressionTemplate, expressionTemplate);
                continue;
            }
            Matcher matcher = PATTERN.matcher(expressionTemplate);
            var parsedStr = new StringBuffer();
            var elementKey =
                    new AnnotatedElementKey(
                            methodExecuteResult.getMethod(), methodExecuteResult.getTargetClass());
            boolean sameDiff = false;
            while (matcher.find()) {
                String expression = matcher.group(2);
                String functionName = matcher.group(1);
                if (DiffParseFunction.diffFunctionName.equals(functionName)) {
                    expression = getDiffFunctionValue(evaluationContext, elementKey, expression);
                    sameDiff = Objects.equals("", expression);
                } else {
                    Object value =
                            expressionEvaluator.parseExpression(
                                    expression, elementKey, evaluationContext);
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
        Map<String, String> functionNameAndReturnValueMap = new HashMap<>();
        EvaluationContext evaluationContext =
                expressionEvaluator.createEvaluationContext(
                        method, args, targetClass, null, null, beanFactory);

        for (String expressionTemplate : templates) {
            if (!expressionTemplate.contains("{")) continue;
            Matcher matcher = PATTERN.matcher(expressionTemplate);
            var elementKey = new AnnotatedElementKey(method, targetClass);
            while (matcher.find()) {
                String expression = matcher.group(2);
                if (expression.contains("#_ret") || expression.contains("#_errorMsg")) continue;
                String functionName = matcher.group(1);
                if (logFunctionParser.beforeFunction(functionName)) {
                    Object value =
                            expressionEvaluator.parseExpression(
                                    expression, elementKey, evaluationContext);
                    String functionReturnValue =
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

    private String getDiffFunctionValue(
            EvaluationContext evaluationContext,
            AnnotatedElementKey elementKey,
            String expression) {
        String[] params = parseDiffFunction(expression);
        if (params.length == 1) {
            Object targetObj =
                    expressionEvaluator.parseExpression(params[0], elementKey, evaluationContext);
            return diffParseFunction.diff(targetObj);
        } else {
            Object sourceObj =
                    expressionEvaluator.parseExpression(params[0], elementKey, evaluationContext);
            Object targetObj =
                    expressionEvaluator.parseExpression(params[1], elementKey, evaluationContext);
            return diffParseFunction.diff(sourceObj, targetObj);
        }
    }

    private String[] parseDiffFunction(String expression) {
        if (expression.contains(COMMA) && countOccurrences(expression, COMMA) == 1) {
            return expression.split(COMMA);
        }
        return new String[] {expression};
    }

    private boolean shouldRecord(boolean sameDiff) {
        if (diffSameWhetherSaveLog) return true;
        return !sameDiff;
    }

    private static int countOccurrences(String src, String find) {
        int count = 0, index = 0;
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
