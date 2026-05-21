package com.xuejiai.aaf.module.system.dict.domain;

import com.xuejiai.aaf.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 字典数据。 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict_data")
@SQLDelete(
        sql =
                "UPDATE sys_dict_data SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class DictData extends BaseEntity {

    /** 字典类型编码（关联 DictType.type） */
    @Column(name = "dict_type", nullable = false, length = 100)
    private String dictType;

    /** 字典标签 */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** 字典键值 */
    @Column(name = "value", nullable = false, length = 100)
    private String value;

    /** 显示排序 */
    @Column(name = "sort", nullable = false)
    private Integer sort = 0;

    /** 0 正常 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;

    /** 标签颜色（default/primary/success/info/warning/danger） */
    @Column(name = "color_type", length = 50)
    private String colorType;

    /** 自定义 CSS 样式 */
    @Column(name = "css_class", length = 100)
    private String cssClass;
}
