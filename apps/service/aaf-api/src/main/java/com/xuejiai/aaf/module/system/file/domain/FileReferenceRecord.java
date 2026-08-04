package com.xuejiai.aaf.module.system.file.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 物理文件的跨业务引用登记。 */
@Getter
@Setter
@Entity
@Table(name = "sys_file_reference")
@SQLDelete(
        sql =
                "UPDATE sys_file_reference SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class FileReferenceRecord extends BaseEntity {

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "ref_type", nullable = false, length = 64)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "ref_field", nullable = false, length = 64)
    private String refField;

    @Column(name = "purpose", length = 64)
    private String purpose;
}
