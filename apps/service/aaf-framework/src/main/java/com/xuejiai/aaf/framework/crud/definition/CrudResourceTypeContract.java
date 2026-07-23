package com.xuejiai.aaf.framework.crud.definition;

import java.util.Objects;

import org.springframework.core.ResolvableType;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointKind;
import com.xuejiai.aaf.framework.crud.web.NestedCrudResourceController;
import com.xuejiai.aaf.framework.crud.web.ResourceOptionsController;

/** 资源实体与标准 CRUD 输入、输出、分页类型的运行时契约。 */
public record CrudResourceTypeContract<E extends BaseEntity>(
        Class<E> entityType,
        Class<?> createType,
        Class<?> updateType,
        Class<?> viewType,
        Class<? extends PageParam> pageType) {

    public CrudResourceTypeContract {
        entityType = Objects.requireNonNull(entityType, "entityType");
        createType = Objects.requireNonNull(createType, "createType");
        updateType = Objects.requireNonNull(updateType, "updateType");
        viewType = Objects.requireNonNull(viewType, "viewType");
        pageType = Objects.requireNonNull(pageType, "pageType");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static CrudResourceTypeContract<?> fromEndpoint(
            Class<?> controllerType, CrudResourceEndpointKind kind) {
        var contractType =
                switch (kind) {
                    case CRUD -> BaseCrudController.class;
                    case NESTED_CRUD -> NestedCrudResourceController.class;
                    case OPTIONS -> ResourceOptionsController.class;
                };
        var contract = ResolvableType.forClass(controllerType).as(contractType);
        var entityType = contract.getGeneric(0).resolve();
        var viewType = contract.getGeneric(1).resolve();
        if (entityType == null
                || viewType == null
                || !BaseEntity.class.isAssignableFrom(entityType)) {
            throw new IllegalStateException("无法解析资源端点泛型: " + controllerType.getName());
        }
        if (kind == CrudResourceEndpointKind.OPTIONS) {
            return new CrudResourceTypeContract(
                    entityType, Void.class, Void.class, viewType, PageParam.class);
        }
        var createType = contract.getGeneric(2).resolve();
        var updateType = contract.getGeneric(3).resolve();
        var pageType = contract.getGeneric(4).resolve();
        if (createType == null
                || updateType == null
                || pageType == null
                || !PageParam.class.isAssignableFrom(pageType)) {
            throw new IllegalStateException("无法解析 CRUD 端点完整泛型: " + controllerType.getName());
        }
        return new CrudResourceTypeContract(entityType, createType, updateType, viewType, pageType);
    }

    /** 从标准 CRUD Controller 泛型推导类型，并校验预期实体类型。 */
    @SuppressWarnings("unchecked")
    public static <E extends BaseEntity> CrudResourceTypeContract<E> fromCrudController(
            Class<?> controllerType, Class<E> entityType) {
        var inferred = fromEndpoint(controllerType, CrudResourceEndpointKind.CRUD);
        if (!entityType.equals(inferred.entityType())) {
            throw new IllegalStateException(
                    "CRUD Controller 实体类型不匹配: %s，期望 %s"
                            .formatted(inferred.entityType().getName(), entityType.getName()));
        }
        return (CrudResourceTypeContract<E>) inferred;
    }
}
