package com.xuejiai.aaf.module.system.file.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.file.domain.FileReferenceRecord;

/** 文件引用仓储。 */
public interface FileReferenceRepository extends JpaRepository<FileReferenceRecord, Long> {

    Optional<FileReferenceRecord> findByFileIdAndRefTypeAndRefIdAndRefField(
            Long fileId, String refType, Long refId, String refField);

    long countByFileId(Long fileId);
}
