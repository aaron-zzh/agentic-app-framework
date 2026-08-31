package com.xuejiai.aaf.framework.intelligent.cognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.AgentPurpose;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.ContextBudget;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.ContextScope;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.Disclosure;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextRequest.KnowledgeQuery;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort.SessionRecallQuery;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort.SessionTurn;
import com.xuejiai.aaf.framework.intelligent.cognition.port.UnifiedRetrievalPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.UnifiedRetrievalPort.FusedCandidate;
import com.xuejiai.aaf.framework.intelligent.cognition.port.UnifiedRetrievalPort.UnifiedRetrievalResult;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

@ExtendWith(MockitoExtension.class)
class DefaultL1ContextCollaboratorTest {

    @Mock private UnifiedRetrievalPort unifiedRetrieval;
    @Mock private SessionMemoryPort sessions;

    @Test
    void emptyScopesReturnEmptySnapshot() {
        var collaborator = new DefaultL1ContextCollaborator(unifiedRetrieval, sessions);
        var request = requestBuilder().scopes(Set.of()).build();

        var snapshot = collaborator.resolve(request);

        assertThat(snapshot.messages()).isEmpty();
        assertThat(snapshot.references()).isEmpty();
    }

    @Test
    void sessionContextInjectedIndependentlyOfRetrievalResult() {
        when(sessions.recentTurns(any(SessionRecallQuery.class)))
                .thenReturn(List.of(new SessionTurn("user", "你好")));
        when(unifiedRetrieval.retrieve(any())).thenReturn(UnifiedRetrievalResult.empty());

        var collaborator = new DefaultL1ContextCollaborator(unifiedRetrieval, sessions);
        var request =
                requestBuilder()
                        .scopes(Set.of(ContextScope.MEMORY))
                        .memorySubjectKind(SubjectKind.USER)
                        .sessionId("session-1")
                        .build();

        var snapshot = collaborator.resolve(request);

        assertThat(snapshot.messages()).isNotEmpty();
        assertThat(snapshot.messages().get(0).text()).contains("你好");
        verify(sessions).recentTurns(any(SessionRecallQuery.class));
    }

    @Test
    void memoryScopeDelegatesToUnifiedRetrievalPort() {
        when(unifiedRetrieval.retrieve(any()))
                .thenReturn(
                        new UnifiedRetrievalResult(
                                List.of(
                                        new FusedCandidate(
                                                "MEMORY:ATOMIC:1", "记忆内容", "atomic", 1.0)),
                                List.of()));

        var collaborator = new DefaultL1ContextCollaborator(unifiedRetrieval, sessions);
        var request =
                requestBuilder()
                        .scopes(Set.of(ContextScope.MEMORY))
                        .memorySubjectKind(SubjectKind.USER)
                        .query("怎么办")
                        .build();

        var snapshot = collaborator.resolve(request);

        verify(unifiedRetrieval).retrieve(any());
        assertThat(snapshot.references()).hasSize(1);
    }

    @Test
    void visitorSubjectSkipsMemoryRetrieval() {
        var collaborator = new DefaultL1ContextCollaborator(unifiedRetrieval, sessions);
        var request =
                requestBuilder()
                        .scopes(Set.of(ContextScope.MEMORY))
                        .memorySubjectKind(SubjectKind.VISITOR)
                        .query("怎么办")
                        .build();

        collaborator.resolve(request);

        verify(unifiedRetrieval, never()).retrieve(any());
    }

    @Test
    void taskMaterialsInjectedIndependentlyOfRetrieval() {
        var collaborator = new DefaultL1ContextCollaborator(unifiedRetrieval, sessions);
        var reference =
                new com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest
                        .SourceReference(
                        com.xuejiai.aaf.framework.intelligent.assistant.model
                                .EffectiveContextManifest.SourceType.TASK_MATERIAL,
                        "material-1",
                        "1",
                        "task",
                        "任务材料",
                        "任务材料摘要",
                        true);
        var material =
                new ContextRequest.TaskMaterial(
                        reference,
                        new com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage(
                                "material-1",
                                com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage.Role
                                        .USER,
                                "任务材料正文"));
        var request =
                requestBuilder()
                        .scopes(Set.of(ContextScope.TASK_MATERIAL))
                        .authorizedCandidates(List.of(reference))
                        .taskMaterials(List.of(material))
                        .build();

        var snapshot = collaborator.resolve(request);

        assertThat(snapshot.references()).contains(reference);
        verify(unifiedRetrieval, never()).retrieve(any());
    }

    private static RequestBuilder requestBuilder() {
        return new RequestBuilder();
    }

    /** 测试用 ContextRequest 构造辅助，避免每个用例重复冗长构造代码。 */
    private static final class RequestBuilder {
        private Set<ContextScope> scopes = Set.of(ContextScope.MEMORY);
        private SubjectKind subjectKind = SubjectKind.USER;
        private String sessionId;
        private String query = "";
        private List<
                        com.xuejiai.aaf.framework.intelligent.assistant.model
                                .EffectiveContextManifest.SourceReference>
                authorizedCandidates = List.of();
        private List<ContextRequest.TaskMaterial> taskMaterials = List.of();

        RequestBuilder scopes(Set<ContextScope> value) {
            this.scopes = value;
            return this;
        }

        RequestBuilder memorySubjectKind(SubjectKind value) {
            this.subjectKind = value;
            return this;
        }

        RequestBuilder sessionId(String value) {
            this.sessionId = value;
            return this;
        }

        RequestBuilder query(String value) {
            this.query = value;
            return this;
        }

        RequestBuilder authorizedCandidates(
                List<
                                com.xuejiai.aaf.framework.intelligent.assistant.model
                                        .EffectiveContextManifest.SourceReference>
                        value) {
            this.authorizedCandidates = value;
            return this;
        }

        RequestBuilder taskMaterials(List<ContextRequest.TaskMaterial> value) {
            this.taskMaterials = value;
            return this;
        }

        ContextRequest build() {
            return new ContextRequest(
                    new TenantId("tenant-1"),
                    new UserId("1"),
                    new TaskId("task-1"),
                    new ExecutionId("execution-1"),
                    new AssistantId("assistant-1"),
                    new MemorySubject(new TenantId("tenant-1"), subjectKind, "1"),
                    AgentPurpose.EXECUTION,
                    query,
                    scopes,
                    new ContextBudget(8, 2048),
                    Disclosure.CONTENT_ALLOWED,
                    authorizedCandidates,
                    taskMaterials,
                    KnowledgeQuery.none(),
                    sessionId,
                    Instant.now());
        }
    }
}
