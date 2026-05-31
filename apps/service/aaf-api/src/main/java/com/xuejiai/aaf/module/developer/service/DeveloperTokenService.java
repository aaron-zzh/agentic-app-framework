package com.xuejiai.aaf.module.developer.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.developer.domain.DeveloperTokenAccount;
import com.xuejiai.aaf.module.developer.domain.DeveloperTokenTransaction;
import com.xuejiai.aaf.module.developer.repository.DeveloperTokenAccountRepository;
import com.xuejiai.aaf.module.developer.repository.DeveloperTokenTransactionRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperTokenAccountVO;
import com.xuejiai.aaf.module.developer.vo.DeveloperTokenTransactionVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeveloperTokenService {

    private final DeveloperTokenAccountRepository accountRepository;
    private final DeveloperTokenTransactionRepository transactionRepository;

    @Transactional
    public DeveloperTokenAccount getOrCreateAccount(Long developerId) {
        return accountRepository
                .findByDeveloperIdForUpdate(developerId)
                .orElseGet(
                        () -> {
                            var account = new DeveloperTokenAccount();
                            account.setDeveloperId(developerId);
                            return accountRepository.saveAndFlush(account);
                        });
    }

    @Transactional(readOnly = true)
    public DeveloperTokenAccountVO getAccountVO(Long developerId) {
        var account = accountRepository.findByDeveloperId(developerId).orElse(null);
        if (account == null) {
            return new DeveloperTokenAccountVO(developerId, 0, 0, 0, 0);
        }
        return toVO(account);
    }

    @Transactional(readOnly = true)
    public void precheck(Long developerId) {
        var balance =
                accountRepository.findByDeveloperId(developerId).map(DeveloperTokenAccount::getBalanceTokens).orElse(0L);
        if (balance <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "开发者托管 Token 余额不足");
        }
    }

    @Transactional
    public void earn(Long developerId, long tokens, String source, String bizId) {
        if (tokens <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Token 入账数量必须大于 0");
        }
        var account = getOrCreateAccount(developerId);
        account.setBalanceTokens(account.getBalanceTokens() + tokens);
        account.setTotalEarnedTokens(account.getTotalEarnedTokens() + tokens);
        accountRepository.save(account);
        record(account, "EARN", tokens, source, bizId);
    }

    @Transactional
    public void spend(Long developerId, long tokens, String source, String bizId) {
        if (tokens <= 0) {
            return;
        }
        var account =
                accountRepository
                        .findByDeveloperIdForUpdate(developerId)
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "开发者 Token 账户不存在"));
        if (account.getBalanceTokens() < tokens) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "开发者托管 Token 余额不足");
        }
        account.setBalanceTokens(account.getBalanceTokens() - tokens);
        account.setTotalSpentTokens(account.getTotalSpentTokens() + tokens);
        accountRepository.save(account);
        record(account, "SPEND", tokens, source, bizId);
    }

    @Transactional(readOnly = true)
    public Page<DeveloperTokenTransactionVO> listTransactions(Long developerId, Pageable pageable) {
        return transactionRepository.findByDeveloperId(developerId, pageable).map(this::toVO);
    }

    private void record(
            DeveloperTokenAccount account, String type, long amountTokens, String source, String bizId) {
        var tx = new DeveloperTokenTransaction();
        tx.setAccountId(account.getId());
        tx.setDeveloperId(account.getDeveloperId());
        tx.setType(type);
        tx.setAmountTokens(amountTokens);
        tx.setBalanceAfterTokens(account.getBalanceTokens());
        tx.setSource(source);
        tx.setBizId(bizId);
        transactionRepository.save(tx);
    }

    private DeveloperTokenAccountVO toVO(DeveloperTokenAccount account) {
        return new DeveloperTokenAccountVO(
                account.getDeveloperId(),
                account.getBalanceTokens(),
                account.getFrozenTokens(),
                account.getTotalEarnedTokens(),
                account.getTotalSpentTokens());
    }

    private DeveloperTokenTransactionVO toVO(DeveloperTokenTransaction tx) {
        return new DeveloperTokenTransactionVO(
                tx.getId(),
                tx.getType(),
                tx.getAmountTokens(),
                tx.getBalanceAfterTokens(),
                tx.getSource(),
                tx.getBizId(),
                tx.getCreateTime());
    }
}
