package com.xuejiai.aaf.framework.engine.workflow.config;

import org.flowable.common.engine.impl.cfg.IdGenerator;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flowable 引擎配置增强。
 *
 * <ul>
 *   <li>自定义 ID 生成器（UUID 去横线）
 *   <li>历史记录级别 FULL
 *   <li>异步执行器配置（支持定时器边界事件）
 * </ul>
 */
@Configuration
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration>
            processEngineConfigurer() {
        return config -> {
            // 历史记录级别：FULL，记录所有流程变量和表单数据
            config.setHistoryLevel(
                    org.flowable.common.engine.impl.history.HistoryLevel.FULL);

            // 自定义 ID 生成器
            config.setIdGenerator(uuidIdGenerator());

            // 异步执行器配置（支持定时器边界事件、异步任务）
            config.setAsyncExecutorActivate(true);
            config.setAsyncExecutorCorePoolSize(4);
            config.setAsyncExecutorMaxPoolSize(16);
            config.setAsyncExecutorMaxAsyncJobsDuePerAcquisition(10);
        };
    }

    /** UUID 去横线的 ID 生成器 */
    private IdGenerator uuidIdGenerator() {
        return () -> java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
