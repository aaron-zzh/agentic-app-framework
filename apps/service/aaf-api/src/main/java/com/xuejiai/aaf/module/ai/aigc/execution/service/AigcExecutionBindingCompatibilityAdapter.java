package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingCompatibilityPort;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;

import lombok.RequiredArgsConstructor;

/** 仅在 execution 真理源内读取并校验版本化执行绑定。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcExecutionBindingCompatibilityAdapter
        implements AigcExecutionBindingCompatibilityPort {

    private final AigcExecutionBindingRepository repository;

    @Override
    public Validation validate(
            List<Long> bindingIds,
            String projectTypeCode,
            String domainExtensionCode,
            String productionMode,
            List<String> channelCodes) {
        var requested =
                bindingIds == null ? List.<Long>of() : bindingIds.stream().distinct().toList();
        var channels = channelCodes == null ? List.<String>of() : channelCodes;
        var compatible =
                repository.findAllById(requested).stream()
                        .filter(
                                binding ->
                                        compatible(
                                                binding,
                                                projectTypeCode,
                                                domainExtensionCode,
                                                productionMode,
                                                channels))
                        .toList();
        var coveredActions =
                compatible.stream()
                        .map(AigcExecutionBinding::getActionKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableSet());
        return new Validation(requested.size(), compatible.size(), coveredActions);
    }

    private boolean compatible(
            AigcExecutionBinding binding,
            String projectTypeCode,
            String domainExtensionCode,
            String productionMode,
            List<String> channelCodes) {
        return !Boolean.TRUE.equals(binding.getDeleted())
                && "published".equals(binding.getStatus())
                && matches(binding.getProjectTypeCode(), projectTypeCode)
                && matches(binding.getDomainExtensionCode(), domainExtensionCode)
                && matches(binding.getProductionMode(), productionMode)
                && (binding.getChannelCode() == null
                        || channelCodes.contains(binding.getChannelCode()));
    }

    private boolean matches(String constraint, String actual) {
        return constraint == null || Objects.equals(constraint, actual);
    }
}
