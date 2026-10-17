package com.xuejiai.aaf.module.elder.profile.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.ELDER_HEALTH_ITEM_TYPE_INVALID;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.ELDER_PROFILE_INVALID;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.ELDER_VERSION_CONFLICT;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.HealthItemTypeEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.module.elder.profile.domain.ElderHealthItem;
import com.xuejiai.aaf.module.elder.profile.mapper.ElderHealthItemConvert;
import com.xuejiai.aaf.module.elder.profile.repository.ElderHealthItemRepository;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemVO;

import lombok.RequiredArgsConstructor;

/** 长者健康条目 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElderHealthItemService
        extends BaseCrudService<
                ElderHealthItem,
                ElderHealthItemVO,
                ElderHealthItemCreateDTO,
                ElderHealthItemUpdateDTO,
                ElderHealthItemPageDTO> {

    private final ElderHealthItemRepository repository;

    @Override
    protected ElderHealthItemRepository getRepository() {
        return repository;
    }

    @Override
    protected ElderHealthItemVO toVO(ElderHealthItem entity) {
        return new ElderHealthItemVO(
                entity.getId(),
                entity.getVersion(),
                entity.getElderId(),
                entity.getItemType(),
                entity.getName(),
                entity.getDescription(),
                entity.getOccurredDate(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                null);
    }

    @Override
    protected ElderHealthItem toEntity(ElderHealthItemCreateDTO request) {
        var entity = ElderHealthItemConvert.INSTANCE.toEntity(request);
        validate(entity);
        return entity;
    }

    @Override
    protected void updateEntity(ElderHealthItem entity, ElderHealthItemUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        applyRequired(request.elderId(), entity::setElderId);
        applyRequired(request.itemType(), entity::setItemType);
        applyRequired(request.name(), entity::setName);
        applyNullable(request.description(), entity::setDescription);
        applyNullable(request.occurredDate(), entity::setOccurredDate);
        validate(entity);
        entity.setVersion(currentVersion(entity) + 1);
    }

    @Override
    protected Specification<ElderHealthItem> buildSpec(ElderHealthItemPageDTO request) {
        return SpecificationBuilder.<ElderHealthItem>builder()
                .eqIfPresent("elderId", request.getElderId())
                .eqIfPresent("itemType", request.getItemType())
                .likeIfPresent("name", request.getName())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name");
    }

    private void validate(ElderHealthItem entity) {
        if (entity.getElderId() == null || entity.getElderId() <= 0) {
            throw exception(ELDER_PROFILE_INVALID);
        }
        if (entity.getItemType() == null
                || Arrays.stream(HealthItemTypeEnum.ARRAYS)
                        .noneMatch(entity.getItemType()::equals)) {
            throw exception(ELDER_HEALTH_ITEM_TYPE_INVALID);
        }
        if (entity.getName() == null
                || entity.getName().isBlank()
                || entity.getName().length() > 200) {
            throw exception(ELDER_PROFILE_INVALID);
        }
    }

    private void requireExpectedVersion(ElderHealthItem entity, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(entity)) {
            throw exception(ELDER_VERSION_CONFLICT);
        }
    }

    private int currentVersion(ElderHealthItem entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(ELDER_PROFILE_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }
}
