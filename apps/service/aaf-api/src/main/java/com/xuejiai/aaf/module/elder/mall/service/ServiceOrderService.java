package com.xuejiai.aaf.module.elder.mall.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.mall.ErrorCodeConstants.MALL_VERSION_CONFLICT;
import static com.xuejiai.aaf.module.elder.mall.ErrorCodeConstants.SERVICE_ITEM_NOT_FOUND;
import static com.xuejiai.aaf.module.elder.mall.ErrorCodeConstants.SERVICE_ITEM_UNAVAILABLE;
import static com.xuejiai.aaf.module.elder.mall.ErrorCodeConstants.SERVICE_ORDER_INVALID;
import static com.xuejiai.aaf.module.elder.mall.ErrorCodeConstants.SERVICE_ORDER_STATUS_TRANSITION_INVALID;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceMessageDirectionEnum;
import com.xuejiai.aaf.common.enums.elder.AuthorizationScopeEnum;
import com.xuejiai.aaf.common.enums.elder.ServiceItemStatusEnum;
import com.xuejiai.aaf.common.enums.elder.ServiceOrderStatusEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.module.device.api.DeviceMessageApi;
import com.xuejiai.aaf.module.elder.mall.ServiceOrderResource;
import com.xuejiai.aaf.module.elder.mall.api.ServiceOrderApi;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceItem;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceOrder;
import com.xuejiai.aaf.module.elder.mall.mapper.ServiceOrderConvert;
import com.xuejiai.aaf.module.elder.mall.repository.ServiceItemRepository;
import com.xuejiai.aaf.module.elder.mall.repository.ServiceOrderRepository;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderActionDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderCreateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderPageDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderUpdateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderVO;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.scenario.ElderDeviceScenario;

import lombok.RequiredArgsConstructor;

