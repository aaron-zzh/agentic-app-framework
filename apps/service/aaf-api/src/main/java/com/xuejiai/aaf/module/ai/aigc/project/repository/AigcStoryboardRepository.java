package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcStoryboard;

public interface AigcStoryboardRepository extends CrudEntityRepository<AigcStoryboard> {
    List<AigcStoryboard> findByProjectIdOrderByCreateTimeDesc(Long projectId);
}
