package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import org.springframework.beans.factory.SmartInitializingSingleton;

import com.xuejiai.aaf.framework.intelligent.assistant.application.InstallSystemAssistantTemplatesUseCase;

/** Spring 启动完成后调用通用幂等模板安装用例。 */
final class SystemAssistantTemplateBootstrap implements SmartInitializingSingleton {

    private final InstallSystemAssistantTemplatesUseCase useCase;

    SystemAssistantTemplateBootstrap(InstallSystemAssistantTemplatesUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase 不能为空");
    }

    @Override
    public void afterSingletonsInstantiated() {
        useCase.install();
    }
}
