package com.xuejiai.aaf.module.ai.model.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.module.ai.model.vo.AiModelCreateDTO;
import com.xuejiai.aaf.module.ai.model.vo.AiModelUpdateDTO;
import com.xuejiai.aaf.module.ai.model.vo.AiModelVO;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;

import lombok.RequiredArgsConstructor;

/**
 * AI 模型管理 Service。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository repository;
    private final DynamicChatClientFactory chatClientFactory;

    /**
     * 创建模型
     *
     * @param dto 创建请求
     * @return 模型信息
     */
    @Transactional
    public AiModelVO create(AiModelCreateDTO dto) {
        if (repository.findByModelId(dto.modelId()).isPresent()) {
            throw exception(ErrorCodeConstants.AI_MODEL_ID_EXISTS);
        }
        var model = new AiModel();
        model.setModelId(dto.modelId());
        model.setDisplayName(dto.displayName());
        model.setProvider(dto.provider());
        model.setProviderType(dto.providerType());
        model.setModelName(dto.modelName());
        model.setBaseUrl(dto.baseUrl());
        model.setApiKey(dto.apiKey());
        model.setCapabilities(dto.capabilities() != null ? dto.capabilities() : "CHAT");
        model.setTemperature(dto.temperature());
        model.setMaxTokens(dto.maxTokens());
        model.setContextWindow(dto.contextWindow());
        model.setInputPricePerK(dto.inputPricePerK());
        model.setOutputPricePerK(dto.outputPricePerK());
        model.setFallbackModelId(dto.fallbackModelId());
        model.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 100);
        model.setRemark(dto.remark());
        return toVO(repository.save(model));
    }

    /**
     * 更新模型
     *
     * @param id 模型编号
     * @param dto 更新请求
     * @return 更新后的模型信息
     */
    @Transactional
    public AiModelVO update(Long id, AiModelUpdateDTO dto) {
        var model = getEntity(id);
        if (dto.displayName() != null) model.setDisplayName(dto.displayName());
        if (dto.baseUrl() != null) model.setBaseUrl(dto.baseUrl());
        if (dto.apiKey() != null) model.setApiKey(dto.apiKey().isEmpty() ? null : dto.apiKey());
        if (dto.capabilities() != null) model.setCapabilities(dto.capabilities());
        if (dto.temperature() != null) model.setTemperature(dto.temperature());
        if (dto.maxTokens() != null) model.setMaxTokens(dto.maxTokens());
        if (dto.contextWindow() != null) model.setContextWindow(dto.contextWindow());
        if (dto.inputPricePerK() != null) model.setInputPricePerK(dto.inputPricePerK());
        if (dto.outputPricePerK() != null) model.setOutputPricePerK(dto.outputPricePerK());
        if (dto.enabled() != null) model.setEnabled(dto.enabled());
        if (dto.fallbackModelId() != null) model.setFallbackModelId(dto.fallbackModelId());
        if (dto.sortOrder() != null) model.setSortOrder(dto.sortOrder());
        if (dto.remark() != null) model.setRemark(dto.remark());
        var saved = repository.save(model);
        chatClientFactory.evict(saved.getModelId());
        return toVO(saved);
    }

    /**
     * 删除模型（软删除）
     *
     * @param id 模型编号
     */
    @Transactional
    public void delete(Long id) {
        var model = getEntity(id);
        model.setDeleted(true);
        repository.save(model);
        chatClientFactory.evict(model.getModelId());
    }

    /**
     * 切换模型启用状态
     *
     * @param id 模型编号
     * @param enabled 是否启用
     * @return 更新后的模型信息
     */
    @Transactional
    public AiModelVO toggleEnabled(Long id, boolean enabled) {
        var model = getEntity(id);
        model.setEnabled(enabled);
        var saved = repository.save(model);
        chatClientFactory.evict(saved.getModelId());
        return toVO(saved);
    }

    /**
     * 获取模型详情
     *
     * @param id 模型编号
     * @return 模型信息
     */
    public AiModelVO getById(Long id) {
        return toVO(getEntity(id));
    }

    /**
     * 分页查询模型列表
     *
     * @param provider 厂商筛选（可选）
     * @param enabled 启用状态筛选（可选）
     * @param pageable 分页参数
     * @return 模型分页结果
     */
    public PageResult<AiModelVO> list(String provider, Boolean enabled, Pageable pageable) {
        var page = repository.findByFilter(provider, enabled, pageable);
        return new PageResult<>(page.map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 获取所有已启用模型列表（下拉选择用）
     *
     * @return 已启用模型列表
     */
    public List<AiModelVO> listEnabled() {
        return repository.findByEnabledTrueOrderBySortOrder().stream().map(this::toVO).toList();
    }

    private AiModel getEntity(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> exception(ErrorCodeConstants.AI_MODEL_NOT_FOUND));
    }

    private AiModelVO toVO(AiModel m) {
        return new AiModelVO(
                m.getId(),
                m.getModelId(),
                m.getDisplayName(),
                m.getProvider(),
                m.getProviderType(),
                m.getModelName(),
                m.getBaseUrl(),
                m.getApiKey() != null && !m.getApiKey().isBlank(),
                m.getCapabilities(),
                m.getTemperature(),
                m.getMaxTokens(),
                m.getContextWindow(),
                m.getInputPricePerK(),
                m.getOutputPricePerK(),
                m.getEnabled(),
                m.getFallbackModelId(),
                m.getSortOrder(),
                m.getRemark(),
                m.getCreateTime(),
                m.getUpdateTime());
    }
}
