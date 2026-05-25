package com.xuejiai.aaf.framework.engine.dataprocess.table;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

/**
 * 动态数据表 REST API——通用 CRUD 接口。
 */
@RestController
@RequestMapping("/api/v1/data-tables")
@RequiredArgsConstructor
public class DataTableController {

    private final DynamicTableService tableService;

    // ========== 表定义管理 ==========

    @PostMapping
    public DataTableDefinition createTable(@RequestBody CreateTableRequest req) {
        var columns = req.columns().stream().map(c -> {
            var col = new DataColumnDefinition();
            col.setName(c.name());
            col.setDisplayName(c.displayName());
            col.setColumnType(c.type());
            col.setNullable(c.nullable() == null || c.nullable());
            col.setUniqueCol(c.unique() != null && c.unique());
            col.setDefaultValue(c.defaultValue());
            return col;
        }).toList();
        return tableService.createTable(req.slug(), req.displayName(), req.description(), columns);
    }

    @GetMapping
    public List<DataTableDefinition> listTables() {
        return tableService.getTableRepository().findAll();
    }

    @GetMapping("/{slug}")
    public DataTableDefinition getTable(@PathVariable String slug) {
        return tableService.getTable(slug);
    }

    // ========== 数据 CRUD ==========

    @PostMapping("/{slug}/rows")
    public Object insertRows(@PathVariable String slug, @RequestBody Object body) {
        if (body instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) list;
            var count = tableService.insertBatch(slug, rows);
            return Map.of("inserted", count);
        }
        @SuppressWarnings("unchecked")
        var row = (Map<String, Object>) body;
        return tableService.insertRow(slug, row);
    }

    @GetMapping("/{slug}/rows")
    public List<Map<String, Object>> queryRows(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Map<String, Object> filters) {
        // 移除分页参数
        if (filters != null) {
            filters.remove("page");
            filters.remove("size");
        }
        return tableService.queryRows(slug, page, size, filters);
    }

    @PutMapping("/{slug}/rows/{id}")
    public void updateRow(@PathVariable String slug, @PathVariable Long id,
                          @RequestBody Map<String, Object> fields) {
        tableService.updateRow(slug, id, fields);
    }

    @DeleteMapping("/{slug}/rows/{id}")
    public void deleteRow(@PathVariable String slug, @PathVariable Long id) {
        tableService.deleteRow(slug, id);
    }

    // ========== DTO ==========

    record CreateTableRequest(
            String slug,
            String displayName,
            String description,
            List<ColumnDef> columns) {}

    record ColumnDef(
            String name,
            String displayName,
            String type,
            Boolean nullable,
            Boolean unique,
            String defaultValue) {}
}
