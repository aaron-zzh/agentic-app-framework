package com.xuejiai.aaf.module.system.dict.domain;

import com.xuejiai.aaf.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 字典类型。 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict_type")
@SQLDelete(
        sql =
                "UPDATE sys_dict_type SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class DictType extends BaseEntity {

    /** 字典名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 字典类型编码，全局唯一 */
    @Column(name = "type", nullable = false, length = 100)
    private String type;

    /** 0 正常 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
