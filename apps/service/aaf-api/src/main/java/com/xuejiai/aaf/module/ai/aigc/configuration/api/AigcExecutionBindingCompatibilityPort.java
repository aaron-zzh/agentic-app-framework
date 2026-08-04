package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;
import java.util.Set;

/** ProjectTypePackage 发布时校验 ExecutionBinding 的跨子域端口。 */
public interface AigcExecutionBindingCompatibilityPort {

    Validation validate(
            List<Long> bindingIds,
            String projectTypeCode,
            String domainExtensionCode,
            String productionMode,
            List<String> channelCodes);

    record Validation(int requestedCount, int compatibleCount, Set<String> coveredActionKeys) {

        public Validation {
            if (requestedCount < 0 || compatibleCount < 0 || compatibleCount > requestedCount) {
                throw new IllegalArgumentException("ExecutionBinding 兼容性计数无效");
            }
            coveredActionKeys =
                    coveredActionKeys == null ? Set.of() : Set.copyOf(coveredActionKeys);
        }

        public boolean allCompatible() {
            return requestedCount == compatibleCount;
        }

        public boolean covers(List<String> requiredActionKeys) {
            return requiredActionKeys == null || coveredActionKeys.containsAll(requiredActionKeys);
        }
    }
}
