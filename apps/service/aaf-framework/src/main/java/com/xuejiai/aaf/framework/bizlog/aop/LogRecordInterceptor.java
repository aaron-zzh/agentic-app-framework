package com.xuejiai.aaf.framework.bizlog.aop;

import static com.xuejiai.aaf.framework.bizlog.service.ILogRecordPerformanceMonitor.*;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.bizlog.beans.CodeVariableType;
import com.xuejiai.aaf.framework.bizlog.beans.LogRecord;
import com.xuejiai.aaf.framework.bizlog.beans.LogRecordOps;
import com.xuejiai.aaf.framework.bizlog.beans.MethodExecuteResult;
import com.xuejiai.aaf.framework.bizlog.context.LogRecordContext;
import com.xuejiai.aaf.framework.bizlog.parse.LogFunctionParser;
import com.xuejiai.aaf.framework.bizlog.parse.LogRecordValueParser;
import com.xuejiai.aaf.framework.bizlog.service.IFunctionService;
import com.xuejiai.aaf.framework.bizlog.service.ILogRecordPerformanceMonitor;
import com.xuejiai.aaf.framework.bizlog.service.ILogRecordService;
import com.xuejiai.aaf.framework.bizlog.service.IOperatorGetService;
import com.xuejiai.aaf.framework.bizlog.service.impl.DiffParseFunction;

import lombok.extern.slf4j.Slf4j;

/**
 * @LogRecord AOP 核心拦截器。
 *
 * <p>执行流程： 1. 方法执行前：解析 executeBefore=true 的自定义函数（保存更新前的旧值） 2. 执行目标方法 3. 方法执行后：解析 SpEL 模板，组装
 * LogRecord，调用 ILogRecordService 持久化
 */
