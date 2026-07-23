package com.xuejiai.aaf.module.billing.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.credit.CreditTransaction;
import com.xuejiai.aaf.module.billing.service.WalletTransactionCrudService;
import com.xuejiai.aaf.module.billing.vo.WalletTransactionPageParam;
import com.xuejiai.aaf.module.billing.vo.WalletTransactionVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "钱包流水管理")
@RestController
@RequestMapping("/api/billing/wallet-transactions")
@RequiredArgsConstructor
public class WalletTransactionController
        extends BaseCrudController<
                CreditTransaction, WalletTransactionVO, Void, Void, WalletTransactionPageParam> {

    private final WalletTransactionCrudService walletTransactionCrudService;

    @Override
    protected BaseCrudService<
                    CreditTransaction, WalletTransactionVO, Void, Void, WalletTransactionPageParam>
            getService() {
        return walletTransactionCrudService;
    }
}
