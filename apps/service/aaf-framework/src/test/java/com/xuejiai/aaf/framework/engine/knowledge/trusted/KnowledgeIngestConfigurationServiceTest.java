package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreference;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptTemplateService;
import com.xuejiai.aaf.framework.intelligent.core.prompt.ResolvedPromptTemplate;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestConfigurationServiceTest {

    @Mock private PromptTemplateService promptTemplates;
    @Mock private ModelPreferenceRepository preferenceRepository;
    @Mock private AiModelRepository modelRepository;

    @Test
    void should_resolve_published_prompt_versions_and_system_models() {
        var extractionSystem = prompt("aaf.knowledge.fact-extraction.system", "system", 2);
        var extractionUser = prompt("aaf.knowledge.fact-extraction.user", "user", 3);
        var resolutionSystem = prompt("aaf.knowledge.entity-resolution.system", "resolution", 4);
        var resolutionUser = prompt("aaf.knowledge.entity-resolution.user", "wrapper", 5);
        when(promptTemplates.requirePublished(extractionSystem.name()))
                .thenReturn(extractionSystem);
        when(promptTemplates.requirePublished(extractionUser.name())).thenReturn(extractionUser);
        when(promptTemplates.requirePublished(resolutionSystem.name()))
                .thenReturn(resolutionSystem);
        when(promptTemplates.requirePublished(resolutionUser.name())).thenReturn(resolutionUser);
        when(preferenceRepository.findByScopeAndScopeIdIsNullAndCapability(
                        ModelPreference.SCOPE_SYSTEM,
                        CapabilityRoutingContext.CAP_KNOWLEDGE_EXTRACTION))
                .thenReturn(Optional.of(preference("extract-model")));
        when(preferenceRepository.findByScopeAndScopeIdIsNullAndCapability(
                        ModelPreference.SCOPE_SYSTEM,
                        CapabilityRoutingContext.CAP_KNOWLEDGE_ENTITY_RESOLUTION))
                .thenReturn(Optional.of(preference("resolve-model")));
        when(modelRepository.findByModelIdAndEnabledTrue("extract-model"))
                .thenReturn(Optional.of(chatModel("extract-model")));
        when(modelRepository.findByModelIdAndEnabledTrue("resolve-model"))
                .thenReturn(Optional.of(chatModel("resolve-model")));

        var snapshot =
                new KnowledgeIngestConfigurationService(
                                promptTemplates, preferenceRepository, modelRepository)
                        .resolve();

        assertThat(snapshot.extractionSystem()).isEqualTo(extractionSystem);
        assertThat(snapshot.extractionUser()).isEqualTo(extractionUser);
        assertThat(snapshot.entityResolutionSystem()).isEqualTo(resolutionSystem);
        assertThat(snapshot.entityResolutionUser()).isEqualTo(resolutionUser);
        assertThat(snapshot.extractionOutputContractVersion())
                .isEqualTo(EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION);
        assertThat(snapshot.entityResolutionOutputContractVersion())
                .isEqualTo(EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION);
    }

    private static ResolvedPromptTemplate prompt(String name, String content, int version) {
        return new ResolvedPromptTemplate(name, version, content, "0".repeat(64));
    }

    private static ModelPreference preference(String modelId) {
        var preference = new ModelPreference();
        preference.setScope(ModelPreference.SCOPE_SYSTEM);
        preference.setModelIds(List.of(modelId));
        return preference;
    }

    private static AiModel chatModel(String modelId) {
        var model = new AiModel();
        model.setModelId(modelId);
        model.setCapabilities("CHAT");
        return model;
    }
}
