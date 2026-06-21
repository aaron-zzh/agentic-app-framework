package com.xuejiai.aaf.framework.crud;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

/**
 * 通用 CRUD Controller 基类。提供标准 REST 端点：分页查询、单条查询、创建、更新、删除、批量删除。
 *
 * <p>子类只需加 {@code @RestController}、{@code @RequestMapping}、{@code @Tag} 注解， 并注入对应的 Service 即可获得完整
 * CRUD 能力。子类可覆写方法添加 OpenAPI 注解。
 *
 * @param <E> 实体类型
 * @param <V> 响应 VO 类型
 * @param <C> 创建 DTO 类型
 * @param <U> 更新 DTO 类型
 * @param <P> 分页查询 DTO 类型
 */
public abstract class BaseCrudController<E extends BaseEntity, V, C, U, P extends PageParam> {

    /** 子类提供 Service 实例。 */
    protected abstract BaseCrudService<E, V, C, U, P> getService();

    /**
     * 基础分页查询。
     *
     * <p>保留给简单列表、旧接口兼容和不需要详情快速切换的场景。通用实体列表引擎应优先调用 {@link #queryWindow(PageParam, String)}，以获得
     * ids、queryToken 和 fieldSet。
     */
    @Operation(summary = "基础分页查询", description = "用于简单列表和旧接口兼容；通用实体列表引擎应优先使用 /_query。")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<PageResult<V>> page(@Validated P request) {
        return Result.success(getService().page(request));
    }

    /**
     * 查询窗口。
     *
     * <p>用于通用实体列表引擎，返回当前窗口数据、记录 ID 列表、查询上下文 token 和字段集信息， 支撑列表进入详情、上一条/下一条切换、列表缓存秒开详情以及后续权限版本校验。
     *
     * <p>当 {@code pageSize=-1} 时返回过滤后的完整窗口，由前端进行本地分页；否则按 {@code pageNo/pageSize} 返回服务端分页窗口。
     */
    @Operation(
            summary = "查询窗口",
            description = "用于通用实体列表引擎，返回列表数据、ids、queryToken 和 fieldSet；pageSize=-1 时返回完整窗口。")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/_query")
    public Result<PageResult<V>> queryWindow(
            @Validated P request,
            @Parameter(description = "字段集：list/detail/picker/export，默认 list")
                    @RequestParam(defaultValue = "list")
                    String fieldSet) {
        return Result.success(getService().queryWindow(request, fieldSet));
    }

    /**
     * 查询详情。
     *
     * <p>支持查询窗口上下文：{@code queryToken} 用于后续校验窗口与权限版本，{@code fieldSet} 用于区分 list/detail/picker 等字段集。
     */
    @Operation(summary = "查询详情", description = "支持 queryToken 和 fieldSet，用于列表窗口进入详情后的缓存复用与字段集切换。")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<V> get(
            @Parameter(description = "记录 ID") @PathVariable Long id,
            @Parameter(description = "查询窗口 token，可为空") @RequestParam(required = false)
                    String queryToken,
            @Parameter(description = "字段集：list/detail/picker/export，默认 detail")
                    @RequestParam(defaultValue = "detail")
                    String fieldSet) {
        return Result.success(getService().getById(id, queryToken, fieldSet));
    }

    /**
     * 批量读取。
     *
     * <p>用于详情页预取上一条/下一条、批量操作前确认等读场景。返回顺序按请求 ids 尽量保持一致， 不存在或不可见记录由服务层过滤。
     */
    @Operation(summary = "批量读取", description = "按 ID 批量读取记录，用于详情相邻记录预取和批量操作前确认。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_batch-read")
    public Result<List<V>> batchRead(@Validated @RequestBody BatchReadRequest request) {
        return Result.success(getService().batchRead(request.ids(), request.fieldSet()));
    }

    /**
     * 选择器选项。
     *
     * <p>用于关系字段、下拉选择器、弹窗选择器。默认返回有限数量的记录选项，子类可覆写 Service 的搜索条件和显示名。
     */
    @Operation(summary = "选择器选项", description = "用于关系字段、下拉选择器和弹窗选择器；子类可覆写搜索条件和显示名。")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/_options")
    public Result<List<CrudOption>> options(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String q,
            @Parameter(description = "返回数量，默认 20，最大 100") @RequestParam(defaultValue = "20")
                    Integer limit) {
        return Result.success(getService().options(q, limit));
    }

