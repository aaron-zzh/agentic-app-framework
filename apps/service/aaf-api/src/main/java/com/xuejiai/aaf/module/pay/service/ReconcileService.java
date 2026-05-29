package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.common.enums.pay.ReconcileDiffTypeEnum;
import com.xuejiai.aaf.common.enums.pay.ReconcileStatusEnum;
import com.xuejiai.aaf.framework.engine.settlement.PayChannelAdapter;
import com.xuejiai.aaf.module.pay.domain.PayOrder;
import com.xuejiai.aaf.module.pay.domain.ReconcileRecord;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;
import com.xuejiai.aaf.module.pay.repository.ReconcileRecordRepository;
import com.xuejiai.aaf.module.pay.vo.ReconcileRecordVO;
import com.xuejiai.aaf.module.pay.vo.FinanceSummaryVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 对账服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileService {

    private final List<PayChannelAdapter> adapters;
    private final PayOrderRepository payOrderRepository;
    private final ReconcileRecordRepository reconcileRecordRepository;
    private final ObjectMapper objectMapper;

    /** 执行指定日期和渠道的对账 */
    @Transactional
    public ReconcileRecordVO reconcile(LocalDate date, String channelCode) {
        // 查找对应渠道适配器
        var adapter =
                adapters.stream()
                        .filter(a -> a.supportedChannelCodes().contains(channelCode))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("未找到渠道适配器: " + channelCode));

        // 下载渠道账单
        var billItems = adapter.downloadBill(date);

        // 查询本地当日订单
        var startTime = LocalDateTime.of(date, LocalTime.MIN);
        var endTime = LocalDateTime.of(date, LocalTime.MAX);
        var localOrders =
                payOrderRepository.findByStatusAndCreateTimeAfter(
                        PayOrderStatusEnum.SUCCESS.getCode(), startTime);
        // 过滤当日+当渠道
        var localOrderMap =
                localOrders.stream()
                        .filter(o -> o.getChannelCode().equals(channelCode))
                        .filter(o -> !o.getCreateTime().isAfter(endTime))
                        .collect(Collectors.toMap(PayOrder::getMerchantOrderNo, Function.identity()));

        // 比对
        var diffs = new ArrayList<DiffItem>();
        int matchedCount = 0;
        long totalIncome = 0L;

        var billMap =
                billItems.stream()
                        .collect(Collectors.toMap(PayChannelAdapter.BillItem::outTradeNo, Function.identity()));

        // 遍历渠道账单
        for (var bill : billItems) {
            var local = localOrderMap.get(bill.outTradeNo());
            if (local == null) {
                diffs.add(new DiffItem(bill.outTradeNo(), ReconcileDiffTypeEnum.CHANNEL_ONLY.getCode(), "渠道有本地无"));
            } else if (local.getAmount() != bill.amount()) {
                diffs.add(new DiffItem(bill.outTradeNo(), ReconcileDiffTypeEnum.AMOUNT_MISMATCH.getCode(),
                        "本地=%d 渠道=%d".formatted(local.getAmount(), bill.amount())));
            } else {
                matchedCount++;
                totalIncome += bill.amount();
            }
        }

        // 检查本地有渠道无
        for (var entry : localOrderMap.entrySet()) {
            if (!billMap.containsKey(entry.getKey())) {
                diffs.add(new DiffItem(entry.getKey(), ReconcileDiffTypeEnum.LOCAL_ONLY.getCode(), "本地有渠道无"));
            }
        }

        // 保存对账记录
        var record = new ReconcileRecord();
        record.setReconcileDate(date);
        record.setChannelCode(channelCode);
        record.setTotalCount(billItems.size());
        record.setMatchedCount(matchedCount);
        record.setMismatchCount(diffs.size());
        record.setStatus(
                diffs.isEmpty()
                        ? ReconcileStatusEnum.MATCHED.getCode()
                        : ReconcileStatusEnum.MISMATCHED.getCode());
        record.setTotalIncome(totalIncome);
        record.setTotalRefund(0L);
        record.setTotalFee(0L);
        try {
            record.setDiffDetails(diffs.isEmpty() ? null : objectMapper.writeValueAsString(diffs));
        } catch (JsonProcessingException e) {
            record.setDiffDetails(diffs.toString());
        }
        reconcileRecordRepository.save(record);
        return toVO(record);
    }

    /** 查询对账日报 */
    @Transactional(readOnly = true)
    public List<ReconcileRecordVO> listByDateRange(LocalDate start, LocalDate end) {
        return reconcileRecordRepository.findByReconcileDateBetween(start, end).stream()
                .map(this::toVO)
                .toList();
    }

    /** 财务统计汇总 */
    @Transactional(readOnly = true)
    public FinanceSummaryVO financeSummary(LocalDate start, LocalDate end) {
        var records = reconcileRecordRepository.findByReconcileDateBetween(start, end);
        long income = records.stream().mapToLong(ReconcileRecord::getTotalIncome).sum();
        long refund = records.stream().mapToLong(ReconcileRecord::getTotalRefund).sum();
        long fee = records.stream().mapToLong(ReconcileRecord::getTotalFee).sum();
        return new FinanceSummaryVO(income, refund, fee, income - refund - fee);
    }

    private ReconcileRecordVO toVO(ReconcileRecord r) {
        return new ReconcileRecordVO(
                r.getId(),
                r.getReconcileDate(),
                r.getChannelCode(),
                r.getTotalCount(),
                r.getMatchedCount(),
                r.getMismatchCount(),
                r.getStatus(),
                r.getDiffDetails(),
                r.getTotalIncome(),
                r.getTotalRefund(),
                r.getTotalFee(),
                r.getCreateTime());
    }

    /** 差异条目（序列化为 JSON） */
    record DiffItem(String outTradeNo, String diffType, String detail) {}
}
