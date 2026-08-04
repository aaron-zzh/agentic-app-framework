package com.xuejiai.aaf.module.ai.aigc.execution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;

public interface AigcExecutionBindingRepository extends CrudEntityRepository<AigcExecutionBinding> {

    List<AigcExecutionBinding> findByActionKeyAndStatusOrderByPriorityDesc(
            String actionKey, String status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from AigcExecutionBinding binding where binding.id = :id")
    Optional<AigcExecutionBinding> findLockedById(@Param("id") Long id);
}
