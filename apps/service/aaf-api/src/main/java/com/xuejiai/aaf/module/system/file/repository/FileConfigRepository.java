package com.xuejiai.aaf.module.system.file.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;

/**
 * 文件存储配置仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface FileConfigRepository extends CrudEntityRepository<FileConfig> {

    Optional<FileConfig> findByMasterTrueAndStatus(String status);

    @Modifying
    @Query(
            "UPDATE FileConfig f SET f.master = false "
                    + "WHERE f.master = true AND f.deleted = false AND f.id <> :configId")
    void clearMasterExcept(Long configId);
}
