package com.xuejiai.aaf.framework.engine.dataprocess.table;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 动态数据表列定义。 */
@Getter
@Setter
@Entity
@Table(name = "data_column_definition")
public class DataColumnDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "display_name", length = 128)
    private String displayName;

    /** string/text/integer/decimal/boolean/timestamp/json */
    @Column(name = "column_type", nullable = false, length = 32)
    private String columnType;

    @Column(nullable = false)
    private boolean nullable = true;

    @Column(name = "unique_col", nullable = false)
    private boolean uniqueCol = false;

    @Column(name = "default_value", length = 256)
    private String defaultValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
