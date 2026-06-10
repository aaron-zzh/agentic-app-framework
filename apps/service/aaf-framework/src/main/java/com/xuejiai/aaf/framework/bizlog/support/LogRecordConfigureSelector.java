package com.xuejiai.aaf.framework.bizlog.support;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.lang.Nullable;

import com.xuejiai.aaf.framework.bizlog.annotation.EnableLogRecord;
import com.xuejiai.aaf.framework.bizlog.configuration.LogRecordProxyAutoConfiguration;

/**
 * 根据 @EnableLogRecord 的 mode 属性选择导入的配置类。 PROXY 模式（默认）：同时注册 AutoProxyRegistrar（开启 Spring AOP 自动代理）。
 * ASPECTJ 模式：仅注册业务配置，由 AspectJ 织入。
 */
public class LogRecordConfigureSelector extends AdviceModeImportSelector<EnableLogRecord> {

    @Override
    @Nullable
    public String[] selectImports(AdviceMode adviceMode) {
        return switch (adviceMode) {
            case PROXY ->
                    new String[] {
                        AutoProxyRegistrar.class.getName(),
                        LogRecordProxyAutoConfiguration.class.getName()
                    };
            case ASPECTJ -> new String[] {LogRecordProxyAutoConfiguration.class.getName()};
            default -> null;
        };
    }
}
