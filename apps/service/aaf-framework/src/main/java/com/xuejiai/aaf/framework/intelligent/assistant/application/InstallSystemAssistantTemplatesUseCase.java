package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.TemplateOwnership;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateContributor;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateInstaller;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateInstaller.InstallationResult;

/** 聚合系统模板贡献并通过持久化端口幂等安装。 */
public final class InstallSystemAssistantTemplatesUseCase {

    private final List<SystemAssistantTemplateContributor> contributors;
    private final SystemAssistantTemplateInstaller installer;

    public InstallSystemAssistantTemplatesUseCase(
            List<SystemAssistantTemplateContributor> contributors,
            SystemAssistantTemplateInstaller installer) {
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors 不能为空"));
        this.installer = Objects.requireNonNull(installer, "installer 不能为空");
    }

    public List<InstallationResult> install() {
        var results = new ArrayList<InstallationResult>();
        var systemKeys = new java.util.HashSet<String>();
        for (var contributor : contributors) {
            for (var template : contributor.templates()) {
                if (template.ownership() != TemplateOwnership.SYSTEM_MANAGED) {
                    throw new IllegalArgumentException("系统模板贡献只能包含 SYSTEM_MANAGED 定义");
                }
                if (!systemKeys.add(template.systemKey())) {
                    throw new IllegalStateException("重复的系统 Assistant key: " + template.systemKey());
                }
                results.add(installer.install(template));
            }
        }
        return List.copyOf(results);
    }
}
