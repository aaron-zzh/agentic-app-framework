package com.xuejiai.aaf.framework.engine.tool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * 脚本执行器自动配置——优先 GraalVM Polyglot，不可用时降级到子进程。
 */
@Slf4j
@Configuration
public class ScriptExecutorAutoConfiguration {

    @Bean
    public ScriptExecutor scriptExecutor(ScriptSandbox sandbox) {
        if (isGraalVmAvailable()) {
            log.info("脚本执行器: GraalVM Polyglot（JVM 内沙箱）");
            return new GraalVmScriptExecutor();
        }
        log.info("脚本执行器: 子进程降级（GraalVM Polyglot 不可用）");
        return new ProcessScriptExecutor(sandbox);
    }

    private boolean isGraalVmAvailable() {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
