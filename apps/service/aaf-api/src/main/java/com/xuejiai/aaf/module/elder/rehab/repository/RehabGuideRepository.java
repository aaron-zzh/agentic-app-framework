package com.xuejiai.aaf.module.elder.rehab.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.rehab.domain.RehabGuide;

/** 康复指导仓储。 */
public interface RehabGuideRepository extends CrudEntityRepository<RehabGuide> {

    List<RehabGuide> findAllByStatusOrderByIdDesc(String status);

    Optional<RehabGuide> findByIdAndStatus(Long id, String status);
}
