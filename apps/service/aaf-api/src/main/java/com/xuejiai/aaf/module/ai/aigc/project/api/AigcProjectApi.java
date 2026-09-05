package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AigcProjectApi {

    AigcProjectMaterializeView materialize(AigcProjectMaterializeCommand command);

    AigcProjectView requireProject(Long projectId);

    AigcProjectView lockForCreativeMutation(Long projectId, Long userId);

    AigcProjectView lockForCreativeMutation(
            Long projectId, Long userId, Integer expectedProjectVersion);

    AigcProjectView lockForWorkMutation(
            Long projectId, Long userId, Integer expectedProjectVersion);

    AigcProjectExecutionReservationView requireBoundExecution(
            Long reservationId, Long executionSubmissionId, Long rootExecutionRunId);

    void markCoverExecutionStarted(Long projectId, Long executionRunId);

    void markCoverGenerationFailed(Long projectId);

    void markCoverExecutionTerminal(
            Long projectId, Long executionRunId, AigcProjectCoverStatus status);

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

    AigcProjectExecutionReservationView reserveExecution(
            AigcProjectExecutionReservationCommand command);

    AigcProjectExecutionReservationView bindExecution(
            AigcProjectExecutionReservationBindCommand command);

    AigcProjectExecutionReservationView releaseExecution(
            AigcProjectExecutionReservationReleaseCommand command);

    AigcProjectObjectView updateObjectContract(AigcProjectObjectContractCommand command);

    AigcProjectObjectView removeObject(AigcProjectObjectRemoveCommand command);

    AigcProjectMediaRefView attachMedia(AigcProjectMediaRefCommand command);

    void detachMedia(Long projectId, Long projectMediaRefId, Integer expectedProjectVersion);

    AigcObjectVersionView appendCandidate(AigcObjectVersionCandidateCommand command);

    AigcObjectVersionView requireObjectVersion(Long projectId, Long objectId, Long objectVersionId);

    AigcProjectMediaRefView requireAdoptedMediaVersion(
            Long projectId, Long objectId, Long mediaVersionId);

    AigcObjectVersionComparisonView compareObjectVersions(
            Long projectId, Long objectId, Long leftObjectVersionId, Long rightObjectVersionId);

    AigcObjectVersionView adoptVersion(AigcObjectVersionAdoptCommand command);

    AigcObjectVersionView rejectVersion(AigcObjectVersionRejectCommand command);

    AigcDeliverableSetCompletionView evaluateDeliverableSet(
            AigcDeliverableSetEvaluateCommand command);

    AigcObjectVersionView freezeDeliverableSetManifest(
            AigcDeliverableSetManifestFreezeCommand command);

    AigcReviewView submitReview(AigcReviewSubmitCommand command);

    AigcReviewView approveReview(AigcReviewDecisionCommand command);

    AigcReviewView returnReview(AigcReviewDecisionCommand command);

    List<AigcReviewView> reviews(Long projectId);

    AigcApprovedManifestView requireApprovedManifest(
            Long projectId, Long deliverableSetObjectId, Long manifestObjectVersionId);

    AigcCompletionEvaluationView completionEvidence(Long projectId);

    AigcProjectView complete(AigcProjectLifecycleCommand command);

    AigcProjectView archive(AigcProjectLifecycleCommand command);

    record DocumentProjectReference(Long documentId, Long projectId, String projectName) {}
}
