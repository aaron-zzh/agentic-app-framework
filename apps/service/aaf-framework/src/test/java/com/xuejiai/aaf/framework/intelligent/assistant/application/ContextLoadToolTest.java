package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillReferenceCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
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
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** {@link ContextLoadTool} 单测：覆盖 SKILL / SKILL_REFERENCE 加载语义与各类非法输入。 */
class ContextLoadToolTest extends BaseMockitoUnitTest {

    private static final Long SKILL_ID = 1L;
    private static final Long VERSION_ID = 100L;

    @Mock private SkillCatalogPort skills;
    @Mock private SkillReferenceCatalogPort skillReferences;

    private ContextLoadTool tool;

    @BeforeEach
    void setUp() {
        tool = new ContextLoadTool(skills, skillReferences);
    }

    @Test
    void exposesToolNameAndDescription() {
        assertThat(tool.toolName()).isEqualTo("context.load");
        assertThat(tool.description()).isNotBlank();
    }

    @Test
    void loadsSkillContentByCode() {
        when(skills.findByCode("writer")).thenReturn(Optional.of(skillDef("writer")));

        var result = tool.invoke(invocation(Map.of("kind", "SKILL", "key", "writer"))).block();

        assertThat(result).isNotNull();
        assertThat(result.metadata()).containsEntry("kind", "SKILL").containsEntry("code", "writer");
        assertThat(result.output()).contains("writer 技能正文");
    }

    @Test
    void rejectsSkillLoadWhenSkillNotPublished() {
        when(skills.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> tool.invoke(invocation(Map.of("kind", "SKILL", "key", "missing"))).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不存在已发布 Skill: missing");
    }

    @Test
    void loadsSkillReferenceContentByCompositeKey() {
        when(skills.findByCode("writer")).thenReturn(Optional.of(skillDef("writer")));
        when(skillReferences.findByVersionIdAndKey(VERSION_ID, "style-guide"))
                .thenReturn(Optional.of(skillReference("style-guide", 10L, 20L)));
        when(skillReferences.readContent(10L, 20L)).thenReturn(Optional.of("参考文档正文"));

        var result =
                tool.invoke(
                                invocation(
                                        Map.of(
                                                "kind", "SKILL_REFERENCE",
                                                "key", "writer:style-guide")))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.metadata())
                .containsEntry("kind", "SKILL_REFERENCE")
                .containsEntry("skillCode", "writer")
                .containsEntry("referenceKey", "style-guide")
                .containsEntry("title", "风格指南");
        assertThat(result.output()).contains("参考文档正文");
    }

    @Test
    void rejectsSkillReferenceKeyWithoutSeparator() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "writer-style-guide")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 \"技能code:referenceKey\" 形式");
    }

    @Test
    void rejectsSkillReferenceKeyWithLeadingSeparator() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", ":style-guide")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 \"技能code:referenceKey\" 形式");
    }

    @Test
    void rejectsSkillReferenceKeyWithTrailingSeparator() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "writer:")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 \"技能code:referenceKey\" 形式");
    }

    @Test
    void rejectsSkillReferenceWhenSkillNotPublished() {
        when(skills.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "missing:style-guide")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不存在已发布 Skill: missing");
    }

    @Test
    void rejectsSkillReferenceWhenNotMountedOnSkillVersion() {
        when(skills.findByCode("writer")).thenReturn(Optional.of(skillDef("writer")));
        when(skillReferences.findByVersionIdAndKey(VERSION_ID, "unknown-ref"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "writer:unknown-ref")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Skill 未挂载该参考文档: writer:unknown-ref");
    }

    @Test
    void rejectsSkillReferenceWhenContentUnavailable() {
        when(skills.findByCode("writer")).thenReturn(Optional.of(skillDef("writer")));
        when(skillReferences.findByVersionIdAndKey(VERSION_ID, "style-guide"))
                .thenReturn(Optional.of(skillReference("style-guide", 10L, 20L)));
        when(skillReferences.readContent(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "writer:style-guide")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("参考文档内容不可用: writer:style-guide");
    }

    @Test
    void rejectsKnowledgeBindingAsNotYetSupported() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(invocation(Map.of("kind", "KNOWLEDGE_BINDING", "key", "kb-1")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尚不支持 KNOWLEDGE_BINDING");
    }

    @Test
    void rejectsMissingKind() {
        assertThatThrownBy(() -> tool.invoke(invocation(Map.of("key", "writer"))).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context.load 需要非空 kind");
    }

    @Test
    void rejectsBlankKind() {
        assertThatThrownBy(
                        () -> tool.invoke(invocation(Map.of("kind", "  ", "key", "writer"))).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context.load 需要非空 kind");
    }

    @Test
    void rejectsUnsupportedKind() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(invocation(Map.of("kind", "UNKNOWN", "key", "writer")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context.load kind 不支持: UNKNOWN");
    }

    @Test
    void rejectsMissingKey() {
        assertThatThrownBy(() -> tool.invoke(invocation(Map.of("kind", "SKILL"))).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context.load 需要非空 key");
    }

    @Test
    void rejectsBlankKey() {
        assertThatThrownBy(
                        () -> tool.invoke(invocation(Map.of("kind", "SKILL", "key", "   "))).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context.load 需要非空 key");
    }

    private static SkillDef skillDef(String code) {
        return new SkillDef(
                SKILL_ID,
                code,
                code + " 技能",
                code + " 技能摘要",
                new SkillVersionRef(SKILL_ID, VERSION_ID, 1),
                code + " 技能正文",
                Set.of(),
                Set.of(),
                true,
                false);
    }

    private static SkillReference skillReference(
            String referenceKey, Long documentId, Long documentVersionId) {
        return new SkillReference(referenceKey, "风格指南", documentId, documentVersionId, false, 2000);
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
                "call-1", new ToolRef(ContextLoadTool.TOOL_NAME, 1L, ContextLoadTool.TOOL_NAME), arguments, context);
    }
}
