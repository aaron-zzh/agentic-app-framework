package com.xuejiai.aaf.module.ai.aigc.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcExecutionSubmissionServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcExecutionSubmissionRepository repository;
    @Mock private JdbcClient jdbcClient;
    @Mock private AigcProjectApi projectApi;
    @Mock private OperatorContext operatorContext;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private AigcExecutionSubmissionService service;

    @Test
    @DisplayName("Given durable submission payload When 恢复重放 Then 还原冻结命令参数")
    void should_restore_command_from_durable_submission_for_replay() {
        // 准备参数
        var submission =
                new com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission();
        submission.setIdempotencyKey("idem-replay");
        submission.setRequestPayload(
                Map.of(
                        "projectId",
                        1L,
                        "projectObjectId",
                        2L,
                        "actionKey",
                        "video.generate",
                        "prompt",
                        "生成视频",
                        "requestedModelId",
                        "model-v2",
                        "actionArguments",
                        Map.of("duration", 8),
                        "attachmentMediaVersionIds",
                        List.of(9L, 3L),
                        "selectedProjectObjectIds",
                        List.of(2L, 4L),
                        "expectedGraphRevision",
                        6L,
                        "confirmed",
                        true));

        // 调用
        var command = service.toCommand(submission);

        // 断言
        assertThat(command.idempotencyKey()).isEqualTo("idem-replay");
        assertThat(command.requestedModelId()).isEqualTo("model-v2");
        assertThat(command.actionArguments()).containsEntry("duration", 8);
        assertThat(command.attachmentMediaVersionIds()).containsExactly(9L, 3L);
        assertThat(command.selectedProjectObjectIds()).containsExactly(2L, 4L);
        assertThat(command.expectedGraphRevision()).isEqualTo(6L);
    }

    @Test
    @DisplayName("Given recovery 瞬态失败 When 记录重试 Then 保存 attempt/lastError/backoff 且不改 saga 状态")
    void should_persist_retry_metadata_without_changing_recoverable_status() {
        // 准备参数
        var submission = new AigcExecutionSubmission();
        submission.setId(80L);
        submission.setStatus("RESERVED");
        when(repository.findLockedById(80L)).thenReturn(Optional.of(submission));

        // 调用
        service.recordRetryableRecoveryFailure(80L, "数据库连接超时");

        // 断言
        assertThat(submission.getStatus()).isEqualTo("RESERVED");
        assertThat(submission.getLastError()).isEqualTo("数据库连接超时");
        assertThat(submission.getRemark())
                .contains("\"attempt\":1", "\"backoffSeconds\":30", "\"retryAfter\"");
        assertThat(service.isRecoveryDue(submission, LocalDateTime.now())).isFalse();
        assertThat(service.isRecoveryDue(submission, LocalDateTime.now().plusSeconds(31))).isTrue();
        verify(repository).save(submission);
    }

    @Test
    @DisplayName("Given 附件有业务顺序 When 固化 submission Then attachmentMediaVersionIds 原序保留")
    void should_preserve_attachment_media_version_order() {
        // 准备参数
        var command =
                new AigcActionCommand(
                        1L,
                        2L,
                        "video.compose",
                        "prompt",
                        "model",
                        Map.of("z", 1, "a", 2),
                        List.of(9L, 3L, 7L),
                        List.of(8L, 4L),
                        10L,
                        true,
                        "idem-1");

        // 调用
        var normalized = service.normalize(command);

        // 断言
        assertThat(normalized.get("attachmentMediaVersionIds")).isEqualTo(List.of(9L, 3L, 7L));
        assertThat(normalized.get("actionArguments")).isEqualTo(Map.of("a", 2, "z", 1));
    }
}
