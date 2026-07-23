package com.xuejiai.aaf.framework.intelligent.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.dto.BatchReadRequestDTO;
import com.xuejiai.aaf.framework.crud.dto.CrudIdsRequestDTO;

/** 基于 BaseCrudService 的 AI 标准实体动作适配器。 */
public abstract class BaseCrudEntityActionAdapter<
                E extends BaseEntity, V, C, U, P extends PageParam>
        implements EntityActionAdapter {

    private final ResourceKey resourceKey;
    private final CrudResourceRegistry resourceCatalog;
    private final Class<C> createType;
    private final Class<U> updateType;
    private final Class<P> pageParamType;

    protected BaseCrudEntityActionAdapter(
            ResourceKey resourceKey,
            CrudResourceRegistry resourceCatalog,
            Class<C> createType,
            Class<U> updateType,
            Class<P> pageParamType) {
        this.resourceKey = resourceKey;
        this.resourceCatalog = resourceCatalog;
        this.createType = createType;
        this.updateType = updateType;
        this.pageParamType = pageParamType;
    }

    protected abstract BaseCrudService<E, V, C, U, P> getService();

    @Override
    public String entitySlug() {
        return entry().snapshot().slug();
    }

    @Override
    public String entityName() {
        return entry().snapshot().descriptor().label();
    }

    @Override
    public List<String> supportedActions() {
        var operations = entry().definition().capabilities().operations();
        return Arrays.stream(AiBusinessActionType.values())
                .filter(action -> operations.contains(crudOperation(action)))
                .map(AiBusinessActionType::action)
                .toList();
    }

    @Override
    public String permissionCode(AiBusinessActionType action) {
        return entry().definition().permissionCode(crudOperation(action).action());
    }

    private CrudResourceCatalogEntry entry() {
        return resourceCatalog.require(resourceKey);
    }

    @Override
    public Object execute(AiBusinessActionType action, Map<String, Object> params) {
        var safeParams = params == null ? Map.<String, Object>of() : params;
        return switch (action) {
            case QUERY ->
                    getService()
                            .queryWindow(
                                    toPageParam(safeParams),
                                    fieldSet(safeParams, "list"),
                                    List.of());
            case DETAIL ->
                    getService()
                            .getById(
                                    id(safeParams),
                                    stringValue(safeParams.get("queryToken")),
                                    fieldSet(safeParams, "detail"));
            case BATCH_READ ->
                    getService().batchRead(ids(safeParams), fieldSet(safeParams, "detail"));
            case OPTIONS ->
                    getService()
                            .options(
                                    stringValue(safeParams.get("q")),
                                    intValue(safeParams.get("limit"), 20));
            case META -> getService().meta();
            case CREATE -> getService().create(toCreate(safeParams));
            case UPDATE -> getService().update(id(safeParams), toUpdate(safeParams));
            case DELETE -> {
                getService().delete(id(safeParams));
                yield null;
            }
            case BATCH_DELETE -> {
                getService().deleteBatch(ids(safeParams));
                yield null;
            }
            case EXPORT -> getService().exportData(toPageParam(safeParams));
            case VALIDATE -> getService().validate(toCreate(safeParams));
            case ARCHIVE -> {
                getService().archive(ids(safeParams));
                yield null;
            }
            case RESTORE -> {
                getService().restore(ids(safeParams));
                yield null;
            }
        };
    }

    protected P toPageParam(Map<String, Object> params) {
        return JsonUtils.convertValue(params, pageParamType);
    }

    protected C toCreate(Map<String, Object> params) {
        return JsonUtils.convertValue(payload(params), createType);
    }

    protected U toUpdate(Map<String, Object> params) {
        return JsonUtils.convertValue(payloadWithoutId(params), updateType);
    }

    private Object payload(Map<String, Object> params) {
        return params.containsKey("data") ? params.get("data") : params;
    }

    private Object payloadWithoutId(Map<String, Object> params) {
        if (params.containsKey("data")) {
            return params.get("data");
        }
        var copy = new LinkedHashMap<>(params);
        copy.remove("id");
        copy.remove("ids");
        copy.remove("queryToken");
        copy.remove("fieldSet");
        return copy;
    }

    private Long id(Map<String, Object> params) {
        var value = params.get("id");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "缺少记录 ID");
    }

    private List<Long> ids(Map<String, Object> params) {
        var value = params.get("ids");
        if (value == null && params.get("id") != null) {
            return List.of(id(params));
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<Long>();
            for (var item : list) {
                if (item instanceof Number number) {
                    result.add(number.longValue());
                } else if (item instanceof String text && !text.isBlank()) {
                    result.add(Long.valueOf(text));
                }
            }
            return result;
        }
        if (value instanceof CrudIdsRequestDTO request) {
            return request.ids();
        }
        if (value instanceof BatchReadRequestDTO request) {
            return request.ids();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Arrays.stream(text.split(",")).map(String::trim).map(Long::valueOf).toList();
        }
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "缺少记录 ID 列表");
    }

    private String fieldSet(Map<String, Object> params, String defaultValue) {
        var value = stringValue(params.get("fieldSet"));
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.valueOf(text);
        }
        return defaultValue;
    }

    private CrudOperation crudOperation(AiBusinessActionType action) {
        return switch (action) {
            case QUERY -> CrudOperation.QUERY;
            case DETAIL -> CrudOperation.GET;
            case BATCH_READ -> CrudOperation.BATCH_READ;
            case OPTIONS -> CrudOperation.OPTIONS;
            case META -> CrudOperation.META;
            case CREATE -> CrudOperation.CREATE;
            case UPDATE -> CrudOperation.UPDATE;
            case DELETE -> CrudOperation.DELETE;
            case BATCH_DELETE -> CrudOperation.DELETE_BATCH;
            case EXPORT -> CrudOperation.EXPORT;
            case VALIDATE -> CrudOperation.VALIDATE;
            case ARCHIVE -> CrudOperation.ARCHIVE;
            case RESTORE -> CrudOperation.RESTORE;
        };
    }
}
