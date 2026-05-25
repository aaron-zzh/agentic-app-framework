package com.xuejiai.aaf.framework.engine.dataprocess.table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 动态数据表定义。 */
@Getter
@Setter
@Entity
@Table(name = "data_table_definition")
public class DataTableDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(length = 512)
    private String description;

    /** 实际 PG 表名（data_{slug}） */
    @Column(name = "table_name", nullable = false, unique = true, length = 80)
    private String tableName;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "table_id")
    @OrderBy("sortOrder")
    private List<DataColumnDefinition> columns = new ArrayList<>();
}
