package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentReferenceType;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentRef;

public interface AigcProjectDocumentRefRepository
        extends JpaRepository<AigcProjectDocumentRef, Long> {

    List<AigcProjectDocumentRef> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    Optional<AigcProjectDocumentRef> findByProjectIdAndReferenceTypeAndDocumentIdAndRole(
            Long projectId,
            AigcProjectDocumentReferenceType referenceType,
            Long documentId,
            String role);

    Optional<AigcProjectDocumentRef>
            findByProjectIdAndReferenceTypeAndSelectedDocumentVersionIdAndRole(
                    Long projectId,
                    AigcProjectDocumentReferenceType referenceType,
                    Long selectedDocumentVersionId,
                    String role);

    @Query(
            """
            select distinct reference.documentId
            from AigcProjectDocumentRef reference, AigcProject project
            where project.id = reference.projectId
              and reference.referenceType = :referenceType
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
            @Param("referenceType") AigcProjectDocumentReferenceType referenceType,
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId,
            @Param("projectId") Long projectId);

    @Query(
            """
            select distinct reference.selectedDocumentVersionId
            from AigcProjectDocumentRef reference, AigcProject project
            where project.id = reference.projectId
              and reference.referenceType = :referenceType
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
    Set<Long> findLinkedDocumentVersionIds(
            @Param("referenceType") AigcProjectDocumentReferenceType referenceType,
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId,
            @Param("projectId") Long projectId);

    @Query(
            """
            select reference.documentId as documentId,
                   reference.selectedDocumentVersionId as documentVersionId,
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
            order by project.id, reference.id
            """)
    List<DocumentProjectRow> findDocumentProjects(
            @Param("ownerId") Long ownerId,
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId);

    interface DocumentProjectRow {

        Long getDocumentId();

        Long getDocumentVersionId();

        Long getProjectId();

        String getProjectName();
    }
}
