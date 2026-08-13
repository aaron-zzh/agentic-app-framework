package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreference;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestConfigurationServiceTest {

    @Mock private SystemConfigService systemConfigService;
    @Mock private ModelPreferenceRepository preferenceRepository;
    @Mock private AiModelRepository modelRepository;

    private KnowledgeIngestConfigurationService service;

    @BeforeEach
    void setUp() {
        service =
                new KnowledgeIngestConfigurationService(
                        systemConfigService, preferenceRepository, modelRepository);
    }

    @Test
    @DisplayName("Given 两类 Prompt 与 SYSTEM 模型偏好 When 解析快照 Then 冻结 Prompt 摘要和首个可用 CHAT 模型")
    void should_resolve_prompts_and_only_system_preferences() {
        when(systemConfigService.getString(SysConfigKeys.Knowledge.EXTRACTION_SYSTEM_PROMPT))
                .thenReturn("  configured extraction  \n");
        when(systemConfigService.getString(SysConfigKeys.Knowledge.ENTITY_RESOLUTION_SYSTEM_PROMPT))
                .thenReturn("  configured resolution  \n");
        when(preferenceRepository.findByScopeAndScopeIdIsNullAndCapability(
                        ModelPreference.SCOPE_SYSTEM,
                        CapabilityRoutingContext.CAP_KNOWLEDGE_EXTRACTION))
                .thenReturn(Optional.of(preference(List.of("disabled", "extract-model"))));
        when(preferenceRepository.findByScopeAndScopeIdIsNullAndCapability(
                        ModelPreference.SCOPE_SYSTEM,
                        CapabilityRoutingContext.CAP_KNOWLEDGE_ENTITY_RESOLUTION))
                .thenReturn(Optional.of(preference(List.of("resolve-model"))));
        when(modelRepository.findByModelIdAndEnabledTrue("disabled")).thenReturn(Optional.empty());
        var extractionModel = chatModel("extract-model");
        var resolutionModel = chatModel("resolve-model");
        when(modelRepository.findByModelIdAndEnabledTrue("extract-model"))
                .thenReturn(Optional.of(extractionModel));
        when(modelRepository.findByModelIdAndEnabledTrue("resolve-model"))
                .thenReturn(Optional.of(resolutionModel));

        var snapshot = service.resolve();

        assertThat(snapshot.extractionSystemPrompt()).isEqualTo("configured extraction");
        assertThat(snapshot.extractionPromptDigest())
                .isEqualTo(TrustedKnowledgeStore.sha256("configured extraction"));
        assertThat(snapshot.entityResolutionSystemPrompt()).isEqualTo("configured resolution");
        assertThat(snapshot.entityResolutionPromptDigest())
                .isEqualTo(TrustedKnowledgeStore.sha256("configured resolution"));
        assertThat(snapshot.entityResolutionOutputContractVersion())
                .isEqualTo(EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION);
        assertThat(snapshot.extractionModelId()).isEqualTo("extract-model");
        assertThat(snapshot.entityResolutionModelId()).isEqualTo("resolve-model");
        verify(preferenceRepository, never())
                .findByScopeAndScopeIdAndCapability(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Given 抽取 Prompt 为空白 When 解析快照 Then 在模型查询前失败")
    void should_reject_blank_extraction_prompt() {
        when(systemConfigService.getString(SysConfigKeys.Knowledge.EXTRACTION_SYSTEM_PROMPT))
                .thenReturn(" \n\t ");

        assertThatThrownBy(service::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("事实抽取系统 Prompt 未配置");
        verify(preferenceRepository, never())
                .findByScopeAndScopeIdIsNullAndCapability(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Given 消歧 Prompt 为空白 When 解析快照 Then 在模型查询前失败")
    void should_reject_blank_resolution_prompt() {
        when(systemConfigService.getString(SysConfigKeys.Knowledge.EXTRACTION_SYSTEM_PROMPT))
                .thenReturn("configured extraction");
        when(systemConfigService.getString(SysConfigKeys.Knowledge.ENTITY_RESOLUTION_SYSTEM_PROMPT))
                .thenReturn(" \n\t ");

        assertThatThrownBy(service::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实体消歧系统 Prompt 未配置");
        verify(preferenceRepository, never())
                .findByScopeAndScopeIdIsNullAndCapability(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Given SYSTEM 偏好只含禁用模型 When 解析快照 Then 拒绝配置")
    void should_reject_disabled_model() {
        stubPromptsAndExtractionPreference("disabled-model");
        when(modelRepository.findByModelIdAndEnabledTrue("disabled-model"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(service::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有已启用的 CHAT 模型");
    }

    @Test
    @DisplayName("Given SYSTEM 偏好模型不支持 CHAT When 解析快照 Then 拒绝配置")
    void should_reject_non_chat_model() {
        stubPromptsAndExtractionPreference("embedding-only");
        var model = mock(AiModel.class);
        when(model.hasCapability(CapabilityRoutingContext.CAP_CHAT)).thenReturn(false);
        when(modelRepository.findByModelIdAndEnabledTrue("embedding-only"))
                .thenReturn(Optional.of(model));

        assertThatThrownBy(service::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有已启用的 CHAT 模型");
    }

    private void stubPromptsAndExtractionPreference(String modelId) {
        when(systemConfigService.getString(SysConfigKeys.Knowledge.EXTRACTION_SYSTEM_PROMPT))
                .thenReturn("configured extraction");
        when(systemConfigService.getString(SysConfigKeys.Knowledge.ENTITY_RESOLUTION_SYSTEM_PROMPT))
                .thenReturn("configured resolution");
        when(preferenceRepository.findByScopeAndScopeIdIsNullAndCapability(
                        ModelPreference.SCOPE_SYSTEM,
                        CapabilityRoutingContext.CAP_KNOWLEDGE_EXTRACTION))
                .thenReturn(Optional.of(preference(List.of(modelId))));
    }

    private ModelPreference preference(List<String> modelIds) {
        var preference = new ModelPreference();
        preference.setScope(ModelPreference.SCOPE_SYSTEM);
        preference.setModelIds(modelIds);
        return preference;
    }

    private AiModel chatModel(String modelId) {
        var model = mock(AiModel.class);
        when(model.hasCapability(CapabilityRoutingContext.CAP_CHAT)).thenReturn(true);
        when(model.getModelId()).thenReturn(modelId);
        return model;
    }
}
