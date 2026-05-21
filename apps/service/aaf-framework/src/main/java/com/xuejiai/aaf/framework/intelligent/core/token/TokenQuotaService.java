/**
 * Token 配额服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.token;

import org.springframework.stereotype.Service;

/** 管理用户 Token 配额。 当前使用简单的默认配额，后续可对接会员/积分系统。 */
@Service
public class TokenQuotaService {

    /** 默认月配额（0 表示无限制） */
    private static final long DEFAULT_MONTHLY_QUOTA = 0;

    /** 获取用户月配额 */
    public long getQuota(Long userId) {
        // TODO: 对接会员/积分系统，按用户等级返回不同配额
        return DEFAULT_MONTHLY_QUOTA;
    }
}
