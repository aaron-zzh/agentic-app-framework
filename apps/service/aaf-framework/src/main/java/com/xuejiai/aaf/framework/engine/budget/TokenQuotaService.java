package com.xuejiai.aaf.framework.engine.budget;

import org.springframework.stereotype.Service;

/** 管理用户 Token 配额。当前使用简单的默认配额，后续可对接会员/积分系统。 */
@Service
public class TokenQuotaService {

    private static final long DEFAULT_MONTHLY_QUOTA = 0;

    public long getQuota(Long userId) {
        return DEFAULT_MONTHLY_QUOTA;
    }
}