/** 服务预约 CRUD、运营状态动作及跨域创建实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceOrderService
        extends BaseCrudService<
                ServiceOrder,
                ServiceOrderVO,
                ServiceOrderCreateDTO,
                ServiceOrderUpdateDTO,
                ServiceOrderPageDTO>
        implements ServiceOrderApi {

    private static final String REMINDER_CAPABILITY = "reminder.show";

    private final ServiceOrderRepository repository;
    private final ServiceItemRepository serviceItemRepository;
    private final ElderQueryApi elderQueryApi;
    private final DeviceMessageApi deviceMessageApi;

    @Override
    protected ServiceOrderRepository getRepository() {
        return repository;
    }

    @Override
    protected ServiceOrderVO toVO(ServiceOrder entity) {
        return new ServiceOrderVO(
                entity.getId(),
                entity.getVersion(),
                entity.getElderId(),
                entity.getServiceItemId(),
                entity.getQuantity(),
                entity.getScheduledAt(),
                entity.getServiceAddress(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getReminderCommandId(),
                entity.getConfirmedAt(),
                entity.getCompletedAt(),
                entity.getCancelledAt(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                null,
                null);
    }

    @Override
    protected ServiceOrder toEntity(ServiceOrderCreateDTO request) {
        requireBookingPermission(request.elderId());
        requireAvailableItem(request.serviceItemId());
        var entity = ServiceOrderConvert.INSTANCE.toEntity(request);
        entity.setStatus(ServiceOrderStatusEnum.CREATED.getCode());
        validate(entity);
        return entity;
    }

    @Override
    protected void updateEntity(ServiceOrder entity, ServiceOrderUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        requireStatus(entity, ServiceOrderStatusEnum.CREATED);
        applyRequired(request.elderId(), entity::setElderId);
        applyRequired(request.serviceItemId(), entity::setServiceItemId);
        applyRequired(request.quantity(), entity::setQuantity);
        applyRequired(request.scheduledAt(), entity::setScheduledAt);
        applyRequired(request.serviceAddress(), entity::setServiceAddress);
        applyNullable(request.notes(), entity::setNotes);
        requireBookingPermission(entity.getElderId());
        requireAvailableItem(entity.getServiceItemId());
        validate(entity);
        bumpVersion(entity);
    }

    @Override
    protected Specification<ServiceOrder> buildSpec(ServiceOrderPageDTO request) {
        return SpecificationBuilder.<ServiceOrder>builder()
                .eqIfPresent("elderId", request.getElderId())
                .eqIfPresent("serviceItemId", request.getServiceItemId())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    @Override
    @Transactional
    public ServiceOrderSnapshot createPendingOrder(CreatePendingOrderCommand command) {
        if (command == null) {
            throw exception(SERVICE_ORDER_INVALID);
        }
        var access =
                elderQueryApi.requireAccess(
                        command.elderId(), AuthorizationScopeEnum.BOOK_SERVICE.getCode());
        requireAvailableItem(command.serviceItemId());
        var order =
                ServiceOrderConvert.INSTANCE.toEntity(
                        new ServiceOrderCreateDTO(
                                command.elderId(),
                                command.serviceItemId(),
                                command.quantity() == null ? 1 : command.quantity(),
                                command.scheduledAt(),
                                command.serviceAddress(),
                                command.notes()));
        order.setStatus(ServiceOrderStatusEnum.CREATED.getCode());
        order.setOrgId(access.orgId());
        order.setWorkspaceId(access.workspaceId());
        order.setOwnerId(access.requestingOwnerId());
        validate(order);
        return toSnapshot(toVO(repository.save(order)));
    }

    @Override
    public List<ServiceOrderSnapshot> listRecentOrders(Long elderId, int limit) {
        if (elderId == null || elderId <= 0 || limit <= 0) {
            throw exception(SERVICE_ORDER_INVALID);
        }
        elderQueryApi.requireAccess(elderId, AuthorizationScopeEnum.VIEW_SERVICE.getCode());
        return repository
                .findByElderIdOrderByCreateTimeDescIdDesc(
                        elderId, PageRequest.of(0, Math.min(limit, 20)))
                .stream()
                .map(order -> toSnapshot(toVO(order)))
                .toList();
    }

    /** 运营确认预约，并在同一事务内写入确定性设备提醒 outbox。 */
    @Transactional
    public ServiceOrderVO confirm(ServiceOrderActionDTO command) {
        var plan =
                new CustomUpdatePlan<ServiceOrder, ServiceOrderActionDTO, Void, ServiceOrderVO>(
                        ServiceOrderResource.COMMAND_CONFIRM,
                        Set.of("status", "reminderCommandId", "confirmedAt"),
                        (order, ignored) -> {
                            requireStatus(order, ServiceOrderStatusEnum.CREATED);
                            if (!order.getScheduledAt().isAfter(LocalDateTime.now())) {
                                throw exception(SERVICE_ORDER_INVALID);
                            }
                            requireAvailableItem(order.getServiceItemId());
                        },
                        (order, ignored) -> {
                            order.setStatus(ServiceOrderStatusEnum.CONFIRMED.getCode());
                            order.setConfirmedAt(LocalDateTime.now());
                            order.setReminderCommandId(reminderCommandId(order.getId()));
                            bumpVersion(order);
                        },
                        (order, ignored) -> null,
                        true,
                        (order, ignored, result) -> enqueueReminder(order),
                        (order, ignored, result) -> toVO(order));
        return executeCustomUpdateCommand(command.id(), command, plan);
    }

    /** 待确认或已确认预约可取消；已确认预约同时撤销设备提醒。 */
    @Transactional
    public ServiceOrderVO cancel(ServiceOrderActionDTO command) {
        var plan =
                new CustomUpdatePlan<ServiceOrder, ServiceOrderActionDTO, Void, ServiceOrderVO>(
                        ServiceOrderResource.COMMAND_CANCEL,
                        Set.of("status", "cancelledAt"),
                        (order, ignored) -> requireCancellable(order),
                        (order, ignored) -> {
                            order.setStatus(ServiceOrderStatusEnum.CANCELLED.getCode());
                            order.setCancelledAt(LocalDateTime.now());
                            bumpVersion(order);
                        },
                        (order, ignored) -> null,
                        true,
                        (order, ignored, result) -> enqueueReminderCancellation(order),
                        (order, ignored, result) -> toVO(order));
        return executeCustomUpdateCommand(command.id(), command, plan);
    }

    /** 只有已确认预约能标记为完成。 */
    @Transactional
    public ServiceOrderVO complete(ServiceOrderActionDTO command) {
        var plan =
                new CustomUpdatePlan<ServiceOrder, ServiceOrderActionDTO, Void, ServiceOrderVO>(
                        ServiceOrderResource.COMMAND_COMPLETE,
                        Set.of("status", "completedAt"),
                        (order, ignored) -> requireStatus(order, ServiceOrderStatusEnum.CONFIRMED),
                        (order, ignored) -> {
                            order.setStatus(ServiceOrderStatusEnum.COMPLETED.getCode());
                            order.setCompletedAt(LocalDateTime.now());
                            bumpVersion(order);
                        },
                        (order, ignored) -> null,
                        true,
                        (order, ignored, result) -> {},
                        (order, ignored, result) -> toVO(order));
        return executeCustomUpdateCommand(command.id(), command, plan);
    }

    private void enqueueReminder(ServiceOrder order) {
        var item = requireItem(order.getServiceItemId());
        var arguments = new LinkedHashMap<String, Object>();
        arguments.put("reminderType", "SERVICE_ORDER");
        arguments.put("orderId", order.getId());
        arguments.put("elderId", order.getElderId());
        arguments.put("serviceItemId", item.getId());
        arguments.put("serviceName", item.getName());
        arguments.put("quantity", order.getQuantity());
        arguments.put("scheduledAt", order.getScheduledAt().toString());
        arguments.put("remindAt", order.getScheduledAt().toLocalDate().atStartOfDay().toString());
        arguments.put("serviceAddress", order.getServiceAddress());
        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            arguments.put("notes", order.getNotes());
        }
        deviceMessageApi.enqueue(
                new DeviceMessageApi.DeviceMessageCommand(
                        null,
                        ElderDeviceScenario.KEY,
                        order.getElderId(),
                        order.getReminderCommandId(),
                        DeviceMessageDirectionEnum.COMMAND.getCode(),
                        REMINDER_CAPABILITY,
                        "show",
                        arguments,
                        order.getScheduledAt().toLocalDate().plusDays(1).atStartOfDay(),
                        "service-order:" + order.getId(),
                        null));
    }

    private void enqueueReminderCancellation(ServiceOrder order) {
        if (order.getReminderCommandId() == null) {
            return;
        }
        deviceMessageApi.enqueue(
                new DeviceMessageApi.DeviceMessageCommand(
                        null,
                        ElderDeviceScenario.KEY,
                        order.getElderId(),
                        "service-order-cancel:" + order.getId(),
                        DeviceMessageDirectionEnum.CANCEL.getCode(),
                        REMINDER_CAPABILITY,
                        "cancel",
                        java.util.Map.of("commandId", order.getReminderCommandId()),
                        LocalDateTime.now().plusMinutes(5),
                        "service-order:" + order.getId(),
                        null));
    }

    private void requireBookingPermission(Long elderId) {
        elderQueryApi.requireAccess(elderId, AuthorizationScopeEnum.BOOK_SERVICE.getCode());
    }

    private ServiceItem requireAvailableItem(Long itemId) {
        var item = requireItem(itemId);
        if (!ServiceItemStatusEnum.AVAILABLE.getCode().equals(item.getStatus())) {
            throw exception(SERVICE_ITEM_UNAVAILABLE);
        }
        return item;
    }

    private ServiceItem requireItem(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw exception(SERVICE_ITEM_NOT_FOUND);
        }
        return serviceItemRepository
                .findById(itemId)
                .orElseThrow(() -> exception(SERVICE_ITEM_NOT_FOUND));
    }

    private void validate(ServiceOrder order) {
        if (order.getElderId() == null
                || order.getElderId() <= 0
                || order.getServiceItemId() == null
                || order.getServiceItemId() <= 0
                || order.getQuantity() == null
                || order.getQuantity() <= 0
                || order.getScheduledAt() == null
                || !order.getScheduledAt().isAfter(LocalDateTime.now())
                || isBlankOrTooLong(order.getServiceAddress(), 500)
                || order.getNotes() != null && order.getNotes().length() > 1000) {
            throw exception(SERVICE_ORDER_INVALID);
        }
    }

    private void requireCancellable(ServiceOrder order) {
        var status = order.getStatus();
        if (!ServiceOrderStatusEnum.CREATED.getCode().equals(status)
                && !ServiceOrderStatusEnum.CONFIRMED.getCode().equals(status)) {
            throw exception(SERVICE_ORDER_STATUS_TRANSITION_INVALID);
        }
    }

    private void requireStatus(ServiceOrder order, ServiceOrderStatusEnum expected) {
        if (!expected.getCode().equals(order.getStatus())) {
            throw exception(SERVICE_ORDER_STATUS_TRANSITION_INVALID);
        }
    }

    private String reminderCommandId(Long orderId) {
        return "service-order-reminder:" + orderId;
    }

    private ServiceOrderSnapshot toSnapshot(ServiceOrderVO order) {
        return new ServiceOrderSnapshot(
                order.id(),
                order.elderId(),
                order.serviceItemId(),
                order.quantity(),
                order.scheduledAt(),
                order.serviceAddress(),
                order.notes(),
                order.status());
    }

    private void requireExpectedVersion(ServiceOrder order, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(order)) {
            throw exception(MALL_VERSION_CONFLICT);
        }
    }

    private void bumpVersion(ServiceOrder order) {
        order.setVersion(currentVersion(order) + 1);
    }

    private int currentVersion(ServiceOrder order) {
        return order.getVersion() == null ? 0 : order.getVersion();
    }

    private boolean isBlankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(SERVICE_ORDER_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }
}
