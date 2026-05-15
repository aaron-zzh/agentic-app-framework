package com.xuejiai.aaf.framework.crud;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;

/**
 * 通用 CRUD Service 基类。提供分页查询、单条查询、创建、更新、删除、批量删除。
 *
 * <p>子类需实现转换方法和查询条件构建。
 *
 * @param <E> 实体类型
 * @param <V> 响应 VO 类型
 * @param <C> 创建 DTO 类型
 * @param <U> 更新 DTO 类型
 * @param <P> 分页查询 DTO 类型
 */
public abstract class BaseCrudService<
        E extends BaseEntity, V, C, U, P extends PageParam> {

    /** 子类提供 Repository 实例。 */
    protected abstract JpaRepository<E, Long> getRepository();

    /** 子类提供 JpaSpecificationExecutor（通常与 Repository 是同一个接口）。 */
    protected abstract JpaSpecificationExecutor<E> getSpecExecutor();

    /** 实体转 VO。 */
    protected abstract V toVO(E entity);

    /** 创建 DTO 转实体。 */
    protected abstract E toEntity(C createDTO);

    /** 更新 DTO 应用到已有实体。 */
    protected abstract void updateEntity(E entity, U updateDTO);

    /** 构建查询条件，默认无条件。子类可覆写。 */
    protected Specification<E> buildSpec(P pageDTO) {
        return (root, query, cb) -> null;
    }

    /** 默认排序，子类可覆写。 */
    protected Sort defaultSort() {
        return Sort.by("id").descending();
    }

    /** 分页查询。 */
    public PageResult<V> page(P request) {
        var pageable = request.toPageable(defaultSort());
        Page<E> page = getSpecExecutor().findAll(buildSpec(request), pageable);
        return new PageResult<>(page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 查询单条记录。 */
    public V getById(Long id) {
        return toVO(requireEntity(id));
    }

    /** 创建。 */
    @Transactional
    public V create(C request) {
        E entity = toEntity(request);
        getRepository().save(entity);
        return toVO(entity);
    }

    /** 更新。 */
    @Transactional
    public V update(Long id, U request) {
        E entity = requireEntity(id);
        updateEntity(entity, request);
        getRepository().save(entity);
        return toVO(entity);
    }

    /** 删除。 */
    @Transactional
    public void delete(Long id) {
        if (!getRepository().existsById(id)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, entityName() + "不存在");
        }
        getRepository().deleteById(id);
    }

    /** 批量删除。 */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        getRepository().deleteAllById(ids);
    }

    /** 实体名称，用于错误提示。子类可覆写。 */
    protected String entityName() {
        return "记录";
    }

    /** 根据 ID 查询实体，不存在则抛异常。 */
    protected E requireEntity(Long id) {
        return getRepository()
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, entityName() + "不存在"));
    }
}
