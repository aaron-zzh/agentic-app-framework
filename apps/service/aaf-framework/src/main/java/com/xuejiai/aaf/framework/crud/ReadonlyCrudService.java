package com.xuejiai.aaf.framework.crud;

import java.util.List;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;

/**
 * 只读 CRUD 服务基类。
 *
 * <p>用于商城列表、字典、模板库等只读资源——支持 page/getById/options/exportData 等读操作， 不支持 create/update/delete（调用即抛
 * BusinessException(BAD_REQUEST)）。
 *
 * <p>v0.2 (N2)：替代原方案"业务子类继承 {@link BaseCrudService} + Void 类型 + override toEntity 抛
 * UnsupportedOperationException"的样板，子类只需提供 {@link #toVO}、{@link #getRepository}、{@link
 * #getSpecExecutor}、{@link #buildSpec}、{@link #entityName}。
 *
 * @param <E> 实体类型
 * @param <V> 视图对象类型
 * @param <P> 分页参数类型
 */
public abstract class ReadonlyCrudService<E extends BaseEntity, V, P extends PageParam>
        extends BaseCrudService<E, V, Void, Void, P> {

    @Override
    protected final E toEntity(Void dto) {
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只读资源不支持创建");
    }

    @Override
    protected final void updateEntity(E entity, Void dto) {
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只读资源不支持更新");
    }

    @Override
    public V create(Void request) {
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只读资源不支持创建");
    }

    @Override
    public V update(Long id, Void request) {
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只读资源不支持更新");
    }

    @Override
    public void delete(Long id) {
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只读资源不支持删除");
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只读资源不支持删除");
    }
}
