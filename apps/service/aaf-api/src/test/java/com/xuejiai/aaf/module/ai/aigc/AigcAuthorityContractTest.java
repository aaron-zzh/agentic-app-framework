package com.xuejiai.aaf.module.ai.aigc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.controller.AigcProjectActionController;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcActionCommandService;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetManifestFreezeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycleCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewDecisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcProjectController;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectService;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineCreateCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineDeleteCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineReplaceCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.controller.AigcTimelineController;
import com.xuejiai.aaf.module.ai.aigc.timeline.service.AigcTimelineService;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationCancelCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationRetryCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkArchiveCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkCollectCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkPublishCommand;
import com.xuejiai.aaf.module.ai.aigc.work.controller.AigcWorkController;
import com.xuejiai.aaf.module.ai.aigc.work.service.AigcWorkService;

class AigcAuthorityContractTest {

    @Test
    @DisplayName("Given AIGC 自定义动作 When 检查 Service guard Then 全链使用正式 authority code")
    void should_use_one_authority_source_for_custom_operations() throws NoSuchMethodException {
        assertGuard(
                AigcActionCommandService.class,
                "submit",
                AigcAuthorities.HAS_PROJECT_ACTION,
                AigcActionCommand.class);
        assertGuard(
                AigcActionCommandService.class,
                "requireRunTree",
                AigcAuthorities.HAS_EXECUTION_RUN_READ,
                Long.class);
        assertGuard(
                AigcProjectService.class,
                "adoptVersion",
                AigcAuthorities.HAS_OBJECT_VERSION_ADOPT,
                AigcObjectVersionAdoptCommand.class);
        assertGuard(
                AigcProjectService.class,
                "freezeDeliverableSetManifest",
                AigcAuthorities.HAS_PROJECT_UPDATE,
                AigcDeliverableSetManifestFreezeCommand.class);
        assertGuard(
                AigcProjectService.class,
                "submitReview",
                AigcAuthorities.HAS_PROJECT_REVIEW,
                AigcReviewSubmitCommand.class);
        assertGuard(
                AigcProjectService.class,
                "approveReview",
                AigcAuthorities.HAS_PROJECT_REVIEW,
                AigcReviewDecisionCommand.class);
        assertGuard(
                AigcProjectService.class,
                "complete",
                AigcAuthorities.HAS_PROJECT_LIFECYCLE,
                AigcProjectLifecycleCommand.class);
        assertGuard(
                AigcProjectService.class,
                "archive",
                AigcAuthorities.HAS_PROJECT_LIFECYCLE,
                AigcProjectLifecycleCommand.class);
        assertGuard(
                AigcWorkService.class,
                "collect",
                AigcAuthorities.HAS_WORK_COLLECT,
                AigcWorkCollectCommand.class);
        assertGuard(
                AigcWorkService.class,
                "publish",
                AigcAuthorities.HAS_WORK_PUBLISH,
                AigcWorkPublishCommand.class);
        assertGuard(
                AigcWorkService.class,
                "cancelPublication",
                AigcAuthorities.HAS_WORK_PUBLISH,
                AigcPublicationCancelCommand.class);
        assertGuard(
                AigcWorkService.class,
                "retryPublication",
                AigcAuthorities.HAS_WORK_PUBLISH,
                AigcPublicationRetryCommand.class);
        assertGuard(
                AigcWorkService.class,
                "archive",
                AigcAuthorities.HAS_WORK_ARCHIVE,
                AigcWorkArchiveCommand.class);
        assertGuard(
                AigcTimelineService.class,
                "create",
                AigcAuthorities.HAS_TIMELINE_CREATE,
                AigcTimelineCreateCommand.class);
        assertGuard(
                AigcTimelineService.class,
                "replaceComposition",
                AigcAuthorities.HAS_TIMELINE_UPDATE,
                AigcTimelineReplaceCommand.class);
        assertGuard(
                AigcTimelineService.class,
                "composition",
                AigcAuthorities.HAS_TIMELINE_READ,
                Long.class);
        assertGuard(
                AigcTimelineService.class,
                "deleteComposition",
                AigcAuthorities.HAS_TIMELINE_DELETE,
                AigcTimelineDeleteCommand.class);
    }

