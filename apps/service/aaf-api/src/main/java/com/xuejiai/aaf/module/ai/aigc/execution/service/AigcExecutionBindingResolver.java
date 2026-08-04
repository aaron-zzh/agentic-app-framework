package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;

import lombok.RequiredArgsConstructor;

/** 按项目上下文选择最具体且优先级最高的已发布执行绑定。 */
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
        return repository
                .findByActionKeyAndStatusOrderByPriorityDesc(actionKey, "published")
                .stream()
                .filter(binding -> matches(binding, project))
                .max(
                        Comparator.comparingInt(
                                        (AigcExecutionBinding binding) ->
                                                specificity(binding, project))
                                .thenComparingInt(AigcExecutionBinding::getPriority));
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

    private int specificity(AigcExecutionBinding binding, AigcProjectView project) {
        var score = 0;
        score += exact(binding.getProjectTypeCode(), project.projectTypeCode());
        score += exact(binding.getDomainExtensionCode(), project.domainExtensionCode());
        score += exact(binding.getProductionMode(), project.productionMode());
        return score;
    }

    private int exact(String expected, String actual) {
        return expected != null && expected.equals(actual) ? 1 : 0;
    }
}
