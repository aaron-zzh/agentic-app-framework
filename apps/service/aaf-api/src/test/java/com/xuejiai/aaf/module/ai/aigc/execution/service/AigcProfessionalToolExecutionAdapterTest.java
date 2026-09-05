package com.xuejiai.aaf.module.ai.aigc.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaVersionView;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaView;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskView;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import tools.jackson.core.type.TypeReference;

class AigcProfessionalToolExecutionAdapterTest extends BaseMockitoUnitTest {

    @Mock private AigcTaskApi taskApi;
    @Mock private AigcMediaApi mediaApi;
    @InjectMocks private AigcProfessionalToolExecutionAdapter adapter;

    @Test
    @DisplayName("Given 图片编辑附件和动作参数 When 执行 Tool Then 按序传递 imageFileIds 与请求模型")
    void should_submit_image_edit_with_ordered_file_ids_and_requested_model() {
        // 准备参数
        stubMediaVersion(101L, 1001L);
        stubMediaVersion(102L, 1002L);
        when(taskApi.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AigcTaskView(91L, 1L, "IMAGE", "PENDING", null, null));
        var command =
                command(
                        "aigc.image.edit",
                        "image.edit",
                        Map.of(
                                "requestedModelId",
                                "image-model-v2",
                                "actionArguments",
                                Map.of("width", 1024, "unexpected", "ignored"),
                                "attachmentMediaVersionIds",
                                List.of(101L, 102L)));

        // 调用
        var result = adapter.execute(command);

        // 断言
        var captor = ArgumentCaptor.forClass(AigcTaskSubmitCommand.class);
        verify(taskApi).submit(captor.capture());
        var submitted = captor.getValue();
        assertThat(result.taskIds()).containsExactly(91L);
        assertThat(submitted.taskType()).isEqualTo("IMAGE");
        assertThat(submitted.modelId()).isEqualTo("image-model-v2");
        assertThat(parameters(submitted))
                .containsEntry("width", 1024)
                .containsEntry("imageFileIds", List.of(1001, 1002))
                .doesNotContainKey("unexpected")
                .doesNotContainKey("sourceImages");
    }

    @Test
    @DisplayName("Given 视频附件和动作参数 When 执行 Tool Then 生成 VIDEO 任务并只复制白名单参数")
    void should_submit_video_with_ordered_files_and_allowed_arguments() {
        // 准备参数
        stubMediaVersion(201L, 2001L);
        stubMediaVersion(202L, 2002L);
        when(taskApi.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AigcTaskView(92L, 2L, "VIDEO", "PENDING", null, null));
        var command =
                command(
                        "aigc.video.generate",
                        "video.generate",
                        Map.of(
                                "requestedModelId",
                                "video-model-v3",
                                "actionArguments",
                                Map.of(
                                        "resolution",
                                        "1080p",
                                        "duration",
                                        8,
                                        "imageMode",
                                        "REFERENCE",
                                        "unexpected",
                                        true),
                                "attachmentMediaVersionIds",
                                List.of(201L, 202L)));

        // 调用
        adapter.execute(command);

        // 断言
        var captor = ArgumentCaptor.forClass(AigcTaskSubmitCommand.class);
        verify(taskApi).submit(captor.capture());
        var submitted = captor.getValue();
        assertThat(submitted.taskType()).isEqualTo("VIDEO");
        assertThat(submitted.modelId()).isEqualTo("video-model-v3");
        assertThat(parameters(submitted))
                .containsEntry("resolution", "1080p")
                .containsEntry("duration", 8)
                .containsEntry("imageMode", "REFERENCE")
                .containsEntry("imageFileId", 2001)
                .containsEntry("referenceImageFileIds", List.of(2001, 2002))
                .doesNotContainKey("unexpected");
    }

    private Command command(String targetRef, String actionKey, Map<String, Object> input) {
        return new Command(1L, 2L, 3L, 4L, 5L, 6L, targetRef, actionKey, "生成内容", "idem-1", input);
    }

    private void stubMediaVersion(Long mediaVersionId, Long fileId) {
        var media = org.mockito.Mockito.mock(AigcMediaView.class);
        var version = org.mockito.Mockito.mock(AigcMediaVersionView.class);
        when(version.fileId()).thenReturn(fileId);
        when(media.currentVersion()).thenReturn(version);
        when(mediaApi.getByVersionId(mediaVersionId, 6L)).thenReturn(media);
    }

    private Map<String, Object> parameters(AigcTaskSubmitCommand command) {
        return JsonUtils.parseObject(
                command.parametersJson(), new TypeReference<Map<String, Object>>() {});
    }
}
