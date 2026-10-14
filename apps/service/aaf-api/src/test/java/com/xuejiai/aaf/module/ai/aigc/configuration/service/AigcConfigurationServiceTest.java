package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationResolveCommand;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcChannelSpecRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectBlueprintRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypeRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionBindingApi;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcConfigurationServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcProjectTypeRepository projectTypeRepository;
    @Mock private AigcProjectBlueprintRepository blueprintRepository;
    @Mock private AigcChannelSpecRepository channelSpecRepository;
    @Mock private AigcExecutionBindingApi executionBindingApi;
    @Mock private AigcCatalogBlueprintCompiler blueprintCompiler;
    @InjectMocks private AigcConfigurationService service;

    @Test
    @DisplayName("Given 两个槽位各一个动作且仅一个标记默认 When 解析 Then 仅默认动作写入对应对象")
    void should_resolve_default_action_key_by_target_template_only() {
        // 准备参数
        var projectType = new AigcProjectType();
        projectType.setId(1L);
        projectType.setCode("content");
        projectType.setDefinitionVersion("1.0.0");
        projectType.setDefaultChannels(List.of());
        var blueprint = new AigcProjectBlueprint();
        blueprint.setId(2L);
        blueprint.setCode("story");
        blueprint.setBlueprintVersion("1.0.0");
        blueprint.setProjectTypeCode("content");
        blueprint.setProductionMode("standard");
        blueprint.setStatus(AigcConfigStatus.PUBLISHED.getCode());
        blueprint.setSlotTemplateSpec(
                Map.of(
                        "slotTemplates",
                        List.of(
                                Map.of(
                                        "templateKey",
                                        "shot",
                                        "stableKeyPattern",
                                        "shot.{instanceNo}",
                                        "objectType",
                                        "shot",
                                        "semanticRole",
                                        "DELIVERABLE",
                                        "outputContract",
                                        "MEDIA_VERSION"),
                                Map.of(
                                        "templateKey",
                                        "copy",
                                        "stableKeyPattern",
                                        "copy.{instanceNo}",
                                        "objectType",
                                        "copy",
                                        "semanticRole",
                                        "DELIVERABLE",
                                        "outputContract",
                                        "DOCUMENT_VERSION"))));
        blueprint.setRelationSpec(Map.of());
        blueprint.setActionSpec(
                Map.of(
                        "actions",
                        List.of(
                                Map.of(
                                        "actionKey",
                                        "shot.generate",
                                        "targetTemplateKey",
                                        "shot",
                                        "isDefault",
                                        true),
                                Map.of(
                                        "actionKey",
                                        "copy.rewrite",
                                        "targetTemplateKey",
                                        "copy",
                                        "isDefault",
                                        false))));
        blueprint.setDeliverableSpec(Map.of());
        blueprint.setProcessPolicy(Map.of("publicationPolicy", "OPTIONAL"));
        when(blueprintCompiler.compile(blueprint)).thenCallRealMethod();
        when(projectTypeRepository.findFirstByCodeAndStatusAndDeletedFalseOrderByIdDesc(
                        "content", "published"))
                .thenReturn(Optional.of(projectType));
        when(blueprintRepository.findByIdAndStatusAndDeletedFalse(2L, "published"))
                .thenReturn(Optional.of(blueprint));
        when(executionBindingApi.findPublishedBindingVersionId("shot.generate"))
                .thenReturn(Optional.of(10L));
        when(executionBindingApi.findPublishedBindingVersionId("copy.rewrite"))
                .thenReturn(Optional.of(11L));
        var command = new AigcConfigurationResolveCommand("content", 2L, List.of(), null, null);

        // 调用
        var resolved = service.resolve(command);

        // 断言
        var shotObject =
                resolved.resolvedObjects().stream()
                        .filter(object -> "shot".equals(object.blueprintTemplateKey()))
                        .findFirst()
                        .orElseThrow();
        var copyObject =
                resolved.resolvedObjects().stream()
                        .filter(object -> "copy".equals(object.blueprintTemplateKey()))
                        .findFirst()
                        .orElseThrow();
        assertThat(shotObject.defaultActionKey()).isEqualTo("shot.generate");
        assertThat(copyObject.defaultActionKey()).isNull();
    }
}
