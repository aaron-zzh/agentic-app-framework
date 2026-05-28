package com.xuejiai.aaf.framework.engine.tool.generator;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AI 生成工具持久化实体。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_generated_tool",
        indexes = {
            @Index(columnList = "name", unique = true),
            @Index(columnList = "creatorUserId"),
            @Index(columnList = "visibility")
        })
public class GeneratedTool extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(length = 256)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String parametersJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(nullable = false)
    private Long creatorUserId;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private ToolBlueprint.Visibility visibility = ToolBlueprint.Visibility.PRIVATE;

    @Column(nullable = false, length = 16)
    private String status = "active";
}
