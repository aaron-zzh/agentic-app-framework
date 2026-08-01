package com.xuejiai.aaf.framework.engine.tool;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/** 脚本执行器自动配置，仅启用 GraalVM 受限运行时。 */
@Slf4j
@Configuration
public class ScriptExecutorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ScriptExecutor.class)
    public ScriptExecutor graalVmScriptExecutor() {
        log.info("脚本执行器: GraalVM Polyglot（受限运行时）");
        return new GraalVmScriptExecutor();
    }
}
