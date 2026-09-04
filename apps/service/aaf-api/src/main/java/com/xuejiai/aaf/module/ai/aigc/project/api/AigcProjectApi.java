package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AigcProjectApi {

    AigcProjectMaterializeView materialize(AigcProjectMaterializeCommand command);

    AigcProjectView requireProject(Long projectId);

    Set<Long> findLinkedDocumentIds(Long ownerId, Long orgId, Long workspaceId, Long projectId);

    List<DocumentProjectReference> findDocumentProjects(
            Long ownerId, Long orgId, Long workspaceId, Collection<Long> documentIds);

    void lockForGeneratedResource(Long projectId, Long userId);

    void lockForCoverMutation(Long projectId, Long userId);

    boolean applyGeneratedCover(
            Long projectId,
            Long mediaVersionId,
            Long expectedCoverMediaVersionId,
            Long executionRunId);

    AigcProjectGraphView getGraph(Long projectId);

    AigcProjectObjectView appendObject(AigcProjectObjectCommand command);

    AigcProjectMediaRefView attachMedia(AigcProjectMediaRefCommand command);

    void detachMedia(Long projectId, Long projectMediaRefId, Integer expectedProjectVersion);

    AigcObjectVersionView appendCandidate(AigcObjectVersionCandidateCommand command);

    AigcObjectVersionView requireObjectVersion(Long projectId, Long objectId, Long objectVersionId);

    AigcProjectMediaRefView requireAdoptedMediaVersion(
            Long projectId, Long objectId, Long mediaVersionId);

    AigcObjectVersionView adoptVersion(AigcObjectVersionAdoptCommand command);

    AigcObjectVersionView rejectVersion(
            Long projectId, Long objectId, Long objectVersionId, Integer expectedProjectVersion);

    AigcProjectView submitReview(Long projectId, Integer expectedVersion);

    AigcProjectView approveReview(AigcReviewApproveCommand command);

    AigcProjectView complete(Long projectId, Integer expectedVersion);

    AigcProjectView archive(Long projectId, Integer expectedVersion);

    record DocumentProjectReference(Long documentId, Long projectId, String projectName) {}
}
