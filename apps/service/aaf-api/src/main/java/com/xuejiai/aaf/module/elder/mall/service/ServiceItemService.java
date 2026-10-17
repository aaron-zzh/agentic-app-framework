package com.xuejiai.aaf.module.elder.mall.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.mall.ErrorCodeConstants.MALL_VERSION_CONFLICT;
import static com.xuejiai.aaf.module.elder.mall.ErrorCodeConstants.SERVICE_ITEM_INVALID;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.ServiceCategoryEnum;
import com.xuejiai.aaf.common.enums.elder.ServiceItemStatusEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.module.elder.mall.ServiceItemResource;
import com.xuejiai.aaf.module.elder.mall.api.ServiceCatalogApi;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceItem;
import com.xuejiai.aaf.module.elder.mall.mapper.ServiceItemConvert;
import com.xuejiai.aaf.module.elder.mall.repository.ServiceItemRepository;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemActionDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemCreateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemPageDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemUpdateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemVO;

import lombok.RequiredArgsConstructor;

/** 服务项目 CRUD 与服务目录查询实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceItemService
        extends BaseCrudService<
                ServiceItem,
                ServiceItemVO,
                ServiceItemCreateDTO,
                ServiceItemUpdateDTO,
                ServiceItemPageDTO>
        implements ServiceCatalogApi {

    private static final int CATALOG_LIMIT = 200;

    private final ServiceItemRepository repository;

    @Override
    protected ServiceItemRepository getRepository() {
        return repository;
    }

    @Override
    protected ServiceItemVO toVO(ServiceItem entity) {
        return new ServiceItemVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getUnit(),
                entity.getPriceDescription(),
                entity.getDurationMinutes(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null);
    }

    @Override
    protected ServiceItem toEntity(ServiceItemCreateDTO request) {
        var entity = ServiceItemConvert.INSTANCE.toEntity(request);
        validate(entity);
        return entity;
    }

    @Override
    protected void updateEntity(ServiceItem entity, ServiceItemUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        applyRequired(request.name(), entity::setName);
        applyRequired(request.description(), entity::setDescription);
        applyRequired(request.category(), entity::setCategory);
        applyRequired(request.unit(), entity::setUnit);
        applyRequired(request.priceDescription(), entity::setPriceDescription);
        applyRequired(request.durationMinutes(), entity::setDurationMinutes);
        applyRequired(request.status(), entity::setStatus);
        validate(entity);
        bumpVersion(entity);
    }

    @Override
    protected Specification<ServiceItem> buildSpec(ServiceItemPageDTO request) {
        return SpecificationBuilder.<ServiceItem>builder()
                .likeIfPresent("name", request.getName())
                .eqIfPresent("category", request.getCategory())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name");
    }

    @Override
    protected String displayName(ServiceItem entity) {
        return entity.getName();
    }

    @Override
    public List<ServiceSummary> listAvailableServices() {
        var request = new ServiceItemPageDTO();
        request.setStatus(ServiceItemStatusEnum.AVAILABLE.getCode());
        request.setPageNo(1);
        request.setPageSize(CATALOG_LIMIT);
        return queryWindow(request, "catalog", List.of()).list().stream()
                .map(
                        item ->
                                new ServiceSummary(
                                        item.id(),
                                        item.name(),
                                        item.description(),
                                        item.category(),
                                        item.unit(),
                                        item.priceDescription(),
                                        item.durationMinutes()))
                .toList();
    }

    @Transactional
    public ServiceItemVO publish(ServiceItemActionDTO command) {
        return changeStatus(
                command, ServiceItemResource.COMMAND_PUBLISH, ServiceItemStatusEnum.AVAILABLE);
    }

    @Transactional
    public ServiceItemVO offline(ServiceItemActionDTO command) {
        return changeStatus(
                command, ServiceItemResource.COMMAND_OFFLINE, ServiceItemStatusEnum.UNAVAILABLE);
    }

    private ServiceItemVO changeStatus(
            ServiceItemActionDTO command, String commandType, ServiceItemStatusEnum target) {
        var plan =
                new CustomUpdatePlan<ServiceItem, ServiceItemActionDTO, Void, ServiceItemVO>(
                        commandType,
                        Set.of("status"),
                        (item, ignored) -> {},
                        (item, ignored) -> {
                            if (!Objects.equals(item.getStatus(), target.getCode())) {
                                item.setStatus(target.getCode());
                                bumpVersion(item);
                            }
                        },
                        (item, ignored) -> null,
                        true,
                        (item, ignored, result) -> {},
                        (item, ignored, result) -> toVO(item));
        return executeCustomUpdateCommand(command.id(), command, plan);
    }

    private void validate(ServiceItem item) {
        requireText(item.getName(), 200);
        requireText(item.getDescription(), 2000);
        requireText(item.getUnit(), 32);
        requireText(item.getPriceDescription(), 200);
        if (item.getDurationMinutes() == null || item.getDurationMinutes() <= 0) {
            throw exception(SERVICE_ITEM_INVALID);
        }
        if (item.getCategory() == null
                || Arrays.stream(ServiceCategoryEnum.ARRAYS)
                        .noneMatch(item.getCategory()::equals)) {
            throw exception(SERVICE_ITEM_INVALID);
        }
        if (item.getStatus() == null
                || Arrays.stream(ServiceItemStatusEnum.ARRAYS)
                        .noneMatch(item.getStatus()::equals)) {
            throw exception(SERVICE_ITEM_INVALID);
        }
    }

    private void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw exception(SERVICE_ITEM_INVALID);
        }
    }

    private void requireExpectedVersion(ServiceItem item, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(item)) {
            throw exception(MALL_VERSION_CONFLICT);
        }
    }

    private void bumpVersion(ServiceItem item) {
        item.setVersion(currentVersion(item) + 1);
    }

    private int currentVersion(ServiceItem item) {
        return item.getVersion() == null ? 0 : item.getVersion();
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(SERVICE_ITEM_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }
}
