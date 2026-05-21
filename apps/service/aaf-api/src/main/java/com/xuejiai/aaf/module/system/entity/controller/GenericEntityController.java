package com.xuejiai.aaf.module.system.entity.controller;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.entity.service.EntityDefService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 通用实体数据 CRUD 控制器（动态表）。 */
@Tag(name = "通用实体数据")
@RestController
@RequestMapping("/api/data/{slug}")
@RequiredArgsConstructor
public class GenericEntityController {

    private final EntityDefService entityDefService;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "查询实体数据列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable String slug) {
        var tableName = resolveTable(slug);
        var sql =
                "SELECT * FROM %s WHERE deleted = false ORDER BY create_time DESC"
                        .formatted(tableName);
        return Result.success(jdbcTemplate.queryForList(sql));
    }

    @Operation(summary = "查询单条实体数据")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable String slug, @PathVariable Long id) {
        var tableName = resolveTable(slug);
        var sql = "SELECT * FROM %s WHERE id = ? AND deleted = false".formatted(tableName);
        var rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "数据不存在");
        }
        return Result.success(rows.getFirst());
    }

    @Operation(summary = "创建实体数据")
    @PostMapping
    public Result<Map<String, Object>> create(
            @PathVariable String slug, @RequestBody Map<String, Object> body) {
        var tableName = resolveTable(slug);
        if (body.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        var columns = body.keySet().stream().map(this::sanitize).toList();
        var placeholders = columns.stream().map(c -> "?").toList();
        var sql =
                "INSERT INTO %s (%s) VALUES (%s) RETURNING *"
                        .formatted(
                                tableName,
                                String.join(", ", columns),
                                String.join(", ", placeholders));
        var row = jdbcTemplate.queryForMap(sql, body.values().toArray());
        return Result.success(row);
    }

    @Operation(summary = "更新实体数据")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(
            @PathVariable String slug,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        var tableName = resolveTable(slug);
        if (body.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        var setClauses = body.keySet().stream().map(k -> sanitize(k) + " = ?").toList();
        var values = new java.util.ArrayList<>(body.values().stream().toList());
        values.add(id);
        var sql =
                "UPDATE %s SET %s, update_time = CURRENT_TIMESTAMP WHERE id = ? AND deleted = false RETURNING *"
                        .formatted(tableName, String.join(", ", setClauses));
        var rows = jdbcTemplate.queryForList(sql, values.toArray());
        if (rows.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "数据不存在");
        }
        return Result.success(rows.getFirst());
    }

    @Operation(summary = "删除实体数据（软删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String slug, @PathVariable Long id) {
        var tableName = resolveTable(slug);
        var sql =
                "UPDATE %s SET deleted = true WHERE id = ? AND deleted = false"
                        .formatted(tableName);
        var affected = jdbcTemplate.update(sql, id);
        if (affected == 0) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "数据不存在");
        }
        return Result.success();
    }

    // ========== 内部方法 ==========

    /** 校验 slug 对应的实体定义存在且启用，返回表名 */
    private String resolveTable(String slug) {
        var entityDef = entityDefService.getBySlug(slug);
        if (!entityDef.getEnabled()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "实体已禁用: " + slug);
        }
        return "data_" + slug;
    }

    private String sanitize(String identifier) {
        if (identifier == null || !identifier.matches("^[a-z][a-z0-9_]*$")) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "非法字段名: " + identifier);
        }
        return identifier;
    }
}
