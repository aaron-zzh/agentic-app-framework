package com.xuejiai.aaf.module.ai.aigc.media.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 素材元素——对多个同主题素材（图片/视频/文案等）的分组容器。
 *
 * <p>一个元素对应一个角色、场景或道具主题，下挂多条 {@link MediaAsset}（通过 element_id 关联）。
 *
 * @author AaronZZH
 */
@Getter
@Setter
@Entity
@Table(name = "media_element")
@SQLDelete(
        sql =
                "UPDATE media_element SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class MediaElement extends BaseEntity {

    /** 元素名称 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 元素描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 封面图 URL */
    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
