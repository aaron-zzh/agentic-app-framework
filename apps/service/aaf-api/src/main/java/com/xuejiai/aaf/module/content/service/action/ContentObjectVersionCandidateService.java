package com.xuejiai.aaf.module.content.service.action;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_OBJECT_NOT_FOUND;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.content.ContentObjectVersionStatusEnum;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;
import com.xuejiai.aaf.module.content.domain.ContentObjectVersion;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.repository.ContentObjectVersionRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectObjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * 候选对象版本写入服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ContentObjectVersionCandidateService {

    private final ContentProjectObjectRepository objectRepository;
    private final ContentObjectVersionRepository versionRepository;

    @Transactional
    public ContentObjectVersion createCandidate(
            ContentProject project,
            ContentExecutionRun run,
            Map<String, Object> contentPayload,
            List<String> assetRefs,
            String summary) {
        var object =
                objectRepository
                        .findLockedById(run.getObjectId())
                        .filter(candidate -> project.getId().equals(candidate.getProjectId()))
                        .orElseThrow(() -> exception(CONTENT_OBJECT_NOT_FOUND));
        var version = new ContentObjectVersion();
        version.setOrgId(project.getOrgId());
        version.setWorkspaceId(project.getWorkspaceId());
        version.setOwnerId(project.getOwnerId());
        version.setProjectId(project.getId());
        version.setObjectId(object.getId());
        version.setVersionNo(
                versionRepository
                                .findFirstByObjectIdOrderByVersionNoDesc(object.getId())
                                .map(ContentObjectVersion::getVersionNo)
                                .orElse(0)
                        + 1);
        version.setStatus(ContentObjectVersionStatusEnum.CANDIDATE.getCode());
        version.setContentPayload(contentPayload);
        version.setAssetRefs(assetRefs == null ? List.of() : List.copyOf(assetRefs));
        version.setExecutionRunId(run.getId());
        version.setSummary(summary);
        return versionRepository.save(version);
    }
}
