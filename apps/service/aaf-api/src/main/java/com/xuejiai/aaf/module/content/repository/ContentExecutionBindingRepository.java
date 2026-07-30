package com.xuejiai.aaf.module.content.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.content.domain.ContentExecutionBinding;

/**
 * 内容动作执行绑定仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface ContentExecutionBindingRepository
        extends CrudEntityRepository<ContentExecutionBinding> {

    List<ContentExecutionBinding> findByActionKeyAndStatusOrderByPriorityDesc(
            String actionKey, String status);

    List<ContentExecutionBinding> findByStatusOrderByActionKeyAscPriorityDesc(String status);
}
