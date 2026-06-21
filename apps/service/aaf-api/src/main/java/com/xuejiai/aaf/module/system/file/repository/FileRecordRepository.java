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

    void deleteByKey(String key);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(f.size), 0) FROM FileRecord f WHERE f.uploaderId = :uploaderId AND f.deleted = false")
    long sumSizeByUploaderId(
            @org.springframework.data.repository.query.Param("uploaderId") Long uploaderId);
}
