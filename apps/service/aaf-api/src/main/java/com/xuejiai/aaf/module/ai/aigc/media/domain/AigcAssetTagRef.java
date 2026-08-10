package com.xuejiai.aaf.module.ai.aigc.media.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Asset 与 Tag 的内部关系记录。 */
@Getter
@Setter
@Entity
@IdClass(AigcAssetTagRef.Id.class)
@Table(name = "aigc_asset_tag_ref")
public class AigcAssetTagRef {

    @jakarta.persistence.Id
    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @jakarta.persistence.Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        private Long assetId;
        private Long tagId;
    }
}
