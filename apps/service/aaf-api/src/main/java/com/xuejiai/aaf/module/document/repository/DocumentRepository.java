package com.xuejiai.aaf.module.document.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.vo.DocListItemVO;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByFilePath(String filePath);

    List<Document> findByStatusOrderByFilePath(String status);

    List<Document> findByOwnerIdAndStatusOrderByCreateTimeDesc(Long ownerId, String status);

    /** 查询用户文档列表（不含正文，只投影需要的字段） */
    @Query(
            "SELECT new com.xuejiai.aaf.module.document.vo.DocListItemVO(d.id, d.title, d.docType, d.publish, d.updateTime) "
                    + "FROM Document d WHERE d.ownerId = :ownerId AND d.status = 'active' AND d.deleted = false "
                    + "ORDER BY d.updateTime DESC")
    List<DocListItemVO> listByOwner(@Param("ownerId") Long ownerId);

    List<Document> findByPublishOrderByUpdateTimeDesc(String publish);

    /** 统计用户文档数量 */
    long countByOwnerIdAndStatus(Long ownerId, String status);

    /** 全文检索（PostgreSQL tsvector） */
    @Query(
            value =
                    "SELECT * FROM doc_document WHERE deleted = false "
                            + "AND to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'')) @@ plainto_tsquery('simple', :q) "
                            + "ORDER BY ts_rank(to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'')), plainto_tsquery('simple', :q)) DESC "
                            + "LIMIT 20",
            nativeQuery = true)
    List<Document> fullTextSearch(@Param("q") String query);
}
