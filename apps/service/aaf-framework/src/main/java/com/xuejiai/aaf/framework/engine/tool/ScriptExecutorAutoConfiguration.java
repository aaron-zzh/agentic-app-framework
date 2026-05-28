package com.xuejiai.aaf.framework.engine.tool;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * 脚本执行器自动配置。
 *
 * <p>配置项 {@code aaf.script.engine}：
 *
 * <ul>
 *   <li>{@code graalvm}（生产推荐）— GraalVM Polyglot JVM 内沙箱
 *   <li>{@code process}（开发默认）— 子进程执行（python3/node）
 * </ul>
 *
 * <p>默认 {@code process}（开发友好，无预热开销）。生产环境配置 {@code aaf.script.engine=graalvm}。
 */
@Slf4j
@Configuration
public class ScriptExecutorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "aaf.script.engine", havingValue = "graalvm")
    public ScriptExecutor graalVmScriptExecutor() {
        log.info("脚本执行器: GraalVM Polyglot（JVM 内沙箱）");
        return new GraalVmScriptExecutor();
    }

    @Bean
    @ConditionalOnProperty(
            name = "aaf.script.engine",
            havingValue = "process",
            matchIfMissing = true)
    public ScriptExecutor processScriptExecutor(ScriptSandbox sandbox) {
        log.info("脚本执行器: 子进程（开发模式）");
        return new ProcessScriptExecutor(sandbox);
    }
}