    @Test
    @DisplayName("Given AIGC Controller 自定义动作 When 检查注解 Then 与 Service 使用同一 authority")
    void should_use_same_authority_in_controllers() {
        assertNamedGuard(
                AigcProjectActionController.class, "actions", AigcAuthorities.HAS_PROJECT_READ);
        assertNamedGuard(
                AigcProjectActionController.class, "execute", AigcAuthorities.HAS_PROJECT_ACTION);
        assertNamedGuard(
                AigcProjectController.class,
                "adoptVersion",
                AigcAuthorities.HAS_OBJECT_VERSION_ADOPT);
        assertNamedGuard(
                AigcProjectController.class,
                "freezeDeliverableSet",
                AigcAuthorities.HAS_PROJECT_UPDATE);
        assertNamedGuard(
                AigcProjectController.class, "submitReview", AigcAuthorities.HAS_PROJECT_REVIEW);
        assertNamedGuard(
                AigcProjectController.class, "approveReview", AigcAuthorities.HAS_PROJECT_REVIEW);
        assertNamedGuard(
                AigcProjectController.class, "returnReview", AigcAuthorities.HAS_PROJECT_REVIEW);
        assertNamedGuard(
                AigcProjectController.class, "complete", AigcAuthorities.HAS_PROJECT_LIFECYCLE);
        assertNamedGuard(
                AigcProjectController.class, "archive", AigcAuthorities.HAS_PROJECT_LIFECYCLE);
        assertNamedGuard(AigcWorkController.class, "collect", AigcAuthorities.HAS_WORK_COLLECT);
        assertNamedGuard(AigcWorkController.class, "publish", AigcAuthorities.HAS_WORK_PUBLISH);
        assertNamedGuard(
                AigcWorkController.class, "cancelPublication", AigcAuthorities.HAS_WORK_PUBLISH);
        assertNamedGuard(
                AigcWorkController.class, "retryPublication", AigcAuthorities.HAS_WORK_PUBLISH);
        assertNamedGuard(AigcWorkController.class, "archiveWork", AigcAuthorities.HAS_WORK_ARCHIVE);
        assertNamedGuard(
                AigcTimelineController.class,
                "createComposition",
                AigcAuthorities.HAS_TIMELINE_CREATE);
        assertNamedGuard(
                AigcTimelineController.class, "composition", AigcAuthorities.HAS_TIMELINE_READ);
        assertNamedGuard(
                AigcTimelineController.class,
                "replaceComposition",
                AigcAuthorities.HAS_TIMELINE_UPDATE);
        assertNamedGuard(
                AigcTimelineController.class,
                "deleteComposition",
                AigcAuthorities.HAS_TIMELINE_DELETE);
    }

    @Test
    @DisplayName("Given 正式 AIGC authority When 读取 seed Then 权限列表可查询且角色映射可达")
    void should_seed_custom_authorities_for_permission_queries() throws Exception {
        var sql =
                new ClassPathResource("db/seed/v17__aigc_content_studio_seed.sql")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains(
                        AigcAuthorities.PROJECT_ACTION,
                        AigcAuthorities.OBJECT_VERSION_ADOPT,
                        AigcAuthorities.PROJECT_REVIEW,
                        AigcAuthorities.PROJECT_LIFECYCLE,
                        AigcAuthorities.EXECUTION_RUN_EXECUTE,
                        AigcAuthorities.WORK_COLLECT,
                        AigcAuthorities.WORK_PUBLISH,
                        AigcAuthorities.WORK_ARCHIVE,
                        AigcAuthorities.TIMELINE_READ,
                        AigcAuthorities.TIMELINE_CREATE,
                        AigcAuthorities.TIMELINE_UPDATE,
                        AigcAuthorities.TIMELINE_DELETE)
                .contains("JOIN sys_permission_code permission ON permission.module = 'aigc'")
                .contains("role.code IN ('admin', 'super_admin')")
                .contains("role.code IN ('member', 'org_admin')");
    }

    private void assertNamedGuard(Class<?> type, String methodName, String expected) {
        var method =
                java.util.Arrays.stream(type.getMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow();
        var annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(type.getSimpleName() + "." + methodName + " guard").isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }

    private void assertGuard(
            Class<?> type, String methodName, String expected, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        var annotation =
                type.getMethod(methodName, parameterTypes).getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(type.getSimpleName() + "." + methodName + " guard").isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
