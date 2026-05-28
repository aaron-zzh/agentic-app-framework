package com.xuejiai.aaf.module.system.file.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.system.file.domain.FileConfig;

/**
 * 文件存储配置仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface FileConfigRepository extends JpaRepository<FileConfig, Long> {

    Optional<FileConfig> findByMasterTrue();

    @Modifying
    @Query("UPDATE FileConfig f SET f.master = false WHERE f.master = true")
    void clearMaster();
}
