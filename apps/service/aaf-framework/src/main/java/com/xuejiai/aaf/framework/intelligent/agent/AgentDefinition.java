/**
 * Agent 定义实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Agent 元数据定义，持久化到数据库。 运行时通过此配置构建 AgentScope ReActAgent 实例。 */
@Getter
@Setter
@Entity
@Table(name = "ai_agent_definition")
public class AgentDefinition extends BaseEntity {

    /** Agent 唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String agentId;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述 */
    @Column(length = 512)
    private String description;

    /** 系统提示词（或引用 PromptTemplate 名称） */
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    /** 使用的模型 ID */
    @Column(length = 64)
    private String modelId;

    /** 能力声明（JSON 数组，如 ["code_review","data_analysis"]） */
    @Column(columnDefinition = "TEXT")
    private String capabilities;

    /** 绑定的工具列表（JSON 数组，如 ["weather","calculator"]） */
    @Column(columnDefinition = "TEXT")
    private String tools;

    /** MCP 服务器 URL 列表（JSON 数组） */
    @Column(columnDefinition = "TEXT")
    private String mcpServers;

    /** 最大迭代次数（ReAct 循环上限） */
    @Column(nullable = false)
    private Integer maxIterations = 10;

    /** 超时时间（秒） */
    @Column(nullable = false)
    private Integer timeoutSeconds = 120;

    /** 状态：active / inactive / archived */
    @Column(nullable = false, length = 16)
    private String status = "active";
}
