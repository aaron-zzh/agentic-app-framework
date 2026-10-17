package com.xuejiai.aaf.module.elder.mall.api;

import java.util.List;

/** 服务目录跨域只读契约，供养老 Agent 查询当前可预约服务。 */
public interface ServiceCatalogApi {

    List<ServiceSummary> listAvailableServices();

    record ServiceSummary(
            Long id,
            String name,
            String description,
            String category,
            String unit,
            String priceDescription,
            Integer durationMinutes) {}
}
