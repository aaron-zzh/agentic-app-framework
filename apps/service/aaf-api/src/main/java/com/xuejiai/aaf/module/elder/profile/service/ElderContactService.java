package com.xuejiai.aaf.module.elder.profile.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.*;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.AuthorizationScopeEnum;
import com.xuejiai.aaf.common.enums.elder.ContactRelationshipEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.module.elder.profile.domain.ElderContact;
import com.xuejiai.aaf.module.elder.profile.mapper.ElderContactConvert;
import com.xuejiai.aaf.module.elder.profile.repository.ElderContactRepository;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactVO;
import com.xuejiai.aaf.module.system.role.relation.GrantRelationDTO;
import com.xuejiai.aaf.module.system.role.relation.ResourceRelationService;

import lombok.RequiredArgsConstructor;

/** 长者联系人 CRUD 服务；绑定账号与授权范围通过 ReBAC 关系元组生效。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElderContactService
        extends BaseCrudService<
                ElderContact,
                ElderContactVO,
                ElderContactCreateDTO,
                ElderContactUpdateDTO,
                ElderContactPageDTO> {

    private static final String REBAC_OBJECT_TYPE = "elder";
    private static final String SUBJECT_TYPE_USER = "USER";
    private static final String RELATION_FAMILY = "FAMILY";
    private static final Set<String> ALLOWED_SCOPES =
            Set.copyOf(Arrays.asList(AuthorizationScopeEnum.ARRAYS));

    private final ElderContactRepository elderContactRepository;
    private final ResourceRelationService resourceRelationService;

    @Override
    protected ElderContactRepository getRepository() {
        return elderContactRepository;
    }

    @Override
    protected ElderContactVO toVO(ElderContact entity) {
        return new ElderContactVO(
                entity.getId(),
                entity.getVersion(),
                entity.getElderId(),
                entity.getUserId(),
                entity.getName(),
                entity.getMobile(),
                entity.getRelationship(),
                entity.getPriority(),
                entity.getEmergencyContact(),
                parseScopes(entity.getAuthorizationScopes()),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                null,
                null);
    }

    @Override
    protected ElderContact toEntity(ElderContactCreateDTO request) {
        validateScopes(request.userId(), request.authorizationScopes());
        var entity = ElderContactConvert.INSTANCE.toEntity(request);
        entity.setAuthorizationScopes(serializeScopes(request.authorizationScopes()));
        validate(entity);
        return entity;
    }

    @Override
    protected void afterCreate(ElderContact entity, ElderContactCreateDTO request) {
        synchronizeRelations(RelationState.empty(), stateOf(entity));
    }

    @Override
    protected void updateEntity(ElderContact entity, ElderContactUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        var previous = stateOf(entity);
        applyRequired(request.elderId(), entity::setElderId);
        applyNullable(request.userId(), entity::setUserId);
        applyRequired(request.name(), entity::setName);
        applyRequired(request.mobile(), entity::setMobile);
        applyRequired(request.relationship(), entity::setRelationship);
        applyRequired(request.priority(), entity::setPriority);
        applyRequired(request.emergencyContact(), entity::setEmergencyContact);
        if (!request.authorizationScopes().isAbsent()) {
            if (request.authorizationScopes().isNullValue()) {
                throw exception(ELDER_CONTACT_SCOPE_INVALID);
            }
            entity.setAuthorizationScopes(
                    serializeScopes(request.authorizationScopes().valueOrNull()));
        }
        validate(entity);
        entity.setVersion(currentVersion(entity) + 1);
        synchronizeRelations(previous, stateOf(entity));
    }

    @Override
    protected void afterDelete(ElderContact entity) {
        synchronizeRelations(stateOf(entity), RelationState.empty());
    }

    @Override
    protected Specification<ElderContact> buildSpec(ElderContactPageDTO request) {
        return SpecificationBuilder.<ElderContact>builder()
                .eqIfPresent("elderId", request.getElderId())
                .eqIfPresent("userId", request.getUserId())
                .eqIfPresent("relationship", request.getRelationship())
                .eqIfPresent("emergencyContact", request.getEmergencyContact())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name", "mobile");
    }

    private void synchronizeRelations(RelationState previous, RelationState current) {
        var previousRelations = relations(previous);
        var currentRelations = relations(current);
        previousRelations.stream()
                .filter(relation -> !currentRelations.contains(relation))
                .forEach(
                        relation ->
                                resourceRelationService.revoke(
                                        relation(previous.elderId(), relation, previous.userId())));
        currentRelations.stream()
                .filter(relation -> !previousRelations.contains(relation))
                .forEach(
                        relation ->
                                resourceRelationService.grant(
                                        relation(current.elderId(), relation, current.userId())));
    }

    private Set<String> relations(RelationState state) {
        if (state.elderId() == null || state.userId() == null || state.scopes().isEmpty()) {
            return Set.of();
        }
        var relations = new LinkedHashSet<>(state.scopes());
        relations.add(RELATION_FAMILY);
        return Set.copyOf(relations);
    }

    private GrantRelationDTO relation(Long elderId, String relation, Long userId) {
        return new GrantRelationDTO(
                REBAC_OBJECT_TYPE,
                String.valueOf(elderId),
                relation,
                SUBJECT_TYPE_USER,
                String.valueOf(userId),
                null,
                null);
    }

    private RelationState stateOf(ElderContact entity) {
        return new RelationState(
                entity.getElderId(),
                entity.getUserId(),
                parseScopes(entity.getAuthorizationScopes()));
    }

    private void validate(ElderContact entity) {
        if (entity.getElderId() == null || entity.getElderId() <= 0) {
            throw exception(ELDER_CONTACT_INVALID);
        }
        requireText(entity.getName());
        requireText(entity.getMobile());
        if (entity.getRelationship() == null
                || Arrays.stream(ContactRelationshipEnum.ARRAYS)
                        .noneMatch(entity.getRelationship()::equals)) {
            throw exception(ELDER_CONTACT_INVALID);
        }
        if (entity.getPriority() == null || entity.getPriority() < 0) {
            throw exception(ELDER_CONTACT_INVALID);
        }
        if (entity.getEmergencyContact() == null) {
            throw exception(ELDER_CONTACT_INVALID);
        }
        validateScopes(entity.getUserId(), parseScopes(entity.getAuthorizationScopes()));
    }

    private void validateScopes(Long userId, Set<String> scopes) {
        if (scopes == null || !ALLOWED_SCOPES.containsAll(scopes)) {
            throw exception(ELDER_CONTACT_SCOPE_INVALID);
        }
        if (!scopes.isEmpty() && userId == null) {
            throw exception(ELDER_CONTACT_SCOPE_INVALID);
        }
    }

    private Set<String> parseScopes(String json) {
        return Set.copyOf(JsonUtils.parseArray(json, String.class));
    }

    private String serializeScopes(Set<String> scopes) {
        return JsonUtils.toJsonString(scopes.stream().sorted().toList());
    }

    private void requireExpectedVersion(ElderContact entity, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(entity)) {
            throw exception(ELDER_VERSION_CONFLICT);
        }
    }

    private int currentVersion(ElderContact entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    private void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw exception(ELDER_CONTACT_INVALID);
        }
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(ELDER_CONTACT_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }

    private record RelationState(Long elderId, Long userId, Set<String> scopes) {
        private RelationState {
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        }

        private static RelationState empty() {
            return new RelationState(null, null, Set.of());
        }
    }
}
