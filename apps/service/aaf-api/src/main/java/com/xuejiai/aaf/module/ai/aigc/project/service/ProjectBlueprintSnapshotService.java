package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * 两种合法 Blueprint 来源的 insert-only 持久化服务。
 *
 * <p>快照只允许新增；结构变化必须通过新的 revisionNo 表达新记录。两种来源统一经过 {@link BlueprintSnapshotNormalizer}、{@link
 * BlueprintSnapshotValidator} 与 {@link BlueprintSnapshotFreezer}。
 */
@Service
@RequiredArgsConstructor
public class ProjectBlueprintSnapshotService {

    private final BlueprintSnapshotNormalizer normalizer;
    private final BlueprintSnapshotValidator validator;
    private final BlueprintSnapshotFreezer freezer;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;

    /** 校验并插入项目首条（revisionNo=1）BlueprintSnapshot。 */
    @Transactional
    public AigcProjectConfigSnapshot createInitialSnapshot(
            AigcProject project,
            AigcProjectBlueprintSnapshotPayload payload,
            String expectedSourceRef,
            String blueprintVersion,
            Map<String, Object> compatibilityResult) {
        var normalized = normalizer.normalize(payload);
        validator.validate(normalized);
        if (!expectedSourceRef.equals(normalized.sourceRef())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot sourceRef 与已确认来源不一致");
        }
        if (snapshotRepository
                .findFirstByProjectIdOrderByRevisionNoDesc(project.getId())
                .isPresent()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目已存在结构基线快照，不能重复创建");
        }
        return freezer.freezeInitial(project, normalized, blueprintVersion, compatibilityResult);
    }
}
