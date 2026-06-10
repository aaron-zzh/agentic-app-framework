package com.xuejiai.aaf.module.livechat.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.livechat.TicketOperationEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.chat.livechat.ticket.domain.Ticket;
import com.xuejiai.aaf.module.chat.livechat.ticket.domain.TicketRecord;
import com.xuejiai.aaf.module.chat.livechat.ticket.repository.TicketRecordRepository;
import com.xuejiai.aaf.module.chat.livechat.ticket.repository.TicketRepository;
import com.xuejiai.aaf.module.livechat.vo.TicketCreateDTO;
import com.xuejiai.aaf.module.livechat.vo.TicketStatVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工单管理服务。
 *
 * <p>迁移后使用 chat 模块的 Ticket / TicketRecord / Repository。 Ticket 实体中 type/priority/status/operation
 * 均为大写 String 存储。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketRecordRepository recordRepository;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    /**
     * 创建工单。
     *
     * @param dto 创建请求 DTO
     * @return 保存后的工单
     */
    @Transactional
    public Ticket create(TicketCreateDTO dto) {
        var ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setTitle(dto.title());
        ticket.setDescription(dto.description());
        ticket.setUserId(dto.userId());
        ticket.setConversationId(dto.sessionId());
        // 枚举 name() 转大写 String 存入实体
        ticket.setType(dto.type().name());
        ticket.setPriority(dto.priority().name());
        ticket.setStatus("PENDING");
        // 按优先级计算 SLA 截止时间
        ticket.setSlaDueTime(LocalDateTime.now().plusHours(dto.priority().getSlaHours()));
        var saved = ticketRepository.save(ticket);
        addRecord(saved, TicketOperationEnum.CREATE, dto.userId(), null, "PENDING", null);
        return saved;
    }

    /**
     * 分配工单。
     *
     * @param ticketId 工单 ID
     * @param assigneeId 受理人 ID
     * @param operatorId 操作人 ID
     */
    @Transactional
    public void assign(Long ticketId, Long assigneeId, Long operatorId) {
        var ticket = findById(ticketId);
        var fromStatus = ticket.getStatus();
        ticket.startProcessing(assigneeId);
        ticketRepository.save(ticket);
        addRecord(
                ticket,
                TicketOperationEnum.ASSIGN,
                operatorId,
                fromStatus,
                ticket.getStatus(),
                null);
    }

    /**
     * 提交确认。
     *
     * @param ticketId 工单 ID
     * @param operatorId 操作人 ID
     * @param remark 备注
     */
    @Transactional
    public void submitConfirm(Long ticketId, Long operatorId, String remark) {
        var ticket = findById(ticketId);
        var fromStatus = ticket.getStatus();
        ticket.submitConfirm();
        ticketRepository.save(ticket);
        addRecord(
                ticket,
                TicketOperationEnum.CONFIRM,
                operatorId,
                fromStatus,
                ticket.getStatus(),
                remark);
    }

    /**
     * 关闭工单。
     *
     * @param ticketId 工单 ID
     * @param operatorId 操作人 ID
     * @param remark 备注
     */
    @Transactional
    public void close(Long ticketId, Long operatorId, String remark) {
        var ticket = findById(ticketId);
        var fromStatus = ticket.getStatus();
        ticket.close();
        ticketRepository.save(ticket);
        addRecord(
                ticket,
                TicketOperationEnum.CLOSE,
                operatorId,
                fromStatus,
                ticket.getStatus(),
                remark);
    }

    /**
     * 重新打开工单。
     *
     * @param ticketId 工单 ID
     * @param operatorId 操作人 ID
     * @param remark 备注
     */
    @Transactional
    public void reopen(Long ticketId, Long operatorId, String remark) {
        var ticket = findById(ticketId);
        var fromStatus = ticket.getStatus();
        ticket.reopen();
        ticketRepository.save(ticket);
        addRecord(
                ticket,
                TicketOperationEnum.REOPEN,
                operatorId,
                fromStatus,
                ticket.getStatus(),
                remark);
    }

    /**
     * 转派工单。
     *
     * @param ticketId 工单 ID
     * @param newAssigneeId 新受理人 ID
     * @param operatorId 操作人 ID
     * @param remark 备注
     */
    @Transactional
    public void transfer(Long ticketId, Long newAssigneeId, Long operatorId, String remark) {
        var ticket = findById(ticketId);
        var fromStatus = ticket.getStatus();
        ticket.setAssigneeId(newAssigneeId);
        if ("PENDING".equals(ticket.getStatus())) {
            ticket.setStatus("PROCESSING");
        }
        ticketRepository.save(ticket);
        addRecord(
                ticket,
                TicketOperationEnum.TRANSFER,
                operatorId,
                fromStatus,
                ticket.getStatus(),
                remark);
    }

    /**
     * 查询工单详情。
     *
     * @param ticketId 工单 ID
     * @return 工单实体
     */
    public Ticket getById(Long ticketId) {
        return findById(ticketId);
    }

    /**
     * 查询用户工单列表。
     *
     * @param userId 用户 ID
     * @return 工单列表
     */
    public List<Ticket> listByUser(Long userId) {
        return ticketRepository.findByUserId(userId);
    }

    /**
     * 查询处理人待办工单。
     *
     * @param assigneeId 受理人 ID
     * @return 待办工单列表
     */
    public List<Ticket> listByAssignee(Long assigneeId) {
        return ticketRepository.findByAssigneeIdAndStatusIn(
                assigneeId, List.of("PENDING", "PROCESSING", "CONFIRMING"));
    }

    /**
     * 查询工单流转记录。
     *
     * @param ticketId 工单 ID
     * @return 操作记录列表
     */
    public List<TicketRecord> getRecords(Long ticketId) {
        return recordRepository.findByTicketIdOrderByCreateTimeAsc(ticketId);
    }

    /**
     * SLA 超时扫描（供定时任务调用）。
     *
     * @return 超时工单列表
     */
    public List<Ticket> scanOverdue() {
        var overdueTickets =
                ticketRepository.findByStatusInAndSlaDueTimeBefore(
                        List.of("PENDING", "PROCESSING", "CONFIRMING"), LocalDateTime.now());
        overdueTickets.forEach(
                t ->
                        log.warn(
                                "工单 SLA 超时: ticketNo={}, priority={}, slaDue={}",
                                t.getTicketNo(),
                                t.getPriority(),
                                t.getSlaDueTime()));
        return overdueTickets;
    }

    /**
     * 工单统计。
     *
     * @param since 统计起始时间
     * @return 统计结果 VO
     */
    public TicketStatVO getStatistics(LocalDateTime since) {
        long total = ticketRepository.countCreatedSince(since);
        long closed = ticketRepository.countClosedSince(since);
        long overdue = ticketRepository.countOverdue(LocalDateTime.now());
        double resolveRate = total > 0 ? (double) closed / total : 0.0;
        double overdueRate = total > 0 ? (double) overdue / total : 0.0;
        // 按状态统计（String 直接作为 key）
        Map<String, Long> statusDist =
                ticketRepository.countByStatus().stream()
                        .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        // 按类型统计
        Map<String, Long> typeDist =
                ticketRepository.countByType().stream()
                        .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        return new TicketStatVO(
                total, closed, overdue, resolveRate, overdueRate, statusDist, typeDist);
    }

    private Ticket findById(Long id) {
        return ticketRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "工单不存在"));
    }

    private void addRecord(
            Ticket ticket,
            TicketOperationEnum op,
            Long operatorId,
            String fromStatus,
            String toStatus,
            String remark) {
        var record = new TicketRecord();
        record.setTicketId(ticket.getId());
        record.setOperation(op.name());
        record.setOperatorId(operatorId);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setRecordRemark(remark);
        recordRepository.save(record);
    }

    private String generateTicketNo() {
        var date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "TK" + date + String.format("%05d", SEQ.incrementAndGet() % 100000);
    }
}
