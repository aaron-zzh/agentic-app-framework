package com.xuejiai.aaf.module.document.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.vo.DocListItemVO;

import jakarta.persistence.LockModeType;

public interface DocumentRepository
        extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByFilePath(String filePath);

    List<Document> findByStatusOrderByFilePath(String status);

    List<Document> findByOwnerIdAndStatusOrderByCreateTimeDesc(Long ownerId, String status);

    /** 查询用户文档列表（不含正文，只投影需要的字段） */
    @Query(
            "SELECT new com.xuejiai.aaf.module.document.vo.DocListItemVO(d.id, d.title, d.docType, d.publish, d.updateTime) "
                    + "FROM Document d WHERE d.ownerId = :ownerId AND d.status = 'active' AND d.deleted = false "
                    + "ORDER BY d.updateTime DESC")
    List<DocListItemVO> listByOwner(@Param("ownerId") Long ownerId);

    Optional<Document> findByIdAndOwnerIdAndStatus(Long id, Long ownerId, String status);

    List<Document> findAllByIdInAndOwnerIdAndStatus(
            Collection<Long> ids, Long ownerId, String status);

    List<Document> findByPublishOrderByUpdateTimeDesc(String publish);

    /** 统计用户文档数量 */
    long countByOwnerIdAndStatus(Long ownerId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id and d.deleted = false")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);

    /** 全文检索当前版本（PostgreSQL tsvector）。 */
    @Query(
            value =
                    "SELECT d.* FROM doc_document d "
                            + "JOIN doc_document_version v ON v.id = d.current_version_id AND v.document_id = d.id "
                            + "WHERE d.deleted = false AND d.owner_id = :ownerId "
                            + "AND d.org_id = :orgId AND (d.workspace_id IS NULL OR d.workspace_id = :workspaceId) "
                            + "AND d.status = 'active' "
                            + "AND to_tsvector('simple', coalesce(d.title,'') || ' ' || coalesce(v.content,'')) "
                            + "@@ plainto_tsquery('simple', :q) "
                            + "ORDER BY ts_rank(to_tsvector('simple', coalesce(d.title,'') || ' ' || coalesce(v.content,'')), "
                            + "plainto_tsquery('simple', :q)) DESC LIMIT 20",
            nativeQuery = true)
    List<Document> fullTextSearch(
            @Param("q") String query,
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId);
}