@Slf4j
public class LogRecordInterceptor extends LogRecordValueParser
        implements MethodInterceptor, Serializable, SmartInitializingSingleton {

    private LogRecordOperationSource logRecordOperationSource;
    private String tenantId;
    private ILogRecordService bizLogService;
    private IOperatorGetService operatorGetService;
    private ILogRecordPerformanceMonitor logRecordPerformanceMonitor;
    private boolean joinTransaction;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return execute(
                invocation,
                invocation.getThis(),
                invocation.getMethod(),
                invocation.getArguments());
    }

    private Object execute(MethodInvocation invoker, Object target, Method method, Object[] args)
            throws Throwable {
        // 已是 AOP 代理对象时不再重复拦截
        if (AopUtils.isAopProxy(target)) return invoker.proceed();

        var stopWatch = new StopWatch(MONITOR_NAME);
        stopWatch.start(MONITOR_TASK_BEFORE_EXECUTE);

        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
        Object ret = null;
        var methodExecuteResult = new MethodExecuteResult(method, args, targetClass);
        LogRecordContext.putEmptySpan();
        Collection<LogRecordOps> operations = new ArrayList<>();
        Map<String, String> functionNameAndReturnMap = new HashMap<>();

        try {
            operations = logRecordOperationSource.computeLogRecordOperations(method, targetClass);
            var spElTemplates = getBeforeExecuteFunctionTemplate(operations);
            functionNameAndReturnMap =
                    processBeforeExecuteFunctionTemplate(spElTemplates, targetClass, method, args);
        } catch (Exception e) {
            log.error("LogRecord before-function parse exception", e);
        } finally {
            stopWatch.stop();
        }

        try {
            ret = invoker.proceed();
            methodExecuteResult.setResult(ret);
            methodExecuteResult.setSuccess(true);
        } catch (Exception e) {
            methodExecuteResult.setSuccess(false);
            methodExecuteResult.setThrowable(e);
            methodExecuteResult.setErrorMsg(e.getMessage());
        }

        stopWatch.start(MONITOR_TASK_AFTER_EXECUTE);
        try {
            if (!CollectionUtils.isEmpty(operations)) {
                recordExecute(methodExecuteResult, functionNameAndReturnMap, operations);
            }
        } catch (Exception t) {
            log.error("LogRecord parse exception", t);
            if (joinTransaction) throw t;
        } finally {
            LogRecordContext.clear();
            stopWatch.stop();
            try {
                logRecordPerformanceMonitor.print(stopWatch);
            } catch (Exception e) {
                log.error("LogRecord performance monitor exception", e);
            }
        }

        if (methodExecuteResult.getThrowable() != null) {
            throw methodExecuteResult.getThrowable();
        }
        return ret;
    }

    private List<String> getBeforeExecuteFunctionTemplate(Collection<LogRecordOps> operations) {
        List<String> spElTemplates = new ArrayList<>();
        for (LogRecordOps operation : operations) {
            var templates = getSpElTemplates(operation, operation.getSuccessLogTemplate());
            if (!CollectionUtils.isEmpty(templates)) spElTemplates.addAll(templates);
        }
        return spElTemplates;
    }

    private void recordExecute(
            MethodExecuteResult methodExecuteResult,
            Map<String, String> functionNameAndReturnMap,
            Collection<LogRecordOps> operations) {
        for (LogRecordOps operation : operations) {
            try {
                if (!StringUtils.hasText(operation.getSuccessLogTemplate())
                        && !StringUtils.hasText(operation.getFailLogTemplate())) continue;
                if (exitsCondition(methodExecuteResult, functionNameAndReturnMap, operation))
                    continue;
                if (!methodExecuteResult.isSuccess()) {
                    failRecordExecute(methodExecuteResult, functionNameAndReturnMap, operation);
                } else {
                    successRecordExecute(methodExecuteResult, functionNameAndReturnMap, operation);
                }
            } catch (Exception t) {
                log.error("LogRecord execute exception", t);
                if (joinTransaction) throw t;
            }
        }
    }

    private void successRecordExecute(
            MethodExecuteResult methodExecuteResult,
            Map<String, String> functionNameAndReturnMap,
            LogRecordOps operation) {
        String action;
        boolean flag = true;
        if (StringUtils.hasText(operation.getIsSuccess())) {
            String condition =
                    singleProcessTemplate(
                            methodExecuteResult,
                            operation.getIsSuccess(),
                            functionNameAndReturnMap);
            // 防御 null：condition 解析失败时默认走 success 分支
            if (condition != null && condition.trim().equalsIgnoreCase("false")) {
                action = operation.getFailLogTemplate();
                flag = false;
            } else {
                action = operation.getSuccessLogTemplate();
            }
        } else {
            action = operation.getSuccessLogTemplate();
        }
        if (!StringUtils.hasText(action)) return;
        var spElTemplates = getSpElTemplates(operation, action);
        String operatorIdFromService =
                getOperatorIdFromServiceAndPutTemplate(operation, spElTemplates);
        Map<String, String> expressionValues =
                processTemplate(spElTemplates, methodExecuteResult, functionNameAndReturnMap);
        saveLog(
                methodExecuteResult.getMethod(),
                !flag,
                operation,
                operatorIdFromService,
                action,
                expressionValues);
    }

    private void failRecordExecute(
            MethodExecuteResult methodExecuteResult,
            Map<String, String> functionNameAndReturnMap,
            LogRecordOps operation) {
        if (!StringUtils.hasText(operation.getFailLogTemplate())) return;
        String action = operation.getFailLogTemplate();
        var spElTemplates = getSpElTemplates(operation, action);
        String operatorIdFromService =
                getOperatorIdFromServiceAndPutTemplate(operation, spElTemplates);
        Map<String, String> expressionValues =
                processTemplate(spElTemplates, methodExecuteResult, functionNameAndReturnMap);
        saveLog(
                methodExecuteResult.getMethod(),
                true,
                operation,
                operatorIdFromService,
                action,
                expressionValues);
    }

    private boolean exitsCondition(
            MethodExecuteResult methodExecuteResult,
            Map<String, String> functionNameAndReturnMap,
            LogRecordOps operation) {
        if (!StringUtils.hasText(operation.getCondition())) return false;
        String condition =
                singleProcessTemplate(
                        methodExecuteResult, operation.getCondition(), functionNameAndReturnMap);
        return condition.endsWith("false");
    }

    private void saveLog(
            Method method,
            boolean fail,
            LogRecordOps operation,
            String operatorIdFromService,
            String action,
            Map<String, String> expressionValues) {
        String actionValue = expressionValues.get(action);
        if (!StringUtils.hasText(actionValue)) return;
        // diff 未变化时，模板原样返回（未经替换），视为无变更不记录
        if (!diffSameWhetherSaveLog && action.contains("#") && Objects.equals(action, actionValue))
            return;

        var logRecord =
                LogRecord.builder()
                        .tenant(tenantId)
                        .type(expressionValues.get(operation.getType()))
                        .bizNo(expressionValues.get(operation.getBizNo()))
                        .operator(
                                getRealOperatorId(
                                        operation, operatorIdFromService, expressionValues))
                        .subType(expressionValues.get(operation.getSubType()))
                        .extra(expressionValues.get(operation.getExtra()))
                        .codeVariable(
                                Map.of(
                                        CodeVariableType.ClassName, method.getDeclaringClass(),
                                        CodeVariableType.MethodName, method.getName()))
                        .action(actionValue)
                        .fail(fail)
                        .createTime(Instant.now())
                        .build();

        bizLogService.record(logRecord);
    }

    private List<String> getSpElTemplates(LogRecordOps operation, String... actions) {
        List<String> templates =
                new ArrayList<>(
                        List.of(
                                operation.getType(), operation.getBizNo(),
                                operation.getSubType(), operation.getExtra()));
        templates.addAll(Arrays.asList(actions));
        return templates;
    }

    private String getRealOperatorId(
            LogRecordOps operation,
            String operatorIdFromService,
            Map<String, String> expressionValues) {
        return StringUtils.hasText(operatorIdFromService)
                ? operatorIdFromService
                : expressionValues.get(operation.getOperatorId());
    }

    private String getOperatorIdFromServiceAndPutTemplate(
            LogRecordOps operation, List<String> spElTemplates) {
        if (!StringUtils.hasText(operation.getOperatorId())) {
            String realOperatorId = operatorGetService.getUser().getOperatorId();
            if (!StringUtils.hasText(realOperatorId)) {
                throw new IllegalArgumentException("[LogRecord] operator is null");
            }
            return realOperatorId;
        }
        spElTemplates.add(operation.getOperatorId());
        return "";
    }

    public void setLogRecordOperationSource(LogRecordOperationSource source) {
        this.logRecordOperationSource = source;
    }

    public void setTenant(String tenant) {
        this.tenantId = tenant;
    }

    public void setLogRecordService(ILogRecordService bizLogService) {
        this.bizLogService = bizLogService;
    }

    public void setLogRecordPerformanceMonitor(ILogRecordPerformanceMonitor monitor) {
        this.logRecordPerformanceMonitor = monitor;
    }

    public void setJoinTransaction(boolean joinTransaction) {
        this.joinTransaction = joinTransaction;
    }

    public void setDiffSameWhetherSaveLog(boolean diffLog) {
        this.diffSameWhetherSaveLog = diffLog;
    }

    @Override
    public void afterSingletonsInstantiated() {
        bizLogService = beanFactory.getBean(ILogRecordService.class);
        operatorGetService = beanFactory.getBean(IOperatorGetService.class);
        setLogFunctionParser(new LogFunctionParser(beanFactory.getBean(IFunctionService.class)));
        setDiffParseFunction(beanFactory.getBean(DiffParseFunction.class));
    }
}
