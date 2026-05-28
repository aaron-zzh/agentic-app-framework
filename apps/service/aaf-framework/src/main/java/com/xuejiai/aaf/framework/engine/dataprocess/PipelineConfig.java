package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** 管道配置——定义字段映射、清洗规则、AI 增强、路由目标。 */
@Getter
@Setter
@Builder
public class PipelineConfig {

    /** 管道 ID */
    private String pipelineId;

    /** 来源平台（douyin / xiaohongshu 等） */
    private String platform;

    /** 来源数据类型（video / note / comment） */
    private String sourceType;

    /** 字段映射规则：源字段路径 → 目标字段名 */
    private Map<String, String> fieldMappings;

    /** 清洗规则 */
    private CleanRules cleanRules;

    /** AI 增强配置 */
    private List<EnrichmentConfig> enrichments;

    /** 路由目标 */
    private RouteTarget routeTarget;

    /** 清洗规则 */
    @Getter
    @Setter
    @Builder
    public static class CleanRules {
        /** 去重字段（如 "id"） */
        private String deduplicateBy;

        /** 过滤条件（如 "stats.likes > 100"） */
        private List<String> filters;

        /** 必填字段（缺失则丢弃） */
        private List<String> requiredFields;
    }

    /** AI 增强配置 */
    @Getter
    @Setter
    @Builder
    public static class EnrichmentConfig {
        /** 增强类型：summary / classification / sentiment / tags */
        private String type;

        /** 输入字段（用于 AI 处理的源字段） */
        private String inputField;

        /** 输出字段（AI 结果写入的目标字段） */
        private String outputField;

        /** 额外参数（如分类列表、摘要长度） */
        private Map<String, String> params;
    }

    /** 路由目标 */
    @Getter
    @Setter
    @Builder
    public static class RouteTarget {
        /** 目标类型：custom_table / knowledge_base */
        private String type;

        /** 目标表名或知识库 ID */
        private String target;
    }
}
