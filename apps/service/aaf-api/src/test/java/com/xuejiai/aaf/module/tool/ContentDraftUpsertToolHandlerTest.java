package com.xuejiai.aaf.module.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi.DraftDocument;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi.DraftUpsertCommand;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class ContentDraftUpsertToolHandlerTest extends BaseMockitoUnitTest {

    @Mock private DocumentDraftApi documents;

    private ContentDraftUpsertToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ContentDraftUpsertToolHandler(documents);
    }

    @Test
    @DisplayName("Given 草稿工具进入代码自声明路径 When 构造授权画像 Then 元数据与可撤销写策略一致")
    void should_declare_reversible_write_authorization_profile() {
        // 准备参数
        var authorization =
                new ToolAuthorizationContext(
                        Map.of(
                                ContentDraftUpsertToolHandler.TOOL_NAME,
                                new ToolAuthorizationContext.ToolAuthorizationRule(
                                        false,
                                        true,
                                        true,
                                        ToolAuthorizationContext.MissingGrantBehavior
                                                .REQUEST_ON_DEMAND)));

        // 调用 + 断言
        assertThat(handler.readOnly()).isFalse();
        assertThat(handler.reversible()).isTrue();
        assertThat(handler.requireConfirm()).isTrue();
        assertThat(handler.inputSchema())
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(handler.inputSchema().get("required")).isEqualTo(List.of("title", "content"));
        assertThat((Map<?, ?>) handler.inputSchema().get("properties"))
                .containsKeys("title", "content", "documentType");
        assertThatCode(
                        () ->
                                authorization.requireVisible(
                                        ControlMode.DELEGATED,
                                        ContentDraftUpsertToolHandler.TOOL_NAME,
                                        handler.readOnly(),
                                        handler.reversible()))
                .doesNotThrowAnyException();
    }

    @Test
    void savesDatabaseDraftWithTrustedInvocationScopeAndCompletionEvidence() {
        when(documents.upsertDraft(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DraftDocument(91L, 92L));

        var result =
                handler.invoke(
                                invocation(
                                        Map.of(
                                                "title", "测试文案",
                                                "content", "# Markdown",
                                                "documentType", "copywriting")))
                        .block();

        var command = ArgumentCaptor.forClass(DraftUpsertCommand.class);
        verify(documents).upsertDraft(command.capture());
        assertThat(command.getValue())
                .extracting(
                        DraftUpsertCommand::ownerId,
                        DraftUpsertCommand::orgId,
                        DraftUpsertCommand::workspaceId)
                .containsExactly(7L, 9L, 11L);
        assertThat(result).isNotNull();
        assertThat(result.metadata())
                .containsEntry("artifactState", "DRAFT")
                .containsEntry("artifactType", "DOCUMENT")
                .containsEntry("artifactId", 91L)
                .containsEntry("artifactVersionId", 92L)
                .containsEntry("reversible", true)
                .containsEntry("completionEvidence", "DRAFT_COMMITTED");
        assertThat(result.output()).contains("\"published\":false");
    }

    @Test
    void rejectsModelSuppliedTrustedScopeOrPublishArguments() {
        assertThatThrownBy(
                        () ->
                                handler.invoke(
                                                invocation(
                                                        Map.of(
                                                                "title", "测试文案",
                                                                "content", "# Markdown",
                                                                "workspaceId", 999L)))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content.draft.upsert 不接受受信范围或发布参数: workspaceId");
    }

    private static ToolInvocation invocation(Map<String, Object> arguments) {
        var identity = "execution-1";
        var context =
                new InvocationContext(
                        new TenantId("9"),
                        new UserId("7"),
                        11L,
                        new AssistantId("assistant-1"),
                        new ConversationId(identity),
                        new SessionId(identity),
                        new TaskId(identity),
                        new ExecutionId(identity),
                        new RunId(identity),
                        null,
                        new CorrelationId(identity),
                        null,
                        new IdempotencyKey(identity),
                        ControlMode.COLLABORATIVE,
                        null,
                        null,
                        new ToolAuthorizationContext(Map.of()));
        return new ToolInvocation(
                "call-1",
                new ToolRef(
                        ContentDraftUpsertToolHandler.TOOL_NAME,
                        1L,
                        ContentDraftUpsertToolHandler.TOOL_NAME),
                arguments,
                context);
    }
}
