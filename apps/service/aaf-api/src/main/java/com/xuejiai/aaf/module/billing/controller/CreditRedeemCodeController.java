package com.xuejiai.aaf.module.billing.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.domain.CreditRedeemCode;
import com.xuejiai.aaf.module.billing.service.CreditRedeemCodeService;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodeCreateDTO;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodePageParam;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodeVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "积分兑换码")
@RestController
@RequestMapping("/api/billing/credit-redeem-codes")
@RequiredArgsConstructor
public class CreditRedeemCodeController
        extends BaseCrudController<
                CreditRedeemCode,
                CreditRedeemCodeVO,
                CreditRedeemCodeCreateDTO,
                CreditRedeemCodeCreateDTO,
                CreditRedeemCodePageParam> {

    private final CreditRedeemCodeService redeemCodeService;
    private final OperatorContext operatorContext;

    @Override
    protected BaseCrudService<
                    CreditRedeemCode,
                    CreditRedeemCodeVO,
                    CreditRedeemCodeCreateDTO,
                    CreditRedeemCodeCreateDTO,
                    CreditRedeemCodePageParam>
            getService() {
        return redeemCodeService;
    }

    @Operation(summary = "生成兑换码（明文仅本次可见）")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/generate")
    public Result<String> generate(@Valid @RequestBody CreditRedeemCodeCreateDTO dto) {
        return Result.success(redeemCodeService.createAndReturnRawCode(dto));
    }

    @Operation(summary = "批量生成兑换码")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/generate-batch")
    public org.springframework.http.ResponseEntity<byte[]> generateBatch(
            @Valid @RequestBody CreditRedeemCodeCreateDTO dto,
            @RequestParam(defaultValue = "10") int count)
            throws java.io.IOException {
        var codes = redeemCodeService.createBatch(dto, count);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                var out = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("兑换码");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("兑换码");
            header.createCell(1).setCellValue("积分数量");
            header.createCell(2).setCellValue("积分类型");
            header.createCell(3).setCellValue("过期时间");
            for (var index = 0; index < codes.size(); index++) {
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(codes.get(index));
                row.createCell(1).setCellValue(dto.creditAmount());
                row.createCell(2)
                        .setCellValue(dto.batchType() == null ? "REWARD" : dto.batchType());
                row.createCell(3)
                        .setCellValue(
                                dto.expiresAt() == null ? "永不过期" : dto.expiresAt().toString());
            }
            for (var index = 0; index < 4; index++) sheet.autoSizeColumn(index);
            workbook.write(out);
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"redeem-codes.xlsx\"")
                    .contentType(
                            org.springframework.http.MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    @Operation(summary = "兑换积分码")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/redeem")
    public Result<Long> redeem(@RequestBody java.util.Map<String, String> body) {
        var userId =
                operatorContext
                        .currentUserId()
                        .orElseThrow(
                                () ->
                                        new com.xuejiai.aaf.common.exception.BusinessException(
                                                com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                        .UNAUTHORIZED,
                                                "请先登录"));
        return Result.success(redeemCodeService.redeem(userId, body.get("code")));
    }
}
