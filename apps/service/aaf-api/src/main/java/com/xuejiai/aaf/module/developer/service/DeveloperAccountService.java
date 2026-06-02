package com.xuejiai.aaf.module.developer.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.developer.domain.DeveloperAccount;
import com.xuejiai.aaf.module.developer.repository.DeveloperAccountRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperAccountVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeveloperAccountService {

    private final DeveloperAccountRepository accountRepository;
    private final DeveloperTokenService tokenService;
    private final OperatorContext operatorContext;

    @Transactional
    public DeveloperAccount getOrCreateCurrent() {
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        return getOrCreateByUserId(userId);
    }

    @Transactional
    public DeveloperAccount getOrCreateByUserId(Long userId) {
        var account =
                accountRepository
                        .findByUserId(userId)
                        .orElseGet(
                                () -> {
                                    var created = new DeveloperAccount();
                                    created.setUserId(userId);
                                    created.setName("developer-" + userId);
                                    created.setDeveloperCode(generateDeveloperCode());
                                    return accountRepository.save(created);
                                });
        tokenService.getOrCreateAccount(account.getId());
        return account;
    }

    @Transactional(readOnly = true)
    public DeveloperAccount getById(Long developerId) {
        return accountRepository
                .findById(developerId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "开发者账户不存在"));
    }

    public DeveloperAccountVO toVO(DeveloperAccount account) {
        return new DeveloperAccountVO(
                account.getId(),
                account.getDeveloperCode(),
                account.getUserId(),
                account.getName(),
                account.getStatus(),
                account.getLicenseTier(),
                account.getAllowManagedGateway(),
                account.getAllowSubProxy(),
                account.getMaxProxyDepth());
    }

    private String generateDeveloperCode() {
        return "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
