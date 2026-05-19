package com.xuejiai.aaf.framework.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** 自定义业务指标注册。 */
@Configuration
public class MetricsConfig {

    /** 任务执行计数器。 */
    @Bean
    public Counter taskExecutionsCounter(MeterRegistry registry) {
        return Counter.builder("aaf_task_executions_total")
                .description("任务执行总数")
                .register(registry);
    }

    /** 消息发送计数器。 */
    @Bean
    public Counter messageSentCounter(MeterRegistry registry) {
        return Counter.builder("aaf_message_sent_total")
                .description("消息发送总数")
                .register(registry);
    }
}
