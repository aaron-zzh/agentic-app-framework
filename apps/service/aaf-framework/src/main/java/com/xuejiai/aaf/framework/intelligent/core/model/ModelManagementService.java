/**
 * 模型管理服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** 管理 LLM 模型注册、启用/禁用、参数配置。 与 AgentScope 的 Model 集成配合使用——AgentScope 负责实际调用， 本服务负责模型元数据的持久化管理。 */
@Service
@RequiredArgsConstructor
public class ModelManagementService {

    private final AiModelRepository repository;

    /** 注册新模型 */
    @Transactional
    public AiModel register(AiModel model) {
        return repository.save(model);
    }

    /** 启用模型 */
    @Transactional
    public void enable(String modelId) {
        repository
                .findByModelId(modelId)
                .ifPresent(
                        m -> {
                            m.setEnabled(true);
                            repository.save(m);
                        });
    }

    /** 禁用模型 */
    @Transactional
    public void disable(String modelId) {
        repository
                .findByModelId(modelId)
                .ifPresent(
                        m -> {
                            m.setEnabled(false);
                            repository.save(m);
                        });
    }

    /** 获取所有启用的模型 */
    public List<AiModel> listEnabled() {
        return repository.findByEnabledTrueOrderBySortOrder();
    }

    /** 按 ID 查找 */
    public Optional<AiModel> findByModelId(String modelId) {
        return repository.findByModelId(modelId);
    }

    /** 获取降级模型 */
    public Optional<AiModel> getFallback(String modelId) {
        return repository
                .findByModelId(modelId)
                .filter(m -> m.getFallbackModelId() != null)
                .flatMap(m -> repository.findByModelId(m.getFallbackModelId()));
    }
}
