package com.xuejiai.aaf.framework.bizlog.configuration;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.bizlog.annotation.EnableLogRecord;
import com.xuejiai.aaf.framework.bizlog.aop.BeanFactoryLogRecordAdvisor;
import com.xuejiai.aaf.framework.bizlog.aop.LogRecordInterceptor;
import com.xuejiai.aaf.framework.bizlog.aop.LogRecordOperationSource;
import com.xuejiai.aaf.framework.bizlog.diff.DefaultDiffItemsToLogContentService;
import com.xuejiai.aaf.framework.bizlog.diff.IDiffItemsToLogContentService;
import com.xuejiai.aaf.framework.bizlog.service.*;
import com.xuejiai.aaf.framework.bizlog.service.impl.*;

import lombok.extern.slf4j.Slf4j;

/**
 * 操作日志 AOP 自动配置类，由 @EnableLogRecord 通过 LogRecordConfigureSelector 导入。
 *
 * <p>所有 Bean 均提供 @ConditionalOnMissingBean，业务方可通过注册同类型 Bean 覆盖默认行为。 最关键的扩展点：
 *
 * <ul>
 *   <li>{@link ILogRecordService} — 替换为业务持久化实现（写库或发消息）
 *   <li>{@link IOperatorGetService} — 替换为从 SecurityContext 取操作人
 *   <li>{@link IParseFunction} — 注册自定义函数（ID→名称转换等）
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(LogRecordProperties.class)
@Slf4j
public class LogRecordProxyAutoConfiguration implements ImportAware {

    private AnnotationAttributes enableLogRecord;

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public LogRecordOperationSource logRecordOperationSource() {
        return new LogRecordOperationSource();
    }

    @Bean
    @ConditionalOnMissingBean(IFunctionService.class)
    public IFunctionService functionService(ParseFunctionFactory parseFunctionFactory) {
        return new DefaultFunctionServiceImpl(parseFunctionFactory);
    }

    @Bean
    public ParseFunctionFactory parseFunctionFactory(
            @Autowired List<IParseFunction> parseFunctions) {
        return new ParseFunctionFactory(parseFunctions);
    }

    @Bean
    @ConditionalOnMissingBean(IParseFunction.class)
    public DefaultParseFunction parseFunction() {
        return new DefaultParseFunction();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @DependsOn("logRecordInterceptor")
    public BeanFactoryLogRecordAdvisor logRecordAdvisor(LogRecordInterceptor logRecordInterceptor) {
        var advisor = new BeanFactoryLogRecordAdvisor();
        advisor.setLogRecordOperationSource(logRecordOperationSource());
        advisor.setAdvice(logRecordInterceptor);
        if (enableLogRecord != null) {
            advisor.setOrder(enableLogRecord.getNumber("order"));
        }
        return advisor;
    }

    @Bean
    @ConditionalOnMissingBean(ILogRecordPerformanceMonitor.class)
    public ILogRecordPerformanceMonitor logRecordPerformanceMonitor() {
        return new DefaultLogRecordPerformanceMonitor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public LogRecordInterceptor logRecordInterceptor(LogRecordProperties logRecordProperties) {
        var interceptor = new LogRecordInterceptor();
        interceptor.setLogRecordOperationSource(logRecordOperationSource());
        if (enableLogRecord != null) {
            interceptor.setTenant(enableLogRecord.getString("tenant"));
            interceptor.setJoinTransaction(enableLogRecord.getBoolean("joinTransaction"));
        }
        interceptor.setDiffSameWhetherSaveLog(logRecordProperties.getDiffLog());
        interceptor.setLogRecordPerformanceMonitor(logRecordPerformanceMonitor());
        return interceptor;
    }

    @Bean
    public DiffParseFunction diffParseFunction(
            IDiffItemsToLogContentService diffItemsToLogContentService,
            LogRecordProperties logRecordProperties) {
        var diffParseFunction = new DiffParseFunction();
        diffParseFunction.setDiffItemsToLogContentService(diffItemsToLogContentService);
        // LocalDateTime 默认使用 equals 比较，避免反射比较字段导致误报
        diffParseFunction.addUseEqualsClass(LocalDateTime.class);
        if (StringUtils.hasText(logRecordProperties.getUseEqualsMethod())) {
            diffParseFunction.addUseEqualsClass(
                    Arrays.asList(logRecordProperties.getUseEqualsMethod().split(",")));
        }
        return diffParseFunction;
    }

    @Bean
    @ConditionalOnMissingBean(IDiffItemsToLogContentService.class)
    @Role(BeanDefinition.ROLE_APPLICATION)
    public IDiffItemsToLogContentService diffItemsToLogContentService(
            LogRecordProperties logRecordProperties) {
        return new DefaultDiffItemsToLogContentService(logRecordProperties);
    }

    @Bean
    @ConditionalOnMissingBean(IOperatorGetService.class)
    @Role(BeanDefinition.ROLE_APPLICATION)
    public IOperatorGetService operatorGetService() {
        return new DefaultOperatorGetServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean(ILogRecordService.class)
    @Role(BeanDefinition.ROLE_APPLICATION)
    public ILogRecordService recordService() {
        return new DefaultLogRecordServiceImpl();
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.enableLogRecord =
                AnnotationAttributes.fromMap(
                        importMetadata.getAnnotationAttributes(
                                EnableLogRecord.class.getName(), false));
        if (this.enableLogRecord == null) {
            log.debug("@EnableLogRecord 未找到，使用默认配置");
        }
    }
}
