package com.xuejiai.aaf.module.aigc.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 素材标签。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "media_tag")
@SQLDelete(
        sql =
                "UPDATE media_tag SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class MediaTag extends BaseEntity {

    /** 标签名称 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 标签颜色（十六进制，如 #FF5733） */
    @Column(name = "color", length = 20)
    private String color;
}
