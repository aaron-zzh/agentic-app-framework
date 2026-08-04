package com.xuejiai.aaf.module.ai.aigc.project.api;

public interface AigcProjectApi {

    AigcProjectView materialize(AigcProjectMaterializeCommand command);

    AigcProjectView requireProject(Long projectId);

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
}
