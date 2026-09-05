package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;

import lombok.RequiredArgsConstructor;

/** 仅解析项目快照固定的动作执行绑定版本。 */
@Component
@RequiredArgsConstructor
public class AigcExecutionBindingResolver {

    private final AigcExecutionBindingRepository repository;

    public AigcExecutionBinding resolve(AigcProjectView project, String actionKey) {
        return resolveOptional(project, actionKey)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.NOT_FOUND, "未找到动作执行绑定: " + actionKey));
    }

    public Optional<AigcExecutionBinding> resolveOptional(
            AigcProjectView project, String actionKey) {
        var bindingActionKey =
                "project.cover.generate".equals(actionKey) ? "image.generate" : actionKey;
        var bindingRef =
                project.executionBindings().stream()
                        .filter(reference -> bindingActionKey.equals(reference.actionKey()))
                        .findFirst();
        if (bindingRef.isEmpty()) {
            return Optional.empty();
        }
        var binding = repository.findById(bindingRef.get().executionBindingVersionId());
        if (binding.isEmpty()
                || !"published".equals(binding.get().getStatus())
                || !bindingActionKey.equals(binding.get().getActionKey())
                || !matches(binding.get(), project)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "项目固定的动作执行绑定无效: " + bindingActionKey);
        }
        return binding;
    }

    private boolean matches(AigcExecutionBinding binding, AigcProjectView project) {
        return matches(binding.getProjectTypeCode(), project.projectTypeCode())
                && matches(binding.getDomainExtensionCode(), project.domainExtensionCode())
                && matches(binding.getProductionMode(), project.productionMode())
                && binding.getChannelCode() == null;
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.equals(actual);
    }

}
