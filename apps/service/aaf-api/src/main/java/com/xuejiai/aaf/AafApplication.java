package com.xuejiai.aaf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.xuejiai.aaf.framework.bizlog.annotation.EnableLogRecord;

@EnableAsync
@EnableScheduling
@EnableLogRecord(tenant = "aaf")
@SpringBootApplication
@EnableSpringDataWebSupport(
        pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class AafApplication {
    public static void main(String[] args) {
        // Reactor ThreadLocal 自动传播由 spring.reactor.context-propagation=auto 开启（见 application.yaml）
        SpringApplication.run(AafApplication.class, args);
    }
}
