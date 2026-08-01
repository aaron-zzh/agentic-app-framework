-- M26：退款幂等键与并发超额防护
-- 背景：退款申请以"读 refund_amount → 判断可退 → 成功后写回"处理累计额，并发申请可同时通过校验形成超额退款；
-- 客户端超时重试又会生成新的 refund_no，同一笔退款被提交多次。
-- 方案：
--   1) pay_refund_order 增 request_no（客户端幂等键）+ 唯一索引，重试命中同一退款单，不再新建；
--   2) 累计退款额改为原子条件更新（UPDATE ... WHERE refund_amount + ? <= amount，见 PayOrderRepository#reserveRefundAmount），
--      申请阶段先占额、失败再释放，由数据库行锁保证不超额。

ALTER TABLE pay_refund_order ADD COLUMN IF NOT EXISTS request_no VARCHAR(64);

COMMENT ON COLUMN pay_refund_order.request_no IS '客户端幂等键，同一 request_no 重试复用同一退款单；NULL=历史数据';

CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_order_request_no
    ON pay_refund_order (request_no)
    WHERE request_no IS NOT NULL AND deleted = FALSE;
