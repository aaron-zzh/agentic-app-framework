package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;

/** 系统模板持久化安装边界；实现必须按 systemKey + version 幂等，且不得覆盖用户副本。 */
public interface SystemAssistantTemplateInstaller {

    InstallationResult install(AssistantDefinition systemTemplate);

    record InstallationResult(
            String systemKey, AssistantVersion version, InstallationOutcome outcome) {

        public InstallationResult {
            Objects.requireNonNull(systemKey, "systemKey 不能为空");
            Objects.requireNonNull(version, "version 不能为空");
            Objects.requireNonNull(outcome, "outcome 不能为空");
        }
    }

    enum InstallationOutcome {
        CREATED,
        UPGRADED,
        UNCHANGED
    }
}
