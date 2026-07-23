package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContent;

public interface AigcContentRepository extends CrudEntityRepository<AigcContent> {
    List<AigcContent> findByProjectIdOrderByCreateTimeDesc(Long projectId);

    List<AigcContent> findByProjectIdAndType(Long projectId, String type);
}
