package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/**
 * 业务域当前事实快照端口，供 PromptAssembler 在编译 Assistant System Prompt 时装配 {@code DOMAIN_CONTEXT} 层。
 *
 * <p>与业务域无关：框架只约定"受信范围 → 一段已渲染文本"，具体取哪些事实、如何渲染由业务域实现。 实现必须只返回结构化、体积受限的事实，用户自由文本必须标注为不可信输入； 详情通过只读
 * Tool 按需读取，不整体注入 Prompt。当前范围不属于该业务域时返回 {@link Optional#empty()}。
 */
public interface DomainContextPort {

    Optional<DomainContext> load(Scope scope);

    /** 受信范围，全部来自服务端冻结的执行意图与调用命令，不来自模型或客户端自由输入。 */
    record Scope(TenantId tenantId, UserId userId, Long workspaceId, Long projectId) {

        public Scope {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(userId, "userId 不能为空");
            if (workspaceId != null && workspaceId < 1) {
                throw new IllegalArgumentException("workspaceId 必须为正数");
            }
            if (projectId != null && projectId < 1) {
                throw new IllegalArgumentException("projectId 必须为正数");
            }
        }
    }

    /** 渲染后的领域事实层；{@code sourceKey/sourceVersion} 进入 Prompt 层摘要，用于审计该次执行看到的事实版本。 */
    record DomainContext(String sourceKey, String sourceVersion, String content) {

        public DomainContext {
            Objects.requireNonNull(sourceKey, "sourceKey 不能为空");
            Objects.requireNonNull(sourceVersion, "sourceVersion 不能为空");
            Objects.requireNonNull(content, "content 不能为空");
            if (content.isBlank()) {
                throw new IllegalArgumentException("content 不能为空白");
            }
        }
    }
}
