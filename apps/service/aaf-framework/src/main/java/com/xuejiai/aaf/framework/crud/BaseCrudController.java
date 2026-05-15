package com.xuejiai.aaf.framework.crud;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;

/**
 * 通用 CRUD Controller 基类。提供标准 REST 端点：分页查询、单条查询、创建、更新、删除、批量删除。
 *
 * <p>子类只需加 {@code @RestController}、{@code @RequestMapping}、{@code @Tag} 注解，
 * 并注入对应的 Service 即可获得完整 CRUD 能力。子类可覆写方法添加 OpenAPI 注解。
 *
 * @param <E> 实体类型
 * @param <V> 响应 VO 类型
 * @param <C> 创建 DTO 类型
 * @param <U> 更新 DTO 类型
 * @param <P> 分页查询 DTO 类型
 */
public abstract class BaseCrudController<
        E extends BaseEntity, V, C, U, P extends PageParam> {

    /** 子类提供 Service 实例。 */
    protected abstract BaseCrudService<E, V, C, U, P> getService();

    /** 分页查询。 */
    @GetMapping
    public Result<PageResult<V>> page(@Validated P request) {
        return Result.success(getService().page(request));
    }

    /** 查询详情。 */
    @GetMapping("/{id}")
    public Result<V> get(@PathVariable Long id) {
        return Result.success(getService().getById(id));
    }

    /** 创建。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<V> create(@Validated @RequestBody C request) {
        return Result.success(getService().create(request));
    }

    /** 更新。 */
    @PutMapping("/{id}")
    public Result<V> update(@PathVariable Long id, @Validated @RequestBody U request) {
        return Result.success(getService().update(id, request));
    }

    /** 删除。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        getService().delete(id);
        return Result.success();
    }

    /** 批量删除。 */
    @DeleteMapping
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        getService().deleteBatch(ids);
        return Result.success();
    }
}
