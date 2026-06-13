package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.billing.domain.CreditRedeemCode;
import com.xuejiai.aaf.module.billing.repository.CreditRedeemCodeRepository;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodeCreateDTO;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodePageParam;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodeVO;

import lombok.RequiredArgsConstructor;

/** 积分兑换码 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditRedeemCodeService
        extends BaseCrudService<
                CreditRedeemCode,
                CreditRedeemCodeVO,
                CreditRedeemCodeCreateDTO,
                CreditRedeemCodeCreateDTO,
                CreditRedeemCodePageParam> {

    private final CreditRedeemCodeRepository redeemCodeRepository;
    private final CreditService creditService;

    @Override
    protected JpaRepository<CreditRedeemCode, Long> getRepository() {
        return redeemCodeRepository;
    }

    @Override
    protected JpaSpecificationExecutor<CreditRedeemCode> getSpecExecutor() {
        return redeemCodeRepository;
    }

    @Override
    protected CreditRedeemCodeVO toVO(CreditRedeemCode e) {
        return new CreditRedeemCodeVO(
                e.getId(),
                e.getCodePrefix(),
                e.getCreditAmount(),
                e.getBatchType(),
                e.getStatus(),
                e.getExpiresAt(),
                e.getRedeemedByUserId(),
                e.getRedeemedAt(),
                e.getRemark(),
                e.getCreateTime());
    }

    @Override
    protected CreditRedeemCode toEntity(CreditRedeemCodeCreateDTO dto) {
        var code = new CreditRedeemCode();
        var rawCode = CreditRedeemSecurityUtil.randomSecret("CRED-", 24);
        code.setCodeHash(CreditRedeemSecurityUtil.sha256(rawCode));
        code.setCodePrefix(rawCode.substring(0, 10) + "...");
        code.setCreditAmount(dto.creditAmount());
        code.setExpiresAt(dto.expiresAt());
        code.setRemark(dto.remark());
        // rawCode 存到 remark 临时字段返回给调用方（仅创建时可见）
        code.setRemark(dto.remark());
        return code;
    }

    @Override
    protected void updateEntity(CreditRedeemCode entity, CreditRedeemCodeCreateDTO dto) {
        entity.setCreditAmount(dto.creditAmount());
        entity.setExpiresAt(dto.expiresAt());
        entity.setRemark(dto.remark());
    }

    @Override
    protected String entityName() {
        return "积分兑换码";
    }

    /** 创建兑换码并返回明文（仅此时可见）。 */
    @Transactional
    public String createAndReturnRawCode(CreditRedeemCodeCreateDTO dto) {
        var rawCode = CreditRedeemSecurityUtil.randomSecret("CRED-", 24);
        var code = new CreditRedeemCode();
        code.setCodeHash(CreditRedeemSecurityUtil.sha256(rawCode));
        code.setCodePrefix(rawCode.substring(0, 10) + "...");
        code.setCreditAmount(dto.creditAmount());
        code.setBatchType(dto.batchType() != null ? dto.batchType() : "REWARD");
        code.setExpiresAt(dto.expiresAt());
        code.setRemark(dto.remark());
        redeemCodeRepository.save(code);
        return rawCode;
    }

    /** 批量创建兑换码，返回所有明文列表。 */
    @Transactional
    public java.util.List<String> createBatch(CreditRedeemCodeCreateDTO dto, int count) {
        if (count < 1 || count > 500) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "批量数量需在 1-500 之间");
        }
        var results = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            results.add(createAndReturnRawCode(dto));
        }
        return results;
    }

    /** 用户兑换积分码。 */
    @Transactional
    public long redeem(Long userId, String rawCode) {
        var code =
                redeemCodeRepository
                        .findByCodeHashForUpdate(CreditRedeemSecurityUtil.sha256(rawCode))
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "兑换码不存在"));

        if (!"UNUSED".equals(code.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已使用或已失效");
        }
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(LocalDateTime.now())) {
            code.setStatus("EXPIRED");
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已过期");
        }

        code.setStatus("REDEEMED");
        code.setRedeemedByUserId(userId);
        code.setRedeemedAt(LocalDateTime.now());
        creditService.earnBatch(
                userId,
                code.getCreditAmount(),
                code.getBatchType(),
                "REDEEM_CODE",
                code.getCodePrefix(),
                null);
        return code.getCreditAmount();
    }
}
