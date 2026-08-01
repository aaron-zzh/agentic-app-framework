-- M3：积分一次性入账幂等键
-- 背景：支付回调并发/重推时，"先查状态再入账"无法阻止两个事务同时通过检查各自加分，
-- 且 credit_transaction 此前没有任何按业务单号的唯一约束，重复加分在数据层没有兜底。
-- 方案：新增 idempotency_key（格式 accountId:source:biz_id），仅一次性入账（CreditService.earn）填充；
-- 周期性发放（月度订阅、周签到等走 earnBatch）同一 biz_id 会合法重复出现，该列保持 NULL，不受唯一约束限制。

ALTER TABLE credit_transaction ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);

COMMENT ON COLUMN credit_transaction.idempotency_key IS '一次性入账幂等键 accountId:source:biz_id，NULL=周期性发放不做幂等约束';

CREATE UNIQUE INDEX IF NOT EXISTS uk_credit_transaction_idempotency
    ON credit_transaction (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted = FALSE;
