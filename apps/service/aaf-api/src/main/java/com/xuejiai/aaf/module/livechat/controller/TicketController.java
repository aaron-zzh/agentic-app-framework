package com.xuejiai.aaf.module.livechat.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.livechat.domain.Ticket;
import com.xuejiai.aaf.module.livechat.domain.TicketRecord;
import com.xuejiai.aaf.module.livechat.service.RatingService;
import com.xuejiai.aaf.module.livechat.service.TicketService;
import com.xuejiai.aaf.module.livechat.vo.RatingStatVO;
import com.xuejiai.aaf.module.livechat.vo.RatingSubmitDTO;
import com.xuejiai.aaf.module.livechat.vo.TicketCreateDTO;
import com.xuejiai.aaf.module.livechat.vo.TicketStatVO;
import com.xuejiai.aaf.module.livechat.vo.TicketVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 工单与评价 API。
 */
@RestController
@RequestMapping("/api/livechat")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final RatingService ratingService;

    // ==================== 满意度评价 ====================

    /** 提交评价 */
    @PostMapping("/ratings")
    public Result<Void> submitRating(@RequestBody @Valid RatingSubmitDTO dto) {
        ratingService.submit(dto);
        return Result.success(null);
    }

    /** 评价统计 */
    @GetMapping("/ratings/statistics")
    public Result<RatingStatVO> ratingStatistics(
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(ratingService.getStatistics(LocalDateTime.now().minusDays(days)));
    }

    /** 坐席平均分 */
    @GetMapping("/ratings/staff/{staffId}")
    public Result<Double> staffAvgScore(@PathVariable Long staffId) {
        return Result.success(ratingService.getStaffAvgScore(staffId));
    }

    // ==================== 工单管理 ====================

    /** 创建工单 */
    @PostMapping("/tickets")
    public Result<TicketVO> createTicket(@RequestBody @Valid TicketCreateDTO dto) {
        return Result.success(toVO(ticketService.create(dto)));
    }

    /** 工单详情 */
    @GetMapping("/tickets/{ticketId}")
    public Result<TicketVO> getTicket(@PathVariable Long ticketId) {
        return Result.success(toVO(ticketService.getById(ticketId)));
    }

    /** 用户工单列表 */
    @GetMapping("/tickets/user/{userId}")
    public Result<List<TicketVO>> userTickets(@PathVariable Long userId) {
        return Result.success(ticketService.listByUser(userId).stream().map(this::toVO).toList());
    }

    /** 处理人待办工单 */
    @GetMapping("/tickets/assignee/{assigneeId}")
    public Result<List<TicketVO>> assigneeTickets(@PathVariable Long assigneeId) {
        return Result.success(ticketService.listByAssignee(assigneeId).stream().map(this::toVO).toList());
    }

    /** 分配工单 */
    @PostMapping("/tickets/{ticketId}/assign")
    public Result<Void> assign(
            @PathVariable Long ticketId,
            @RequestParam Long assigneeId,
            @RequestParam Long operatorId) {
        ticketService.assign(ticketId, assigneeId, operatorId);
        return Result.success(null);
    }

    /** 提交确认 */
    @PostMapping("/tickets/{ticketId}/confirm")
    public Result<Void> confirm(
            @PathVariable Long ticketId,
            @RequestParam Long operatorId,
            @RequestParam(required = false) String remark) {
        ticketService.submitConfirm(ticketId, operatorId, remark);
        return Result.success(null);
    }

    /** 关闭工单 */
    @PostMapping("/tickets/{ticketId}/close")
    public Result<Void> closeTicket(
            @PathVariable Long ticketId,
            @RequestParam Long operatorId,
            @RequestParam(required = false) String remark) {
        ticketService.close(ticketId, operatorId, remark);
        return Result.success(null);
    }

    /** 重新打开 */
    @PostMapping("/tickets/{ticketId}/reopen")
    public Result<Void> reopen(
            @PathVariable Long ticketId,
            @RequestParam Long operatorId,
            @RequestParam(required = false) String remark) {
        ticketService.reopen(ticketId, operatorId, remark);
        return Result.success(null);
    }

    /** 转派工单 */
    @PostMapping("/tickets/{ticketId}/transfer")
    public Result<Void> transferTicket(
            @PathVariable Long ticketId,
            @RequestParam Long newAssigneeId,
            @RequestParam Long operatorId,
            @RequestParam(required = false) String remark) {
        ticketService.transfer(ticketId, newAssigneeId, operatorId, remark);
        return Result.success(null);
    }

    /** 工单流转记录 */
    @GetMapping("/tickets/{ticketId}/records")
    public Result<List<TicketRecord>> ticketRecords(@PathVariable Long ticketId) {
        return Result.success(ticketService.getRecords(ticketId));
    }

    /** 工单统计 */
    @GetMapping("/tickets/statistics")
    public Result<TicketStatVO> ticketStatistics(
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(ticketService.getStatistics(LocalDateTime.now().minusDays(days)));
    }

    /** SLA 超时扫描 */
    @PostMapping("/tickets/scan-overdue")
    public Result<List<TicketVO>> scanOverdue() {
        return Result.success(ticketService.scanOverdue().stream().map(this::toVO).toList());
    }

    private TicketVO toVO(Ticket t) {
        return new TicketVO(
                t.getId(), t.getTicketNo(), t.getTitle(), t.getDescription(),
                t.getUserId(), t.getSessionId(), t.getType(), t.getPriority(),
                t.getStatus(), t.getAssigneeId(), t.getSlaDueTime(),
                t.getClosedTime(), t.getCreateTime());
    }
}
