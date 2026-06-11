package com.xuejiai.aaf.module.ai.aigc.media.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 素材组——每次 AIGC 生成任务对应一个组，组内含一张或多张素材。 */
@Getter
@Setter
@Entity
@Table(name = "media_asset_group")
@SQLDelete(sql = "UPDATE media_asset_group SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class MediaAssetGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version private Integer version = 0;

    /** 组名，由 prompt 截取或文件名生成 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 封面图（取组内第一张素材 URL） */
    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    /** 组内素材数量（冗余） */
    @Column(name = "asset_count", nullable = false)
    private Integer assetCount = 0;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    @PreUpdate
    void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
