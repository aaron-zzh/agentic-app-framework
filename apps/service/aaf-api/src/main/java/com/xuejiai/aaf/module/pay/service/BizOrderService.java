package com.xuejiai.aaf.module.pay.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.BizOrderStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.pay.domain.BizOrder;
import com.xuejiai.aaf.module.pay.domain.BizOrderItem;
import com.xuejiai.aaf.module.pay.repository.BizOrderItemRepository;
import com.xuejiai.aaf.module.pay.repository.BizOrderRepository;
import com.xuejiai.aaf.module.pay.vo.*;

import lombok.RequiredArgsConstructor;

/** 业务订单服务 */
@Service
@RequiredArgsConstructor
public class BizOrderService {

    private final BizOrderRepository bizOrderRepository;
    private final BizOrderItemRepository itemRepository;

    /** 创建业务订单（含明细行） */
    @Transactional
    public BizOrderVO create(Long userId, BizOrderCreateDTO dto) {
        var order = new BizOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setOrderType(dto.orderType());
        order.setSubject(dto.subject());
        order.setTotalAmount(dto.totalAmount());
        bizOrderRepository.save(order);

        // 保存明细行
        if (dto.items() != null) {
            for (var itemDto : dto.items()) {
                var item = new BizOrderItem();
                item.setOrderId(order.getId());
                item.setProductType(itemDto.productType());
                item.setProductId(itemDto.productId());
                item.setProductName(itemDto.productName());
                item.setQuantity(itemDto.quantity());
                item.setUnitPrice(itemDto.unitPrice());
                item.setTotalPrice(itemDto.unitPrice() * itemDto.quantity());
                itemRepository.save(item);
            }
        }
        return toVO(order);
    }

    /** 关联支付单 */
    @Transactional
    public void bindPayOrder(Long bizOrderId, Long payOrderId) {
        var order = getOrder(bizOrderId);
        order.setPayOrderId(payOrderId);
        bizOrderRepository.save(order);
    }

    /** 标记已支付 */
    @Transactional
    public void markPaid(Long bizOrderId) {
        var order = getOrder(bizOrderId);
        order.setStatus(BizOrderStatusEnum.PAID.getCode());
        bizOrderRepository.save(order);
    }

    /** 查询单个（含明细） */
    @Transactional(readOnly = true)
    public BizOrderVO getById(Long id) {
        return toVO(getOrder(id));
    }

    /** 查询订单明细行 */
    @Transactional(readOnly = true)
    public List<BizOrderItemVO> getItems(Long orderId) {
        return itemRepository.findByOrderId(orderId).stream().map(this::toItemVO).toList();
    }

    /** 分页查询用户订单 */
    @Transactional(readOnly = true)
    public Page<BizOrderVO> listByUser(Long userId, Pageable pageable) {
        return bizOrderRepository.findByUserId(userId, pageable).map(this::toVO);
    }

    /** 根据支付单 ID 查找业务订单 */
    @Transactional(readOnly = true)
    public BizOrder findByPayOrderId(Long payOrderId) {
        return bizOrderRepository.findByPayOrderId(payOrderId).orElse(null);
    }

    private BizOrder getOrder(Long id) {
        return bizOrderRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "业务订单不存在"));
    }

    private String generateOrderNo() {
        return "BIZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private BizOrderVO toVO(BizOrder o) {
        return new BizOrderVO(
                o.getId(),
                o.getOrderNo(),
                o.getUserId(),
                o.getOrderType(),
                o.getSubject(),
                o.getTotalAmount(),
                o.getPayOrderId(),
                o.getStatus(),
                o.getCreateTime());
    }

    private BizOrderItemVO toItemVO(BizOrderItem i) {
        return new BizOrderItemVO(
                i.getId(),
                i.getOrderId(),
                i.getProductType(),
                i.getProductId(),
                i.getProductName(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getTotalPrice());
    }
}
