package com.xuejiai.aaf.autodev.doc.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.autodev.doc.domain.AutodevDoc;

public interface AutodevDocRepository extends JpaRepository<AutodevDoc, Long> {

    Optional<AutodevDoc> findByFilePath(String filePath);

    List<AutodevDoc> findByStatusOrderByFilePath(String status);

    /** 全文检索（PostgreSQL tsvector） */
    @Query(
            value =
                    "SELECT * FROM autodev_doc WHERE deleted = false "
                            + "AND to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'')) @@ plainto_tsquery('simple', :q) "
                            + "ORDER BY ts_rank(to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'')), plainto_tsquery('simple', :q)) DESC "
                            + "LIMIT 20",
            nativeQuery = true)
    List<AutodevDoc> fullTextSearch(@Param("q") String query);
}
