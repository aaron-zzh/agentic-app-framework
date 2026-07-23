package com.xuejiai.aaf.module.billing.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.engine.credit.CreditAccount;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransaction;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionType;
import com.xuejiai.aaf.module.billing.vo.WalletTransactionPageParam;
import com.xuejiai.aaf.module.billing.vo.WalletTransactionVO;
import com.xuejiai.aaf.module.system.user.api.UserRelationService;

import lombok.RequiredArgsConstructor;

/** 钱包积分流水只读 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletTransactionCrudService
        extends ReadonlyCrudService<
                CreditTransaction, WalletTransactionVO, WalletTransactionPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "accountId",
                    "type",
                    "amount",
                    "balanceAfter",
                    "category",
                    "batchType",
                    "expireAt",
                    "remain",
                    "createTime");

    private final CreditTransactionRepository transactionRepository;
    private final CreditAccountRepository accountRepository;
    private final UserRelationService userRelationService;

    @Override
    protected CreditTransactionRepository getRepository() {
        return transactionRepository;
    }

    @Override
    protected Specification<CreditTransaction> buildSpec(WalletTransactionPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getAccountId() != null)
                predicates.add(cb.equal(root.get("accountId"), request.getAccountId()));
            if (request.getType() != null && !request.getType().isBlank())
                predicates.add(cb.equal(root.get("type"), parseType(request.getType())));
            if (request.getCategory() != null && !request.getCategory().isBlank())
                predicates.add(cb.equal(root.get("category"), request.getCategory().trim()));
            if (request.getBatchType() != null && !request.getBatchType().isBlank())
                predicates.add(cb.equal(root.get("batchType"), request.getBatchType().trim()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected WalletTransactionVO toVO(CreditTransaction transaction) {
        return toVOList(List.of(transaction), "detail").getFirst();
    }

    @Override
    protected List<WalletTransactionVO> toVOList(
            List<CreditTransaction> transactions, String fieldSet) {
        if (transactions.isEmpty()) return List.of();
        Map<Long, CreditAccount> accounts =
                accountRepository
                        .findAllById(
                                transactions.stream()
                                        .map(CreditTransaction::getAccountId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(CreditAccount::getId, account -> account));
        var users =
                userRelationService.findRefs(
                        accounts.values().stream()
                                .map(CreditAccount::getUserId)
                                .collect(Collectors.toSet()));
        return transactions.stream()
                .map(
                        transaction ->
                                toVO(transaction, accounts.get(transaction.getAccountId()), users))
                .toList();
    }

    private CreditTransactionType parseType(String value) {
        try {
            return CreditTransactionType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "未知的钱包流水类型: " + value);
        }
    }

    private WalletTransactionVO toVO(
            CreditTransaction transaction, CreditAccount account, Map<Long, ResourceRefDTO> users) {
        var user = account == null ? null : users.get(account.getUserId());
        return new WalletTransactionVO(
                transaction.getId(),
                user,
                transaction.getAccountId(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getSource(),
                transaction.getCategory(),
                transaction.getBizType(),
                transaction.getBizId(),
                transaction.getBatchType(),
                transaction.getExpireAt(),
                transaction.getRemain(),
                transaction.getRemark(),
                transaction.getCreateTime());
    }
}
