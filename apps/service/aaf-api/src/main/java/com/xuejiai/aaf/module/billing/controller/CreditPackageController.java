package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.billing.repository.CreditPackageRepository;
import com.xuejiai.aaf.module.billing.vo.CreditPackageVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 积分充值套餐接口 */
@Tag(name = "积分充值套餐")
@RestController
@RequestMapping("/api/billing/credit-packages")
@RequiredArgsConstructor
public class CreditPackageController {

    private final CreditPackageRepository creditPackageRepository;

    @Operation(summary = "获取积分充值套餐列表")
    @GetMapping
    public Result<List<CreditPackageVO>> list() {
        var packages =
                creditPackageRepository.findByStatusOrderBySortAsc("ENABLED").stream()
                        .map(
                                p ->
                                        new CreditPackageVO(
                                                p.getId(),
                                                p.getName(),
                                                p.getCredits(),
                                                p.getBonusCredits(),
                                                p.getPrice(),
                                                p.getGroupLabel(),
                                                Boolean.TRUE.equals(p.getRecommended())))
                        .toList();
        return Result.success(packages);
    }
}
