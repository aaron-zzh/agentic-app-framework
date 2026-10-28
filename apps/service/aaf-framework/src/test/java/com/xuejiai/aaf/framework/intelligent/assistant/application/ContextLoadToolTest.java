package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillReferenceCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillScope;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference.LoadMode;
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
        var version = new SkillVersionRef(SKILL_ID, VERSION_ID, 1);
        when(skills.findByVersion(version)).thenReturn(Optional.of(skillDef("writer")));

        var result = tool.invoke(invocation(Map.of("kind", "SKILL", "key", "writer"))).block();

        assertThat(result).isNotNull();
        assertThat(result.metadata())
                .containsEntry("kind", "SKILL")
                .containsEntry("code", "writer");
        assertThat(result.output()).contains("writer 技能正文");
        verify(skills).findByVersion(version);
    }

    @Test
    void rejectsSkillLoadWhenSkillNotPublished() {
        when(skills.findByVersion(new SkillVersionRef(SKILL_ID, VERSION_ID, 1)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                tool.invoke(invocation(Map.of("kind", "SKILL", "key", "missing")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不存在已发布 Skill: missing");
    }

    @Test
    void loadsSkillReferenceContentByCompositeKey() {
        when(skillReferences.versionBelongsToSkillCode(VERSION_ID, "writer")).thenReturn(true);
        when(skillReferences.findByVersionIdAndKey(VERSION_ID, "style-guide"))
                .thenReturn(Optional.of(skillReference("style-guide", 10L, 20L)));
        when(skillReferences.readContent(10L, 20L)).thenReturn(Optional.of("参考文档正文"));

        var result =
                tool.invoke(
                                invocation(
                                        Map.of(
                                                "kind",
                                                "SKILL_REFERENCE",
                                                "key",
                                                "writer@100:style-guide")))
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.metadata())
                .containsEntry("kind", "SKILL_REFERENCE")
                .containsEntry("skillCode", "writer")
                .containsEntry("skillVersionId", VERSION_ID)
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
                .hasMessageContaining("必须是 \"技能code@skillVersionId:referenceKey\" 形式");
    }

    @Test
    void rejectsSkillReferenceKeyWithLeadingSeparator() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "@100:style-guide")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 \"技能code@skillVersionId:referenceKey\" 形式");
    }

    @Test
    void rejectsSkillReferenceKeyWithTrailingSeparator() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "writer@100:")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 \"技能code@skillVersionId:referenceKey\" 形式");
    }

    @Test
    void rejectsSkillReferenceWhenVersionDoesNotBelongToSkill() {
        when(skillReferences.versionBelongsToSkillCode(VERSION_ID, "missing")).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "missing@100:style-guide")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SkillVersion 与 Skill code 不匹配: missing@100:style-guide");
    }

    @Test
    void rejectsSkillReferenceWhenNotMountedOnSkillVersion() {
        when(skillReferences.versionBelongsToSkillCode(VERSION_ID, "writer")).thenReturn(true);
        when(skillReferences.findByVersionIdAndKey(VERSION_ID, "unknown-ref"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "writer@100:unknown-ref")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SkillVersion 未挂载该参考文档: writer@100:unknown-ref");
    }

    @Test
    void rejectsSkillReferenceWhenContentUnavailable() {
        when(skillReferences.versionBelongsToSkillCode(VERSION_ID, "writer")).thenReturn(true);
        when(skillReferences.findByVersionIdAndKey(VERSION_ID, "style-guide"))
                .thenReturn(Optional.of(skillReference("style-guide", 10L, 20L)));
        when(skillReferences.readContent(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind", "SKILL_REFERENCE",
                                                                "key", "writer@100:style-guide")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("参考文档内容不可用: writer@100:style-guide");
    }

    @Test
    void rejectsKnowledgeBindingAsNotYetSupported() {
        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        Map.of(
                                                                "kind",
                                                                "KNOWLEDGE_BINDING",
                                                                "key",
                                                                "kb-1")))
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
                        () ->
                                tool.invoke(invocation(Map.of("kind", "  ", "key", "writer")))
                                        .block())
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
                        () ->
                                tool.invoke(invocation(Map.of("kind", "SKILL", "key", "   ")))
                                        .block())
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
        return new SkillReference(
                referenceKey,
                "风格指南",
                documentId,
                documentVersionId,
                null,
                LoadMode.ON_DEMAND,
                "需要风格规范时",
                false,
                2000,
                100,
                "b8d1768c279b45c77ed38c4bbcb6223325f7eb6c41eb120c13483a5da7a1f1f4");
    }

    private static ToolInvocation invocation(Map<String, Object> arguments) {
        var identity = "execution-1";
        var context =
                new InvocationContext(
                        new TenantId("9"),
                        new UserId("7"),
                        11L,
                        null,
                        new AssistantId("assistant-1"),
                        new ConversationId(identity),
                        new SessionId(identity),
                        new TaskId(identity),
                        new ExecutionId(identity),
                        identity,
                        new RunId(identity),
                        null,
                        new CorrelationId(identity),
                        null,
                        new IdempotencyKey(identity),
                        ControlMode.COLLABORATIVE,
                        null,
                        null,
                        new ToolAuthorizationContext(Map.of()),
                        null,
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        Optional.of(skillExecutionProfile(arguments)));
        return new ToolInvocation(
                "call-1",
                new ToolRef(ContextLoadTool.TOOL_NAME, 1L, ContextLoadTool.TOOL_NAME),
                arguments,
                context);
    }

    private static SkillExecutionProfile skillExecutionProfile(Map<String, Object> arguments) {
        var code = authorizedSkillCode(arguments);
        var summary =
                new AuthorizedSkillSummary(
                        code,
                        code + " 技能",
                        code + " 技能摘要",
                        SkillScope.ASSISTANT,
                        SkillActivationMode.ON_DEMAND,
                        new SkillVersionRef(SKILL_ID, VERSION_ID, 1),
                        List.of(),
                        Set.of(),
                        Set.of());
        return new SkillExecutionProfile(
                new SkillSelectionManifest(
                        "writer", null, SkillSelectionMode.SELECT_AND_AUGMENT, 1, List.of(summary)),
                List.of(),
                List.of(new ToolRef(ContextLoadTool.TOOL_NAME, 1L, ContextLoadTool.TOOL_NAME)));
    }

    private static String authorizedSkillCode(Map<String, Object> arguments) {
        var kind = arguments.get("kind");
        var key = arguments.get("key");
        if ("SKILL_REFERENCE".equals(kind) && key != null) {
            var compositeKey = key.toString();
            var versionSeparator = compositeKey.indexOf('@');
            if (versionSeparator > 0) {
                return compositeKey.substring(0, versionSeparator);
            }
        }
        if ("SKILL".equals(kind) && key != null && !key.toString().isBlank()) {
            return key.toString().trim();
        }
        return "writer";
    }
}