    /**
     * 通用 CRUD 元数据。
     *
     * <p>用于前端实体引擎了解当前资源的实体标识、字段集和可用通用操作。
     */
    @Operation(summary = "CRUD 元数据", description = "返回实体标识、实体名称、字段集和通用操作清单。")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/_meta")
    public Result<CrudMeta> meta() {
        return Result.success(getService().meta());
    }

    /**
     * 导出数据。
     *
     * <p>默认返回过滤后的完整 export 字段集数据；文件流导出可由子类覆写。
     */
    @Operation(summary = "导出数据", description = "默认返回过滤后的完整 export 字段集数据；文件流导出可由子类覆写。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_export")
    public Result<PageResult<V>> exportData(@Validated @RequestBody P request) {
        return Result.success(getService().exportData(request));
    }

    /**
     * JSON 导入。
     *
     * <p>默认未启用，业务子类确认导入映射、校验和事务语义后覆写 Service 实现。
     */
    @Operation(summary = "导入数据", description = "默认未启用；业务子类确认导入映射、校验和事务语义后覆写 Service 实现。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_import")
    public Result<CrudImportResult> importRows(
            @Validated @RequestBody CrudImportRequest<C> request) {
        return Result.success(getService().importRows(request));
    }

    /**
     * 批量删除。
     *
     * <p>POST 形式用于规避部分客户端或网关对 DELETE body 支持不稳定的问题。
     */
    @Operation(summary = "批量删除", description = "POST 形式的批量删除，用于规避部分客户端或网关对 DELETE body 支持不稳定的问题。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_batch-delete")
    public Result<Void> batchDelete(@Validated @RequestBody CrudIdsRequest request) {
        getService().deleteBatch(request.ids());
        return Result.success();
    }

    /**
     * 分组聚合。
     *
     * <p>默认未启用，业务子类确认字段白名单、聚合函数和权限过滤后覆写 Service 实现。
     */
    @Operation(summary = "分组聚合", description = "默认未启用；业务子类确认字段白名单、聚合函数和权限过滤后覆写 Service 实现。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_group")
    public Result<List<CrudGroupResult>> group(@Validated @RequestBody CrudGroupRequest request) {
        return Result.success(getService().group(request));
    }

    /**
     * 创建/更新前预校验。
     *
     * <p>用于唯一性、业务规则、AI 生成数据预检查等不落库校验。
     */
    @Operation(summary = "预校验", description = "用于唯一性、业务规则、AI 生成数据预检查等不落库校验。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_validate")
    public Result<CrudValidationResult> validate(@Validated @RequestBody C request) {
        return Result.success(getService().validate(request));
    }

    /**
     * 归档。
     *
     * <p>默认采用逻辑删除语义；如业务有 archived 状态，子类应覆写 Service 实现。
     */
    @Operation(summary = "归档", description = "默认采用逻辑删除语义；如业务有 archived 状态，子类应覆写 Service 实现。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_archive")
    public Result<Void> archive(@Validated @RequestBody CrudIdsRequest request) {
        getService().archive(request.ids());
        return Result.success();
    }

    @Operation(summary = "恢复", description = "默认未启用；逻辑删除恢复需要绕过默认过滤，业务子类确认后覆写 Service 实现。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/_restore")
    public Result<Void> restore(@Validated @RequestBody CrudIdsRequest request) {
        getService().restore(request.ids());
        return Result.success();
    }

    /** 创建。 */
    @Operation(summary = "创建记录")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<V> create(@Validated @RequestBody C request) {
        return Result.success(getService().create(request));
    }

    /** 更新。 */
    @Operation(summary = "更新记录")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public Result<V> update(
            @Parameter(description = "记录 ID") @PathVariable Long id,
            @Validated @RequestBody U request) {
        return Result.success(getService().update(id, request));
    }

    /** 删除。 */
    @Operation(summary = "删除记录")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "记录 ID") @PathVariable Long id) {
        getService().delete(id);
        return Result.success();
    }

    /** 批量删除。 */
    @Operation(summary = "批量删除记录", description = "兼容旧接口；推荐新调用使用 POST /_batch-delete。")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        getService().deleteBatch(ids);
        return Result.success();
    }
}
