package com.xuejiai.aaf.framework.crud;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import com.xuejiai.aaf.common.model.BaseEntity;

/** BaseCrud 实体的统一 Repository 契约。 */
@NoRepositoryBean
public interface CrudEntityRepository<E extends BaseEntity>
        extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {}
