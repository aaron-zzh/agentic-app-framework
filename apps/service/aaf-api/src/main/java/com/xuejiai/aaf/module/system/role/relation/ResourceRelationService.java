package com.xuejiai.aaf.module.system.role.relation;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.access.PermissionVersionService;
import com.xuejiai.aaf.framework.security.access.RelationPermissionChecker;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/** ReBAC 关系元组服务。 */
@Service
@RequiredArgsConstructor
public class ResourceRelationService implements RelationPermissionChecker {

    private static final int MAX_DEPTH = 8;

    private final PermissionTupleRepository repository;
    private final UserRoleRepository userRoleRepository;
    private final RebacPermissionCache rebacPermissionCache;
    private final PermissionVersionService versionService;

    @Transactional
    public void grant(GrantRelationDTO dto) {
        var subjectRelation = normalizeSubjectRelation(dto.subjectRelation());
        if (repository
                .existsByObjectTypeAndObjectIdAndRelationAndSubjectTypeAndSubjectIdAndSubjectRelation(
                        dto.objectType(),
                        dto.objectId(),
                        dto.relation(),
                        dto.subjectType(),
                        dto.subjectId(),
                        subjectRelation)) {
            return;
        }
        var entity = new PermissionTuple();
        entity.setObjectType(dto.objectType());
        entity.setObjectId(dto.objectId());
        entity.setRelation(dto.relation());
        entity.setSubjectType(dto.subjectType());
        entity.setSubjectId(dto.subjectId());
        entity.setSubjectRelation(subjectRelation);
        entity.setExpiresAt(dto.expiresAt());
        repository.save(entity);
        evict(dto.objectType(), dto.objectId());
    }

    @Transactional
    public void revoke(GrantRelationDTO dto) {
        repository
                .deleteByObjectTypeAndObjectIdAndRelationAndSubjectTypeAndSubjectIdAndSubjectRelation(
                        dto.objectType(),
                        dto.objectId(),
                        dto.relation(),
                        dto.subjectType(),
                        dto.subjectId(),
                        normalizeSubjectRelation(dto.subjectRelation()));
        evict(dto.objectType(), dto.objectId());
    }

    public boolean check(
            String objectType,
            String objectId,
            String relation,
            String subjectType,
            String subjectId) {
        return repository
                .existsByObjectTypeAndObjectIdAndRelationAndSubjectTypeAndSubjectIdAndSubjectRelation(
                        objectType, objectId, relation, subjectType, subjectId, "");
    }

    @Override
    public boolean hasPermission(
            Long userId, String objectType, String objectId, String permission) {
        if (userId == null || objectType == null || objectId == null || permission == null) {
            return false;
        }
        var cached = rebacPermissionCache.get(userId, objectType, objectId, permission);
        if (cached != null) {
            return cached;
        }
        var roleIds =
                userRoleRepository.findByUserIdAndDeletedFalse(userId).stream()
                        .map(UserRole::getRoleId)
                        .map(String::valueOf)
                        .toList();
        var allowed =
                repository.hasPath(
                        objectType,
                        objectId,
                        List.copyOf(acceptedRelations(permission)),
                        String.valueOf(userId),
                        roleIds.isEmpty() ? List.of("-1") : roleIds,
                        MAX_DEPTH,
                        Instant.now());
        rebacPermissionCache.put(userId, objectType, objectId, permission, allowed);
        return allowed;
    }

    public List<PermissionTupleVO> listByResource(String objectType, String objectId) {
        return repository.findByObjectTypeAndObjectId(objectType, objectId).stream()
                .map(
                        entity ->
                                new PermissionTupleVO(
                                        entity.getId(),
                                        entity.getObjectType(),
                                        entity.getObjectId(),
                                        entity.getRelation(),
                                        entity.getSubjectType(),
                                        entity.getSubjectId(),
                                        entity.getSubjectRelation(),
                                        entity.getExpiresAt()))
                .toList();
    }

    private Set<String> acceptedRelations(String permission) {
        return switch (permission.toLowerCase(Locale.ROOT)) {
            case "can_read", "read" -> Set.of("OWNER", "EDITOR", "VIEWER");
            case "can_write", "write", "update" -> Set.of("OWNER", "EDITOR");
            case "can_delete", "delete" -> Set.of("OWNER");
            default -> Set.of(permission.toUpperCase(Locale.ROOT));
        };
    }

    private String normalizeSubjectRelation(String subjectRelation) {
        return subjectRelation == null ? "" : subjectRelation.trim();
    }

    private void evict(String objectType, String objectId) {
        versionService.bumpRelationSchemaVersion();
        rebacPermissionCache.evictObject(objectType, objectId);
    }
}
