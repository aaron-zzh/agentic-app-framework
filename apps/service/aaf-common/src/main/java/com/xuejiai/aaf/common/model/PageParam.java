package com.xuejiai.aaf.common.model;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分页请求参数基类。业务分页 DTO 继承此类即可自动获得分页 + 排序能力。
 *
 * <p>排序格式：逗号分隔的 {@code field:asc|desc}，例如 {@code "createTime:asc,id:desc"}。
 */
@Schema(description = "分页参数")
@Data
public class PageParam implements Serializable {

    private static final Pattern SORT_ITEM_PATTERN =
            Pattern.compile("^([A-Za-z][A-Za-z0-9_]*):(asc|desc)$");

    public static final PageParam DEFAULT = new PageParam();

    /** 不分页标记值，用于导出等需要查全部数据的场景。 */
    public static final int PAGE_SIZE_NONE = -1;

    @Schema(description = "页码，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小值为 1")
    private Integer pageNo = 1;

    @Schema(
            description = "每页条数，最大 200；设为 -1 表示不分页",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = -1, message = "每页条数最小值为 -1")
    @Max(value = 200, message = "每页条数最大值为 200")
    private Integer pageSize = 10;

    @Schema(description = "排序，格式：field:asc|desc，多个字段用逗号分隔", example = "createTime:desc,id:asc")
    private String sort;

    /** 是否不分页 */
    public boolean isNoPaging() {
        return PAGE_SIZE_NONE == pageSize;
    }

    /** 构建 Spring Data Pageable，显式排序字段默认不允许。 */
    public Pageable toPageable() {
        return toPageable(Sort.unsorted(), Set.of());
    }

    /** 构建 Spring Data Pageable，无排序时使用 defaultSort，显式排序字段默认不允许。 */
    public Pageable toPageable(Sort defaultSort) {
        return toPageable(defaultSort, Set.of());
    }

    /**
     * 构建 Spring Data Pageable，并校验客户端显式指定的排序字段。
     *
     * <p>服务端默认排序不需要白名单；只有请求携带 {@code sort} 时才校验 allowedSortFields。
     */
    public Pageable toPageable(Sort defaultSort, Set<String> allowedSortFields) {
        var requestedSort = buildSort();
        validateSortFields(requestedSort, allowedSortFields);
        return toPageable(defaultSort, requestedSort);
    }

    /** 使用已解析的客户端排序或服务端默认排序构建 Pageable。 */
    public Pageable toPageable(Sort defaultSort, Sort requestedSort) {
        var effectiveSort = requestedSort.isSorted() ? requestedSort : defaultSort;
        if (isNoPaging()) {
            return effectiveSort.isSorted() ? Pageable.unpaged(effectiveSort) : Pageable.unpaged();
        }
        return effectiveSort.isSorted()
                ? PageRequest.of(pageNo - 1, pageSize, effectiveSort)
                : PageRequest.of(pageNo - 1, pageSize);
    }

    /** 构建严格解析后的 Spring Data Sort。未传 sort 时返回 Sort.unsorted()。 */
    public Sort buildSort() {
        if (sort == null) {
            return Sort.unsorted();
        }
        var fields = new HashSet<String>();
        var orders =
                java.util.Arrays.stream(sort.split(",", -1))
                        .map(
                                item -> {
                                    var matcher = SORT_ITEM_PATTERN.matcher(item);
                                    if (!matcher.matches() || !fields.add(matcher.group(1))) {
                                        throw exception(GlobalErrorCode.SORT_FORMAT_INVALID);
                                    }
                                    var direction =
                                            "asc".equals(matcher.group(2))
                                                    ? Sort.Direction.ASC
                                                    : Sort.Direction.DESC;
                                    return new Sort.Order(direction, matcher.group(1));
                                })
                        .toList();
        return Sort.by(orders);
    }

    private void validateSortFields(Sort requestedSort, Set<String> allowedSortFields) {
        for (var order : requestedSort) {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw exception(GlobalErrorCode.SORT_FIELD_NOT_SUPPORTED, order.getProperty());
            }
        }
    }
}
