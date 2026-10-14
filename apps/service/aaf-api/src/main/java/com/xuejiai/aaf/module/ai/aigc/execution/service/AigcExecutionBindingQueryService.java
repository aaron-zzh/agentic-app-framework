package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionBindingApi;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;

import lombok.RequiredArgsConstructor;

/** {@link AigcExecutionBindingApi} 的实现：按优先级读取已发布的执行绑定。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcExecutionBindingQueryService implements AigcExecutionBindingApi {

    private static final String PUBLISHED = "published";

    private final AigcExecutionBindingRepository bindingRepository;

    @Override
    public Optional<Long> findPublishedBindingVersionId(String actionKey) {
        return bindingRepository
                .findByActionKeyAndStatusOrderByPriorityDesc(actionKey, PUBLISHED)
                .stream()
                .findFirst()
                .map(AigcExecutionBinding::getId);
    }
}
