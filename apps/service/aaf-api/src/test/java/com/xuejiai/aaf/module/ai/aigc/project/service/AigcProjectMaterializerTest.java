package com.xuejiai.aaf.module.ai.aigc.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.brand.api.AigcBrandApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintProcessPolicy;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedConfiguration;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedObjectSpec;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcBlueprintSourceType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectChannelRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocumentRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectObjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectProfileRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRelationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRevisionRepository;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcProjectMaterializerTest extends BaseMockitoUnitTest {

    @Mock private AigcConfigurationApi configurationApi;
    @Mock private AigcBrandApi brandApi;
    @Mock private AigcProjectRepository projectRepository;
    @Mock private ProjectBlueprintSnapshotService snapshotService;
    @Mock private AigcProjectProfileRefRepository profileRefRepository;
    @Mock private AigcProjectChannelRefRepository channelRefRepository;
    @Mock private AigcProjectDocumentRefRepository documentRefRepository;
    @Mock private AigcProjectObjectRepository objectRepository;
    @Mock private AigcProjectRelationRepository relationRepository;
    @Mock private AigcProjectRevisionRepository revisionRepository;
    @Mock private DocumentVersionApi documentVersionApi;
    @Mock private AigcMediaApi mediaApi;
    @Mock private AigcProjectMediaRefRepository mediaRefRepository;
    @Mock private OperatorContext operatorContext;
    @InjectMocks private AigcProjectMaterializer materializer;

    @Test
    @DisplayName("Given resolved object instanceNo When 物化项目 Then 原值持久化到项目对象")
    void should_materialize_resolved_instance_no() {
        // 准备参数
        var objectSpec =
                new AigcResolvedObjectSpec(
                        "shot.03",
                        "shot",
                        3,
                        "shot",
                        "镜头 03",
                        null,
                        30,
                        "REQUIRED",
                        "DELIVERABLE",
                        "MEDIA_VERSION",
                        "MANUAL",
                        "shot.generate",
                        null,
                        "{}",
                        null);
        var initialization = AigcProjectInitializationSpec.empty();
        var processPolicy = new AigcBlueprintProcessPolicy(false, "OPTIONAL", List.of(), List.of());
        var resolved =
                new AigcResolvedConfiguration(
                        "1.0.0",
                        3L,
                        "story",
                        "1.0.0",
                        List.of(),
                        List.of(),
                        "standard",
                        initialization,
                        List.of(),
                        List.of(objectSpec),
                        List.of(),
                        List.of(),
                        List.of(),
                        processPolicy);
        when(configurationApi.resolve(any())).thenReturn(resolved);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(brandApi.requireVersions(any(), any())).thenReturn(List.of());
        when(projectRepository.save(any(AigcProject.class)))
                .thenAnswer(
                        invocation -> {
                            var project = invocation.getArgument(0, AigcProject.class);
                            project.setId(1L);
                            return project;
                        });
        when(snapshotService.createInitialSnapshot(
                        any(AigcProject.class), any(), any(), any(AigcResolvedConfiguration.class)))
                .thenAnswer(
                        invocation -> {
                            var snapshot = new AigcProjectConfigSnapshot();
                            snapshot.setId(2L);
                            return snapshot;
                        });
        when(objectRepository.save(any(AigcProjectObject.class)))
                .thenAnswer(
                        invocation -> {
                            var object = invocation.getArgument(0, AigcProjectObject.class);
                            object.setId(11L);
                            return object;
                        });
        var command =
                new AigcProjectMaterializeCommand(
                        9L,
                        "系列项目",
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        Map.of(),
                        null,
                        null,
                        AigcBlueprintSourceType.CATALOG,
                        "blueprint:3@1.0.0",
                        "narrative_series",
                        3L,
                        null,
                        List.of(),
                        List.of(),
                        "standard",
                        List.of(),
                        AigcProjectCoverMode.NONE,
                        null,
                        null,
                        null,
                        null);

        // 调用
        materializer.materialize(command);

        // 断言
        var captor = ArgumentCaptor.forClass(AigcProjectObject.class);
        org.mockito.Mockito.verify(objectRepository).save(captor.capture());
        assertThat(captor.getValue().getInstanceNo()).isEqualTo(3);
        assertThat(captor.getValue().getDefaultActionKey()).isEqualTo("shot.generate");
    }
}
