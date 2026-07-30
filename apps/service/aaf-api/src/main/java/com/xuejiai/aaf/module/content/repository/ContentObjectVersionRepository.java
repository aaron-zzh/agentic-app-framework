package com.xuejiai.aaf.module.content.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.content.domain.ContentObjectVersion;

/**
 * 内容项目对象版本仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface ContentObjectVersionRepository extends CrudEntityRepository<ContentObjectVersion> {

    List<ContentObjectVersion> findByObjectIdOrderByVersionNoDesc(Long objectId);

    List<ContentObjectVersion> findByObjectIdAndStatus(Long objectId, String status);

    Optional<ContentObjectVersion> findFirstByObjectIdOrderByVersionNoDesc(Long objectId);

    Optional<ContentObjectVersion> findByExecutionRunId(Long executionRunId);
}
