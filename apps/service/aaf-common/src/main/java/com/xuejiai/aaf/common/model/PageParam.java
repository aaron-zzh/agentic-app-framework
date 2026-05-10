package com.xuejiai.aaf.common.model;

import java.io.Serializable;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分页请求参数基类。业务分页 DTO 继承此类即可自动获得分页 + 排序能力。
 *
 * <p>排序格式：逗号分隔字段名，前缀 - 表示降序。示例：{@code "createTime,-id"} 表示 createTime 升序、id 降序。
 */
@Schema(description = "分页参数")
@Data
public class PageParam implements Serializable {

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

    @Schema(description = "排序，格式：字段名逗号分隔，前缀-降序。示例：createTime,-id", example = "-createTime")
    private String sort;

    /** 是否不分页 */
    public boolean isNoPaging() {
        return PAGE_SIZE_NONE == pageSize;
    }

    /** 构建 Spring Data Pageable，含排序。不分页时返回 Pageable.unpaged()。 */
    public Pageable toPageable() {
        if (isNoPaging()) {
            Sort sortObj = buildSort();
            return sortObj.isSorted() ? Pageable.unpaged(sortObj) : Pageable.unpaged();
        }
        Sort sortObj = buildSort();
        return sortObj.isSorted()
                ? PageRequest.of(pageNo - 1, pageSize, sortObj)
                : PageRequest.of(pageNo - 1, pageSize);
    }

    /** 构建 Spring Data Sort。无排序时返回 Sort.unsorted()。 */
    public Sort buildSort() {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        var orders =
                java.util.Arrays.stream(sort.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(
                                s -> {
                                    if (s.startsWith("-")) {
                                        return Sort.Order.desc(s.substring(1));
                                    }
                                    return Sort.Order.asc(s);
                                })
                        .toList();
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
