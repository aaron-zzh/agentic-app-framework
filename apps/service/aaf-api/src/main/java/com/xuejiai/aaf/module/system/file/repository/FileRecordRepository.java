package com.xuejiai.aaf.module.system.file.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.file.domain.FileRecord;

/**
 * 文件记录仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface FileRecordRepository
        extends JpaRepository<FileRecord, Long>, JpaSpecificationExecutor<FileRecord> {

    Optional<FileRecord> findByKey(String key);
}
