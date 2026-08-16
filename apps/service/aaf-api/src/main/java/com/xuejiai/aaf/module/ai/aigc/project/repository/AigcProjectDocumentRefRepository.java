package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentRef;

public interface AigcProjectDocumentRefRepository
        extends JpaRepository<AigcProjectDocumentRef, Long> {

    List<AigcProjectDocumentRef> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    Optional<AigcProjectDocumentRef> findByProjectIdAndDocumentVersionIdAndRole(
            Long projectId, Long documentVersionId, String role);

    @Query(
            """
            select distinct reference.documentVersionId
            from AigcProjectDocumentRef reference, AigcProject project
            where project.id = reference.projectId
              and reference.ownerId = :ownerId
              and reference.orgId = :orgId
              and (
                    (:workspaceId is null and reference.workspaceId is null)
                    or (:workspaceId is not null and (reference.workspaceId is null or reference.workspaceId = :workspaceId))
                  )
              and project.ownerId = :ownerId
              and project.orgId = :orgId
              and (
                    (:workspaceId is null and project.workspaceId is null)
                    or (:workspaceId is not null and (project.workspaceId is null or project.workspaceId = :workspaceId))
                  )
              and project.deleted = false
              and (:projectId is null or project.id = :projectId)
            """)
    Set<Long> findLinkedDocumentIds(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId,
            @Param("projectId") Long projectId);

    @Query(
            """
            select distinct reference.documentVersionId as documentId,
                            project.id as projectId,
                            project.name as projectName
            from AigcProjectDocumentRef reference, AigcProject project
            where project.id = reference.projectId
              and reference.ownerId = :ownerId
              and reference.orgId = :orgId
              and (
                    (:workspaceId is null and reference.workspaceId is null)
                    or (:workspaceId is not null and (reference.workspaceId is null or reference.workspaceId = :workspaceId))
                  )
              and project.ownerId = :ownerId
              and project.orgId = :orgId
              and (
                    (:workspaceId is null and project.workspaceId is null)
                    or (:workspaceId is not null and (project.workspaceId is null or project.workspaceId = :workspaceId))
                  )
              and project.deleted = false
              and reference.documentVersionId in :documentIds
            order by reference.documentVersionId, project.id
            """)
    List<DocumentProjectRow> findDocumentProjects(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId,
            @Param("documentIds") Collection<Long> documentIds);

    interface DocumentProjectRow {

        Long getDocumentId();

        Long getProjectId();

        String getProjectName();
    }
}
