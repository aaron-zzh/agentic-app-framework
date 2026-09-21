package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;

import lombok.RequiredArgsConstructor;

/** 两种 Blueprint 来源共用的配置快照冻结器；只执行 insert，不提供历史快照更新。 */
@Component
@RequiredArgsConstructor
public class BlueprintSnapshotFreezer {

    private final BlueprintSnapshotValidator validator;
    private final AigcProjectConfigSnapshotRepository repository;

    public AigcProjectConfigSnapshot freezeInitial(
            AigcProject project,
            AigcProjectBlueprintSnapshotPayload payload,
            String blueprintVersion,
            Map<String, Object> compatibilityResult) {
        var snapshot = new AigcProjectConfigSnapshot();
        snapshot.setOrgId(project.getOrgId());
        snapshot.setWorkspaceId(project.getWorkspaceId());
        snapshot.setOwnerId(project.getOwnerId());
        snapshot.setProjectId(project.getId());
        snapshot.setRevisionNo(1);
        snapshot.setSourceType(payload.sourceType().name());
        snapshot.setSourceRef(payload.sourceRef());
        snapshot.setSnapshotHash(validator.canonicalHash(payload));
        snapshot.setProjectTypeVersion(payload.projectSpecification().projectTypeVersion());
        snapshot.setBlueprintVersion(blueprintVersion);
        snapshot.setChannelVersions(payload.projectSpecification().channelSpecVersionIds());
        snapshot.setExecutionBindingVersions(payload.projectSpecification().executionBindings());
        snapshot.setCompatibilityResult(
                compatibilityResult == null ? Map.of("compatible", true) : compatibilityResult);
        snapshot.setSnapshot(payload);
        return repository.save(snapshot);
    }
}
