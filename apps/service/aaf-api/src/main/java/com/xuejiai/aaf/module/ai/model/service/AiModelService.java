package com.xuejiai.aaf.module.ai.model.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProvider;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.ai.model.vo.AiModelCreateDTO;
import com.xuejiai.aaf.module.ai.model.vo.AiModelImportResultVO;
import com.xuejiai.aaf.module.ai.model.vo.AiModelUpdateDTO;
import com.xuejiai.aaf.module.ai.model.vo.AiModelVO;
import com.xuejiai.aaf.module.ai.model.vo.PublicModelPricingVO;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * AI 模型管理 Service。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository repository;
    private final AiModelProviderRepository providerRepository;
    private final DynamicChatClientFactory chatClientFactory;
    private final SystemConfigService configService;

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
        model.setProviderType(AiModelProviderType.valueOf(dto.providerType()));
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

    public List<AiModelVO> listEnabledByCapability(String capability) {
        return listEnabledByCapabilities(List.of(capability));
    }

    /**
     * 用户侧模型公开定价列表（已乘加价倍率，以积分为单位）。
     *
     * <p>所有已启用模型均返回，各计费类型规则：
     *
     * <ul>
     *   <li>quotaType=0（TOKEN）：inputCreditPerK = inputPricePerK × 1000 × YUAN_TO_CREDIT × markup /
     *       1000
     *   <li>quotaType=1（PER_USE）：creditPerUse = modelPrice × YUAN_TO_CREDIT × markup
     *   <li>quotaType=2（PER_SEC）：creditPerSec = modelPrice × YUAN_TO_CREDIT × markup
     *   <li>quotaType=3（PER_UNIT）：creditPerUnit = modelPrice × YUAN_TO_CREDIT × markup
     * </ul>
     */
    public List<PublicModelPricingVO> listPublicPricing() {
        int markup = configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 5);
        return repository.findByEnabledTrueOrderBySortOrder().stream()
                .map(m -> toPricingVO(m, markup))
                .toList();
    }

    private PublicModelPricingVO toPricingVO(AiModel m, int markup) {
        short quotaType = m.getQuotaType() != null ? m.getQuotaType() : 0;
        Long inputCreditPerK = null;
        Long outputCreditPerK = null;
        Long creditPerUse = null;
        Long creditPerSec = null;
        Long creditPerUnit = null;

        switch (quotaType) {
            case 1 -> // PER_USE
                    creditPerUse = AiCreditGuard.calcPerUseCost(m.getModelPrice(), markup);
            case 2 -> // PER_SEC
                    creditPerSec =
                            m.getModelPrice() != null
                                    ? Math.max(
                                            1,
                                            Math.round(
                                                    m.getModelPrice().doubleValue()
                                                            * AiCreditGuard.YUAN_TO_CREDIT
                                                            * markup))
                                    : null;
            case 3 -> // PER_UNIT
                    creditPerUnit =
                            m.getModelPrice() != null
                                    ? Math.max(
                                            1,
                                            Math.round(
                                                    m.getModelPrice().doubleValue()
                                                            * AiCreditGuard.YUAN_TO_CREDIT
                                                            * markup))
                                    : null;
            default -> { // TOKEN
                if (m.getInputPricePerK() != null) {
                    // 每千 token：inputPricePerK（元/千token）× YUAN_TO_CREDIT × markup
                    inputCreditPerK =
                            Math.max(
                                    1,
                                    Math.round(
                                            m.getInputPricePerK().doubleValue()
                                                    * AiCreditGuard.YUAN_TO_CREDIT
                                                    * markup));
                }
                if (m.getOutputPricePerK() != null) {
                    outputCreditPerK =
                            Math.max(
                                    1,
                                    Math.round(
                                            m.getOutputPricePerK().doubleValue()
                                                    * AiCreditGuard.YUAN_TO_CREDIT
                                                    * markup));
                }
            }
        }

        return new PublicModelPricingVO(
                m.getModelId(),
                m.getDisplayName(),
                m.getProvider(),
                m.getCapabilities(),
                quotaType,
                inputCreditPerK,
                outputCreditPerK,
                creditPerUse,
                creditPerSec,
                creditPerUnit,
                markup);
    }

    public List<AiModelVO> listEnabledByCapabilities(List<String> capabilities) {
        return repository.findByEnabledTrueOrderBySortOrder().stream()
                .filter(
                        m ->
                                m.getCapabilities() != null
                                        && capabilities.stream()
                                                .anyMatch(
                                                        c ->
                                                                m.getCapabilities()
                                                                        .contains(c.trim())))
                .map(this::toVO)
                .toList();
    }

    /**
     * 从第三方模型价格 JSON 导入模型。按 enable_groups 分组，每组取 sort_order 最靠前的 10 个模型后去重入库。
     *
     * @param file JSON 文件，支持根节点 data 数组或直接数组
     * @param providerCode 供应商编码
     * @param providerName 供应商名称
     * @param baseUrl 供应商默认 API 地址
     * @return 导入统计
     */
    @Transactional
    public AiModelImportResultVO importJson(
            MultipartFile file, String providerCode, String providerName, String baseUrl) {
        var normalizedProviderCode = normalizeProviderCode(providerCode);
        var provider = upsertProvider(normalizedProviderCode, providerName, baseUrl);
        var items = readImportItems(file);
        var groups = groupItems(items);
        var selected = selectTopModels(groups);
        var modelIds = new ArrayList<String>();
        var createdCount = 0;
        var updatedCount = 0;

        for (var item : selected) {
            var modelName = text(item, "model_name");
            if (modelName == null) {
                continue;
            }
            var modelId = normalizedProviderCode + ":" + modelName;
            var model = repository.findByModelId(modelId).orElseGet(AiModel::new);
            var isNew = model.getId() == null;
            fillImportedModel(model, item, modelId, modelName, normalizedProviderCode, provider);
            repository.save(model);
            chatClientFactory.evict(modelId);
            modelIds.add(modelId);
            if (isNew) {
                createdCount++;
            } else {
                updatedCount++;
            }
        }

        var groupCounts = new LinkedHashMap<String, Integer>();
        groups.forEach(
                (group, groupItems) -> groupCounts.put(group, Math.min(groupItems.size(), 10)));
        return new AiModelImportResultVO(
                items.size(),
                selected.size(),
                createdCount,
                updatedCount,
                normalizedProviderCode,
                groupCounts,
                modelIds);
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
                m.getProviderType() != null ? m.getProviderType().getCode() : null,
                m.getModelName(),
                m.getBaseUrl(),
                m.getApiKey() != null && !m.getApiKey().isBlank(),
                m.getCapabilities(),
                m.getTemperature(),
                m.getMaxTokens(),
                m.getContextWindow(),
                m.getInputPricePerK(),
                m.getOutputPricePerK(),
                m.getModelPrice(),
                m.getQuotaType(),
                m.getEnabled(),
                m.getFallbackModelId(),
                m.getSortOrder(),
                m.getRemark(),
                m.getImageConfig() != null ? m.getImageConfig().toString() : null,
                m.getVideoConfig(),
                m.getCreateTime(),
                m.getUpdateTime());
    }

    private AiModelProvider upsertProvider(
            String providerCode, String providerName, String baseUrl) {
        var provider =
                providerRepository.findByProviderCode(providerCode).orElseGet(AiModelProvider::new);
        if (provider.getProviderCode() == null) {
            provider.setProviderCode(providerCode);
            provider.setProviderName(
                    providerName != null && !providerName.isBlank() ? providerName : "第三方聚合");
            provider.setProviderType(AiModelProviderType.OPENAI_COMPAT);
        }
        if (providerName != null && !providerName.isBlank()) {
            provider.setProviderName(providerName);
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            provider.setBaseUrl(baseUrl);
        }
        provider.setEnabled(true);
        return providerRepository.save(provider);
    }

    private List<JsonNode> readImportItems(MultipartFile file) {
        try {
            var root = JsonUtils.readTree(new String(file.getInputStream().readAllBytes()));
            var data = root.isArray() ? root : root.get("data");
            if (data == null || !data.isArray()) {
                throw exception(ErrorCodeConstants.AI_MODEL_IMPORT_INVALID);
            }
            var items = new ArrayList<JsonNode>();
            data.forEach(items::add);
            return items;
        } catch (IOException e) {
            throw exception(ErrorCodeConstants.AI_MODEL_IMPORT_INVALID);
        }
    }

    private Map<String, List<JsonNode>> groupItems(List<JsonNode> items) {
        var groups = new LinkedHashMap<String, List<JsonNode>>();
        for (var item : items) {
            var modelName = text(item, "model_name");
            if (modelName == null) {
                continue;
            }
            var enableGroups = item.get("enable_groups");
            if (enableGroups == null || !enableGroups.isArray() || enableGroups.isEmpty()) {
                groups.computeIfAbsent("default", ignored -> new ArrayList<>()).add(item);
                continue;
            }
            for (var group : enableGroups) {
                var groupName = group.asText();
                if (!groupName.isBlank()) {
                    groups.computeIfAbsent(groupName, ignored -> new ArrayList<>()).add(item);
                }
            }
        }
        return groups;
    }

    private List<JsonNode> selectTopModels(Map<String, List<JsonNode>> groups) {
        var selectedModelNames = new LinkedHashSet<String>();
        var selected = new ArrayList<JsonNode>();
        for (var groupItems : groups.values()) {
            groupItems.stream()
                    .sorted(Comparator.comparingInt(item -> intValue(item, "sort_order", 100)))
                    .limit(10)
                    .forEach(
                            item -> {
                                var modelName = text(item, "model_name");
                                if (modelName != null && selectedModelNames.add(modelName)) {
                                    selected.add(item);
                                }
                            });
        }
        return selected;
    }

    private void fillImportedModel(
            AiModel model,
            JsonNode item,
            String modelId,
            String modelName,
            String providerCode,
            AiModelProvider provider) {
        model.setModelId(modelId);
        model.setDisplayName(modelName);
        model.setProvider(providerCode);
        model.setProviderConfig(provider);
        model.setProviderType(resolveProviderType(item));
        model.setModelName(modelName);
        model.setBaseUrl(provider.getBaseUrl());
        model.setCapabilities(resolveCapabilities(item));
        model.setSortOrder(intValue(item, "sort_order", 100));
        model.setModelRatio(decimal(item, "model_ratio", BigDecimal.ONE));
        model.setCompletionRatio(decimal(item, "completion_ratio", BigDecimal.ONE));
        model.setCacheRatio(decimal(item, "cache_ratio", null));
        model.setAudioRatio(decimal(item, "audio_ratio", null));
        model.setAudioCompletionRatio(decimal(item, "audio_completion_ratio", null));
        model.setStepRatios(copy(item.get("step_ratios")));
        model.setTags(text(item, "tags"));
        model.setModelType(text(item, "model_type"));
        model.setSupportedEndpoints(
                copy(firstPresent(item, "supported_endpoint_types", "supported_endpoints")));
        model.setQuotaType((short) intValue(item, "quota_type", 0));
        model.setModelPrice(decimal(item, "model_price", null));
        model.setEnableGroups(copy(item.get("enable_groups")));
        model.setVendorId(longValue(item, "vendor_id"));
        model.setVendorName(text(item, "vendor_name"));
        model.setVendorIcon(text(item, "vendor_icon"));
        model.setIcon(text(item, "icon"));
        model.setDescription(text(item, "description"));
        model.setOfficialPrice(copy(item.get("official_price")));
        model.setEnabled(true);
        model.setRemark("从模型价格 JSON 导入");
    }

    private AiModelProviderType resolveProviderType(JsonNode item) {
        var endpoints = firstPresent(item, "supported_endpoint_types", "supported_endpoints");
        if (endpoints != null && endpoints.isArray()) {
            for (var endpoint : endpoints) {
                if ("anthropic".equalsIgnoreCase(endpoint.asText())) {
                    return AiModelProviderType.ANTHROPIC;
                }
            }
        }
        return AiModelProviderType.OPENAI_COMPAT;
    }

    private String resolveCapabilities(JsonNode item) {
        var values = new LinkedHashSet<String>();
        var tags = text(item, "tags");
        var modelType = text(item, "model_type");
        var endpoints = firstPresent(item, "supported_endpoint_types", "supported_endpoints");
        if (contains(tags, "识图")) {
            values.add("VISION");
        }
        if (contains(tags, "绘画") || contains(modelType, "图像")) {
            values.add("IMAGE_GEN");
        }
        if (contains(modelType, "音") || contains(modelType, "语音")) {
            values.add("AUDIO");
        }
        if (endpoints != null && endpoints.isArray()) {
            for (var endpoint : endpoints) {
                if (contains(endpoint.asText(), "image")) {
                    values.add("IMAGE_GEN");
                }
                if (contains(endpoint.asText(), "embedding")) {
                    values.add("EMBEDDING");
                }
            }
        }
        if (values.isEmpty() || contains(tags, "对话") || contains(modelType, "文本")) {
            values.add("CHAT");
        }
        return String.join(",", values);
    }

    private boolean contains(String value, String part) {
        return value != null && value.toLowerCase().contains(part.toLowerCase());
    }

    private String normalizeProviderCode(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return "third_party";
        }
        return providerCode.trim().toLowerCase();
    }

    private JsonNode firstPresent(JsonNode node, String... fieldNames) {
        for (var fieldName : fieldNames) {
            var value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String copy(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return JsonUtils.toJsonString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode node, String fieldName) {
        var value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        var text = value.asText();
        return text.isBlank() ? null : text;
    }

    private int intValue(JsonNode node, String fieldName, int defaultValue) {
        var value = node.get(fieldName);
        return value != null && value.isNumber() ? value.asInt() : defaultValue;
    }

    private Long longValue(JsonNode node, String fieldName) {
        var value = node.get(fieldName);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    private BigDecimal decimal(JsonNode node, String fieldName, BigDecimal defaultValue) {
        var value = node.get(fieldName);
        if (value == null || value.isNull() || !value.isNumber()) {
            return defaultValue;
        }
        return value.decimalValue();
    }
}
