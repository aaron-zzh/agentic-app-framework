package com.xuejiai.aaf.module.ai.aigc.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetManifestFreezeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewDecisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.CompletionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcObjectVersion;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcObjectVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectExecutionReservationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectExecutionReservationTargetRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectObjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRevisionRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcProjectDeliveryServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcProjectRepository projectRepository;
    @Mock private AigcProjectObjectRepository objectRepository;
    @Mock private AigcObjectVersionRepository versionRepository;
    @Mock private AigcProjectRevisionRepository revisionRepository;
    @Mock private AigcProjectConfigSnapshotRepository snapshotRepository;
    @Mock private AigcProjectMediaRefRepository mediaRefRepository;
    @Mock private AigcProjectExecutionReservationRepository reservationRepository;
    @Mock private AigcProjectExecutionReservationTargetRepository reservationTargetRepository;
    @Mock private AigcMediaApi mediaApi;
    @Mock private CompletionEvidencePort completionEvidencePort;
    @Mock private OperatorContext operatorContext;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AigcActivityEventService activityEventService;
    @InjectMocks private AigcProjectDeliveryService service;

    @Test
    @DisplayName("Given Review 固定 manifest 99 When 以预期 manifest 100 审批 Then 拒绝版本漂移")
    void should_reject_review_decision_for_different_manifest_version() {
        // 准备参数
        var project = new AigcProject();
        project.setId(1L);
        project.setVersion(4);
        project.setStatus(AigcProjectLifecycle.REVIEWING);
        var review = new AigcProjectObject();
        review.setId(2L);
        review.setProjectId(1L);
        review.setObjectType("review");
        review.setStatus("PENDING");
        review.setVersion(2);
        review.setPayload(Map.of("subjectObjectId", 3L, "subjectObjectVersionId", 99L));
        when(projectRepository.findLockedById(1L)).thenReturn(Optional.of(project));
        when(objectRepository.findLockedById(2L)).thenReturn(Optional.of(review));
        var command = new AigcReviewDecisionCommand(1L, 2L, 100L, 4, 2, "通过", "decision-1");

        // 调用 + 断言
        assertThatThrownBy(() -> service.approveReview(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核目标 manifest 与预期不一致");
    }

    @Test
    @DisplayName("Given completion 要求全部已选渠道 When 渠道 102 未成功 Then 返回明确 blocker")
    void should_block_completion_when_selected_channel_has_not_succeeded() {
        // 准备参数
        var project = new AigcProject();
        project.setId(1L);
        project.setConfigSnapshotId(5L);
        var snapshot = new AigcProjectConfigSnapshot();
        snapshot.setProjectId(1L);
        snapshot.setChannelVersions(List.of(101L, 102L));
        snapshot.setSnapshot(
                Map.of("processPolicy", Map.of("publicationPolicy", "ALL_SELECTED_CHANNELS")));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(snapshotRepository.findById(5L)).thenReturn(Optional.of(snapshot));
        when(completionEvidencePort.load(1L))
                .thenReturn(new CompletionEvidencePort.CompletionEvidence(1, 1, 0, List.of(101L)));

        // 调用
        var evaluation = service.completion(1L);

        // 断言
        assertThat(evaluation.satisfied()).isFalse();
        assertThat(evaluation.requiredChannelSpecVersionIds()).containsExactly(101L, 102L);
        assertThat(evaluation.succeededChannelSpecVersionIds()).containsExactly(101L);
        assertThat(evaluation.blockers()).containsExactly("以下已选渠道尚未发布成功: [102]");
    }

    @Test
    @DisplayName("Given 已采用版本发生变化 When 采用候选 Then CAS 拒绝覆盖")
    void should_reject_adopt_when_expected_adopted_version_is_stale() {
        // 准备参数
        var project = project(4, 5L, AigcProjectLifecycle.CREATING);
        var object = object(2L, "image_deliverable");
        object.setAdoptedVersionId(90L);
        var desired = new AigcObjectVersion();
        desired.setId(91L);
        desired.setProjectId(1L);
        desired.setObjectId(2L);
        desired.setStatus("candidate");
        when(projectRepository.findLockedById(1L)).thenReturn(Optional.of(project));
        when(objectRepository.findLockedById(2L)).thenReturn(Optional.of(object));
        when(versionRepository.findById(91L)).thenReturn(Optional.of(desired));
        when(versionRepository.findByObjectIdAndIdempotencyKey(2L, "adopt-1"))
                .thenReturn(Optional.empty());
        var command = new AigcObjectVersionAdoptCommand(1L, 2L, 91L, 89L, 4, true, "替换", "adopt-1");

        // 调用 + 断言
        assertThatThrownBy(() -> service.adopt(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已采用版本已变化");
    }

    @Test
    @DisplayName("Given 项目 graph revision 已推进 When 冻结 manifest Then 拒绝 stale evidence")
    void should_reject_manifest_freeze_when_graph_revision_is_stale() {
        // 准备参数
        var project = project(4, 5L, AigcProjectLifecycle.CREATING);
        var setObject = object(3L, "deliverable_set");
        when(projectRepository.findLockedById(1L)).thenReturn(Optional.of(project));
        when(objectRepository.findLockedById(3L)).thenReturn(Optional.of(setObject));
        when(versionRepository.findByObjectIdAndIdempotencyKey(3L, "freeze-1"))
                .thenReturn(Optional.empty());
        var command =
                new AigcDeliverableSetManifestFreezeCommand(
                        1L, 3L, List.of(), 4L, "old-evidence", 4, "freeze-1");

        // 调用 + 断言
        assertThatThrownBy(() -> service.freeze(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目图谱修订已变化");
    }

    @Test
    @DisplayName("Given Review 已 stale When 审批 Then 拒绝陈旧审核")
    void should_reject_review_decision_when_review_is_stale() {
        // 准备参数
        var project = project(4, 5L, AigcProjectLifecycle.REVIEWING);
        var review = object(2L, "review");
        review.setStatus("STALE");
        review.setVersion(3);
        review.setPayload(Map.of("subjectObjectVersionId", 99L));
        when(projectRepository.findLockedById(1L)).thenReturn(Optional.of(project));
        when(objectRepository.findLockedById(2L)).thenReturn(Optional.of(review));
        var command = new AigcReviewDecisionCommand(1L, 2L, 99L, 4, 3, "通过", "decision-2");

        // 调用 + 断言
        assertThatThrownBy(() -> service.approveReview(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被处理或已 stale");
    }

    private AigcProject project(int version, Long graphRevision, AigcProjectLifecycle lifecycle) {
        var project = new AigcProject();
        project.setId(1L);
        project.setVersion(version);
        project.setGraphRevision(graphRevision.intValue());
        project.setStatus(lifecycle);
        return project;
    }

    private AigcProjectObject object(Long id, String objectType) {
        var object = new AigcProjectObject();
        object.setId(id);
        object.setProjectId(1L);
        object.setObjectType(objectType);
        return object;
    }
}
