package com.xuejiai.aaf.module.billing.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** 会员写操作互斥仓储。调用方必须在同一事务中先 ensure 再 lock。 */
@Repository
@RequiredArgsConstructor
public class MembershipCheckoutGuardRepository {

    private final JdbcTemplate jdbcTemplate;

    public void ensureGuard(Long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO billing_checkout_guard(user_id, create_time, update_time)
                VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO NOTHING
                """,
                userId);
    }

    public void lockGuard(Long userId) {
        var locked =
                jdbcTemplate.queryForObject(
                        """
                        SELECT user_id
                        FROM billing_checkout_guard
                        WHERE user_id = ?
                        FOR UPDATE
                        """,
                        Long.class,
                        userId);
        if (!userId.equals(locked)) {
            throw new IllegalStateException("会员结账互斥行锁定失败: userId=" + userId);
        }
    }
}
