package com.xuejiai.aaf.module.developer.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.developer.domain.DeveloperRedeemCode;
import com.xuejiai.aaf.module.developer.repository.DeveloperRedeemCodeRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperRedeemCodeCreateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperRedeemCodeCreateVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeveloperRedeemCodeService {

    private static final String CODE_PREFIX = "AAFRC-";

    private final DeveloperRedeemCodeRepository redeemCodeRepository;
    private final DeveloperTokenService tokenService;

    @Transactional
    public DeveloperRedeemCodeCreateVO create(DeveloperRedeemCodeCreateDTO dto) {
        var rawCode = DeveloperSecurityUtil.randomSecret(CODE_PREFIX, 28);
        var code = new DeveloperRedeemCode();
        code.setCodeHash(DeveloperSecurityUtil.sha256(rawCode));
        code.setCodePrefix(rawCode.substring(0, 12) + "...");
        code.setTokenAmount(dto.tokenAmount());
        code.setExpiresAt(dto.expiresAt());
        code.setRemark(dto.remark());
        redeemCodeRepository.save(code);
        return new DeveloperRedeemCodeCreateVO(code.getId(), rawCode, code.getCodePrefix(), code.getTokenAmount());
    }

    @Transactional
    public long redeem(Long developerId, String rawCode) {
        var code =
                redeemCodeRepository
                        .findByCodeHashForUpdate(DeveloperSecurityUtil.sha256(rawCode))
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "兑换码不存在"));
        if (!"UNUSED".equals(code.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已使用或已失效");
        }
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(LocalDateTime.now())) {
            code.setStatus("EXPIRED");
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已过期");
        }
        code.setStatus("REDEEMED");
        code.setRedeemedByDeveloperId(developerId);
        code.setRedeemedAt(LocalDateTime.now());
        tokenService.earn(developerId, code.getTokenAmount(), "REDEEM_CODE", code.getCodePrefix());
        return code.getTokenAmount();
    }
}
