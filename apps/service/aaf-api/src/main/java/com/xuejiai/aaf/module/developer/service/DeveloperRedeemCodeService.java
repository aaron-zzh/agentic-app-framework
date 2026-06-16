package com.xuejiai.aaf.module.developer.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.developer.domain.DeveloperRedeemCode;
import com.xuejiai.aaf.module.developer.repository.DeveloperRedeemCodeRepository;
import com.xuejiai.aaf.module.developer.repository.DeveloperSubscriptionPlanRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperRedeemCodeCreateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperRedeemCodeCreateVO;
import com.xuejiai.aaf.module.system.license.service.LicenseIssueService;
import com.xuejiai.aaf.module.system.license.vo.LicenseIssueDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeveloperRedeemCodeService {

    private static final String CODE_PREFIX = "AAFRC-";

    private final DeveloperRedeemCodeRepository redeemCodeRepository;
    private final DeveloperTokenService tokenService;
    private final DeveloperSubscriptionService subscriptionService;
    private final DeveloperSubscriptionPlanRepository planRepository;
    private final LicenseIssueService licenseIssueService;

    @Transactional
    public DeveloperRedeemCodeCreateVO create(DeveloperRedeemCodeCreateDTO dto) {
        var rawCode = DeveloperSecurityUtil.randomSecret(CODE_PREFIX, 28);
        var code = new DeveloperRedeemCode();
        code.setType(dto.type());
        code.setCodeHash(DeveloperSecurityUtil.sha256(rawCode));
        code.setCodePrefix(rawCode.substring(0, 12) + "...");
        code.setExpiresAt(dto.expiresAt());
        code.setRemark(dto.remark());

        String licenseJwt = null;

        if ("LICENSE".equals(dto.type())) {
            // 验证套餐存在
            var planCode = dto.planCode();
            if (planCode == null || planCode.isBlank()) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "LICENSE 类型兑换码必须指定套餐 planCode");
            }
            var plan = planRepository.findByCode(planCode)
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "套餐不存在：" + planCode));
            code.setPlanCode(planCode);
            code.setTokenAmount(0L);

            // 预签发 license.jwt（subject 留空，兑换时由实际 developerId 决定）
            var issueDTO = new LicenseIssueDTO(
                    null,
                    plan.getCode(),
                    null,
                    Set.of("developer"),
                    dto.expiresAt() != null
                            ? dto.expiresAt().toInstant(java.time.ZoneOffset.UTC)
                            : java.time.Instant.now().plusSeconds(365L * 24 * 3600));
            licenseJwt = licenseIssueService.issue(issueDTO).token();
            code.setLicenseJwt(licenseJwt);
        } else {
            // TOKEN 类型
            if (dto.tokenAmount() == null || dto.tokenAmount() < 1) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "TOKEN 类型兑换码必须指定 tokenAmount");
            }
            code.setTokenAmount(dto.tokenAmount());
        }

        redeemCodeRepository.save(code);
        return new DeveloperRedeemCodeCreateVO(
                code.getId(), rawCode, code.getCodePrefix(), code.getTokenAmount(), licenseJwt);
    }

    @Transactional
    public Object redeem(Long developerId, String rawCode) {
        var code = redeemCodeRepository
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

        if ("LICENSE".equals(code.getType())) {
            // 激活订阅
            subscriptionService.subscribe(developerId, code.getPlanCode());
            return code.getLicenseJwt();
        } else {
            // 发放 token
            tokenService.earn(developerId, code.getTokenAmount(), "REDEEM_CODE", code.getCodePrefix());
            return code.getTokenAmount();
        }
    }

    /**
     * 公开激活接口专用：无需登录，验证 LICENSE 类型兑换码，返回预签发的 license.jwt。
     * 兑换码标记为 REDEEMED，一次性有效。
     */
    @Transactional
    public String activateLicense(String rawCode) {
        var code = redeemCodeRepository
                .findByCodeHashForUpdate(DeveloperSecurityUtil.sha256(rawCode))
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "兑换码不存在"));

        if (!"LICENSE".equals(code.getType())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该兑换码不是 LICENSE 类型");
        }
        if (!"UNUSED".equals(code.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已使用或已失效");
        }
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(LocalDateTime.now())) {
            code.setStatus("EXPIRED");
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已过期");
        }

        code.setStatus("REDEEMED");
        code.setRedeemedAt(LocalDateTime.now());
        return code.getLicenseJwt();
    }
}
