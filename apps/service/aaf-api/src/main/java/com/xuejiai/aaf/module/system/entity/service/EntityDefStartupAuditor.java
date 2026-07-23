package com.xuejiai.aaf.module.system.entity.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 在 Flyway 完成后审计已持久化的代码实体定义。 */
@Component
@RequiredArgsConstructor
public class EntityDefStartupAuditor implements ApplicationRunner {

    private final EntityDefService entityDefService;

    @Override
    public void run(ApplicationArguments args) {
        entityDefService.auditPersistedCodeDefinitions();
    }
}
