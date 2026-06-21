package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawTypeEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * user-facing 提现申请 DTO（仅包含用户可填字段；contactId / status 由后端自动填充）。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "提现申请")
public record WithdrawApplyDTO(
        @NotNull @Min(100) @Schema(description = "申请提现金额（分），最低 1 元") Long amount,
        @NotNull @Schema(description = "提现类型：WECHAT/ALIPAY/BANK") BrokerageWithdrawTypeEnum type,
        @NotBlank @Schema(description = "收款人真实姓名") String accountName,
        @NotBlank @Schema(description = "收款账号（微信/支付宝账号或银行卡号）") String accountNo) {}
