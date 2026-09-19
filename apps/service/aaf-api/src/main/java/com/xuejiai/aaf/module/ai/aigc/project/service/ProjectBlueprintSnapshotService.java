package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedConfiguration;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * CATALOG BlueprintSnapshot 的 insert-only 持久化服务。
 *
 * <p>快照只允许新增；结构变化必须通过新的 revisionNo 表达新记录，不提供任何更新已存在快照的方法。持久化前统一调用 {@link
 * BlueprintSnapshotValidator} 完成语义校验，确保 CATALOG 与后续 ASSISTANT_GENERATED（#11321）来源共用同一份合同。
 */
@Service
@RequiredArgsConstructor
public class ProjectBlueprintSnapshotService {

    private final BlueprintSnapshotValidator validator;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;

    /**
     * 校验并插入项目首条（revisionNo=1）BlueprintSnapshot。
     *
     * <p>要求项目当前没有任何既有快照，保证同一项目结构基线只创建一次；后续结构变更走新的 revision，不修改本条记录。
     */
    @Transactional
    public AigcProjectConfigSnapshot createInitialSnapshot(
            AigcProject project,
            AigcProjectBlueprintSnapshotPayload payload,
            String sourceRef,
            AigcResolvedConfiguration resolved) {
        validator.validate(payload);
        if (!sourceRef.equals(payload.sourceRef())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "CATALOG sourceRef 与已发布 Blueprint 不一致");
        }
        if (snapshotRepository
                .findFirstByProjectIdOrderByRevisionNoDesc(project.getId())
                .isPresent()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目已存在结构基线快照，不能重复创建");
        }
        var snapshot = new AigcProjectConfigSnapshot();
        snapshot.setOrgId(project.getOrgId());
        snapshot.setWorkspaceId(project.getWorkspaceId());
        snapshot.setOwnerId(project.getOwnerId());
        snapshot.setProjectId(project.getId());
        snapshot.setRevisionNo(1);
        snapshot.setSourceType(payload.sourceType().name());
        snapshot.setSourceRef(sourceRef);
        snapshot.setSnapshotHash(validator.canonicalHash(payload));
        snapshot.setProjectTypeVersion(resolved.projectTypeVersion());
        snapshot.setBlueprintVersion(resolved.blueprintVersion());
        snapshot.setChannelVersions(resolved.channelSpecVersionIds());
        snapshot.setExecutionBindingVersions(resolved.executionBindings());
        snapshot.setCompatibilityResult(
                Map.of(
                        "compatible", true,
                        "blueprintId", resolved.blueprintVersionId(),
                        "blueprintVersion", resolved.blueprintVersion()));
        snapshot.setSnapshot(payload);
        return snapshotRepository.save(snapshot);
    }
}
