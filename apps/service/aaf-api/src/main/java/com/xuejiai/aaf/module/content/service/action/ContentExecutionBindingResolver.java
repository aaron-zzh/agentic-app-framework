package com.xuejiai.aaf.module.content.service.action;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_ACTION_BINDING_NOT_FOUND;

import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.module.content.domain.ContentExecutionBinding;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.repository.ContentExecutionBindingRepository;

import lombok.RequiredArgsConstructor;

/**
 * 内容动作执行绑定解析器。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class ContentExecutionBindingResolver {

    private final ContentExecutionBindingRepository repository;

    public ContentExecutionBinding resolve(ContentProject project, String actionKey) {
        return resolveOptional(project, actionKey)
                .orElseThrow(() -> exception(CONTENT_ACTION_BINDING_NOT_FOUND, actionKey));
    }

    public Optional<ContentExecutionBinding> resolveOptional(
            ContentProject project, String actionKey) {
        return repository
                .findByActionKeyAndStatusOrderByPriorityDesc(
                        actionKey, ContentConfigStatusEnum.PUBLISHED.getCode())
                .stream()
                .filter(binding -> matches(binding, project))
                .max(
                        Comparator.comparingInt(
                                        (ContentExecutionBinding binding) ->
                                                specificity(binding, project))
                                .thenComparingInt(ContentExecutionBinding::getPriority));
    }

    private boolean matches(ContentExecutionBinding binding, ContentProject project) {
        return matches(binding.getProjectTypeCode(), project.getProjectTypeCode())
                && matches(binding.getDomainExtensionCode(), project.getDomainExtensionCode())
                && matches(binding.getProductionMode(), project.getProductionMode())
                && (binding.getChannelCode() == null
                        || project.getChannels().contains(binding.getChannelCode()));
    }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.equals(actual);
    }

    private int specificity(ContentExecutionBinding binding, ContentProject project) {
        var score = 0;
        score += exact(binding.getProjectTypeCode(), project.getProjectTypeCode());
        score += exact(binding.getDomainExtensionCode(), project.getDomainExtensionCode());
        score += exact(binding.getProductionMode(), project.getProductionMode());
        score +=
                binding.getChannelCode() != null
                                && project.getChannels().contains(binding.getChannelCode())
                        ? 1
                        : 0;
        return score;
    }

    private int exact(String expected, String actual) {
        return expected != null && expected.equals(actual) ? 1 : 0;
    }
}
