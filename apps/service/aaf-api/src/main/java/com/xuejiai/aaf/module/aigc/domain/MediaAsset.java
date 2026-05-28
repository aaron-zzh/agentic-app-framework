package com.xuejiai.aaf.module.aigc.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 素材库实体。
 *
 * <p>关联关系：
 *
 * <ul>
 *   <li>类型枚举：{@link MediaAssetType}
 *   <li>标签关联：{@link com.xuejiai.aaf.module.aigc.domain.MediaAssetTag}
 *   <li>变体关联：{@link com.xuejiai.aaf.module.aigc.domain.MediaAssetVariant}
 * </ul>
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "media_asset")
@SQLDelete(
        sql =
                "UPDATE media_asset SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class MediaAsset extends BaseEntity {

    /** 素材名称 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 素材类型，枚举 {@link MediaAssetType} */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MediaAssetType type;

    /** 素材文件 URL */
    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    /** 缩略图 URL */
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /** 文件大小（字节） */
    @Column(name = "size")
    private Long size;

    /** 图片/视频宽度（像素） */
    @Column(name = "width")
    private Integer width;

    /** 图片/视频高度（像素） */
    @Column(name = "height")
    private Integer height;

    /** 时长（秒），音视频素材使用 */
    @Column(name = "duration", precision = 10, scale = 2)
    private BigDecimal duration;

    /** 生成参数（JSON），记录 AI 生成时的 prompt、模型等信息 */
    @Column(name = "generation_params", columnDefinition = "JSONB")
    private String generationParams;

    /** 标签，逗号分隔 */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 所属分类 ID，关联 {@link MediaCategory} */
    @Column(name = "category_id")
    private Long categoryId;

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
