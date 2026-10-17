package com.xuejiai.aaf.module.elder.mall.api;

import java.time.LocalDateTime;
import java.util.List;

/** 服务预约跨域写契约；Agent 明确确认草稿后也只能创建待运营确认的预约。 */
public interface ServiceOrderApi {

    ServiceOrderSnapshot createPendingOrder(CreatePendingOrderCommand command);

    List<ServiceOrderSnapshot> listRecentOrders(Long elderId, int limit);

    record CreatePendingOrderCommand(
            Long elderId,
            Long serviceItemId,
            Integer quantity,
            LocalDateTime scheduledAt,
            String serviceAddress,
            String notes) {}

    record ServiceOrderSnapshot(
            Long id,
            Long elderId,
            Long serviceItemId,
            Integer quantity,
            LocalDateTime scheduledAt,
            String serviceAddress,
            String notes,
            String status) {}
}
