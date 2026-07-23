package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShot;

public interface AigcShotRepository extends CrudEntityRepository<AigcShot> {
    List<AigcShot> findByStoryboardIdOrderByShotNo(Long storyboardId);

    @Modifying
    @Query("DELETE FROM AigcShot s WHERE s.storyboardId = :storyboardId")
    void deleteByStoryboardId(Long storyboardId);
}
