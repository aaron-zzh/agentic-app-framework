package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.Objects;

/**
 * 生成 Proposal 的作用域（显式 sum type）。
 *
 * <p>同一 Proposal 内核服务两种域，不建第二套 Proposal/Approval 状态机：
 *
 * <ul>
 *   <li>{@code PROJECT}：项目已物化，身份是 projectId，上下文锚定项目版本、快照与 graphRevision。
 *   <li>{@code CREATION_DRAFT}：项目尚未物化（还没有 projectId），身份是服务端签发的 draftId，上下文锚定已发布 Blueprint 版本与
 *       声明的初始化字段键集合；只允许 {@code INITIALIZATION_FIELD} 目标。
 * </ul>
 *
 * <p>两种域互斥：字段组合由构造器与数据库 CHECK 双重约束，禁止用 null 判断做隐式分流。
 */
public record AigcGenerationProposalScope(
        Kind kind, Long projectId, String draftId, Long blueprintVersionId) {

    public enum Kind {
        PROJECT,
        CREATION_DRAFT
    }

    public AigcGenerationProposalScope {
        Objects.requireNonNull(kind, "scope kind 不能为空");
        draftId = draftId == null || draftId.isBlank() ? null : draftId.trim();
        switch (kind) {
            case PROJECT -> {
                if (projectId == null || projectId < 1) {
                    throw new IllegalArgumentException("PROJECT 作用域必须提供 projectId");
                }
                if (draftId != null || blueprintVersionId != null) {
                    throw new IllegalArgumentException(
                            "PROJECT 作用域不得携带 draftId 或 blueprintVersionId");
                }
            }
            case CREATION_DRAFT -> {
                if (projectId != null) {
                    throw new IllegalArgumentException("CREATION_DRAFT 作用域不得携带 projectId");
                }
                if (blueprintVersionId == null || blueprintVersionId < 1) {
                    throw new IllegalArgumentException(
                            "CREATION_DRAFT 作用域必须提供已发布 blueprintVersionId");
                }
            }
        }
    }

    public static AigcGenerationProposalScope project(Long projectId) {
        return new AigcGenerationProposalScope(Kind.PROJECT, projectId, null, null);
    }

    /** {@code draftId} 为空表示请求服务端签发新草稿身份。 */
    public static AigcGenerationProposalScope creationDraft(
            String draftId, Long blueprintVersionId) {
        return new AigcGenerationProposalScope(
                Kind.CREATION_DRAFT, null, draftId, blueprintVersionId);
    }

    public boolean creationDraft() {
        return kind == Kind.CREATION_DRAFT;
    }
}
