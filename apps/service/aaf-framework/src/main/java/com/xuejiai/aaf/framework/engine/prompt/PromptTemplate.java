package com.xuejiai.aaf.framework.engine.prompt;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一提示词资产。
 *
 * <p>系统内部 Prompt、用户私有/公开模板和 Studio 系统模板共用 {@code ai_prompt_template}，由 {@code visibility}
 * 明确区分用途。用户记录由 BaseCrud 自动写入 owner/org/workspace；引擎只读取 {@link #VISIBILITY_ENGINE}。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_prompt_template")
@SQLDelete(
        sql =
                "UPDATE ai_prompt_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PromptTemplate extends BaseEntity {

    public static final String VISIBILITY_ENGINE = "ENGINE";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_SYSTEM = "SYSTEM";

    /** 模板名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 模板版本号。 */
    @Column(name = "template_version", nullable = false)
    private Integer templateVersion = 1;

    /** 正向提示词内容，变量使用 ${name} 语法。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 描述。 */
    @Column(length = 512)
    private String description;

    /** 已声明变量名 JSON 数组。 */
    @Column(columnDefinition = "TEXT")
    private String variables;

    /** 是否为当前激活版本。 */
    @Column(nullable = false)
    private Boolean active = true;

    /** 分类标签。 */
    @Column(length = 64)
    private String category;

    /** 封面地址。 */
    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    /** 资产类型：SYSTEM / PROMPT / IMAGE_GEN / VIDEO_GEN / COPYWRITING。 */
    @Column(nullable = false, length = 30)
    private String type = "PROMPT";

    /** 反向提示词。 */
    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    private String negativePrompt;

    /** 建议模型。 */
    @Column(length = 100)
    private String model;

    /** 建议生成宽度。 */
    private Integer width;

    /** 建议生成高度。 */
    private Integer height;

    /** 建议推理步数。 */
    private Integer steps;

    /** 建议随机种子。 */
    private Long seed;

    /** 明确的引擎用途或 Studio 可见目录。 */
    @Column(nullable = false, length = 16)
    private String visibility = VISIBILITY_PRIVATE;

    /** 使用次数。 */
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    /** 使用场景：SYSTEM / GENERATION / PROJECT。 */
    @Column(nullable = false, length = 20)
    private String scope = "GENERATION";
}
