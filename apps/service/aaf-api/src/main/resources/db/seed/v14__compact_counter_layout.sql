-- ============================================================
-- 紧凑化 counter widget 布局：h:3 → h:2，同列下方 widget y 重排
--
-- 背景：v12 init seed 中 personal / admin 预设的 counter 卡片高度
-- (h:3 = 240px) 在 Minimal 风格视觉下显得过空；调整为 h:2 (160px)
-- 更接近设计参考。同时把同列后续 widget 的 y 坐标向上平移补齐。
--
-- 影响范围：仅修改两个内置预设的 widgets jsonb 字段，对用户自建
-- dashboard 实例 (sys_dashboard) 不影响。
-- ============================================================

-- 1. 个人工作台：4 个 counter (y:3) h:3→h:2；底部两个 echarts y:6→y:5
UPDATE sys_dashboard_preset
SET widgets = '[
  {"id":"personal-shortcuts","type":"shortcut","title":"快捷入口","position":{"x":0,"y":0,"w":12,"h":3},"config":{"type":"shortcut","items":[{"label":"AI 创作","href":"/aigc","icon":"sparkles"},{"label":"素材库","href":"/aigc/assets","icon":"image"},{"label":"知识库","href":"/knowledge","icon":"database"},{"label":"设置","href":"/settings","icon":"settings"}]}},
  {"id":"personal-credits","type":"counter","title":"积分余额","position":{"x":0,"y":3,"w":3,"h":2},"config":{"type":"counter","entity":"@total_credit","aggregation":"count","icon":"credit-card","color":"yellow"}},
  {"id":"personal-assets","type":"counter","title":"我的素材","position":{"x":3,"y":3,"w":3,"h":2},"config":{"type":"counter","entity":"media_asset","aggregation":"count","icon":"image","color":"purple"}},
  {"id":"personal-aigc-tasks","type":"counter","title":"生成任务","position":{"x":6,"y":3,"w":3,"h":2},"config":{"type":"counter","entity":"aigc_task","aggregation":"count","icon":"wand-2","color":"blue"}},
  {"id":"personal-knowledge","type":"counter","title":"知识库数量","position":{"x":9,"y":3,"w":3,"h":2},"config":{"type":"counter","entity":"ai_knowledge_base","aggregation":"count","icon":"database","color":"green"}},
  {"id":"personal-task-trend","type":"echarts","title":"生成任务趋势","position":{"x":0,"y":5,"w":8,"h":4},"config":{"type":"echarts","statsType":"trend","chartType":"bar","metric":"aigc_task","period":"day"}},
  {"id":"personal-credit-trend","type":"echarts","title":"积分消耗趋势","position":{"x":8,"y":5,"w":4,"h":4},"config":{"type":"echarts","statsType":"trend","chartType":"line","metric":"credit_cost","period":"day"}}
]'::jsonb
WHERE preset_key = 'personal';

-- 2. 运营总览：8 个 counter h:3→h:2；第二行 y:3→y:2；底部 echarts y:6→y:4
UPDATE sys_dashboard_preset
SET widgets = '[
  {"id":"admin-user-count","type":"counter","title":"注册用户","position":{"x":0,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@user_count","aggregation":"count","icon":"users","color":"blue"}},
  {"id":"admin-paid-member","type":"counter","title":"付费会员","position":{"x":3,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@paid_member","aggregation":"count","icon":"badge-check","color":"yellow"}},
  {"id":"admin-order-count","type":"counter","title":"订单数","position":{"x":6,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@order_count","aggregation":"count","icon":"receipt","color":"green"}},
  {"id":"admin-order-amount","type":"counter","title":"订单总额（分）","position":{"x":9,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@order_amount","aggregation":"sum","icon":"credit-card","color":"purple"}},
  {"id":"admin-total-credit","type":"counter","title":"积分总量","position":{"x":0,"y":2,"w":3,"h":2},"config":{"type":"counter","entity":"@total_credit","aggregation":"sum","icon":"credit-card","color":"orange"}},
  {"id":"admin-spent-credit","type":"counter","title":"已消耗积分","position":{"x":3,"y":2,"w":3,"h":2},"config":{"type":"counter","entity":"@spent_credit","aggregation":"sum","icon":"credit-card","color":"red"}},
  {"id":"admin-aigc-task","type":"counter","title":"AIGC 任务数","position":{"x":6,"y":2,"w":3,"h":2},"config":{"type":"counter","entity":"aigc_task","aggregation":"count","icon":"wand-2","color":"blue"}},
  {"id":"admin-kb-count","type":"counter","title":"知识库数量","position":{"x":9,"y":2,"w":3,"h":2},"config":{"type":"counter","entity":"ai_knowledge_base","aggregation":"count","icon":"database","color":"green"}},
  {"id":"admin-dau-trend","type":"echarts","title":"DAU 趋势","position":{"x":0,"y":4,"w":6,"h":4},"config":{"type":"echarts","statsType":"trend","chartType":"line","metric":"dau","period":"day"}},
  {"id":"admin-revenue-trend","type":"echarts","title":"收入趋势","position":{"x":6,"y":4,"w":6,"h":4},"config":{"type":"echarts","statsType":"trend","chartType":"bar","metric":"revenue","period":"day"}}
]'::jsonb
WHERE preset_key = 'admin';

-- 3. 分销仪表盘：4 个 counter h:3→h:2；下方 echarts y 上移
UPDATE sys_dashboard_preset
SET widgets = '[
  {"id":"bkr-total-brokers","type":"counter","title":"分销员总数","position":{"x":0,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@brokerage_broker_count","aggregation":"count","icon":"users","color":"blue"}},
  {"id":"bkr-month-amount","type":"counter","title":"本月佣金发放（分）","position":{"x":3,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@brokerage_month_amount","aggregation":"sum","icon":"percent","color":"green"}},
  {"id":"bkr-pending-withdraw","type":"counter","title":"待审核提现","position":{"x":6,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@brokerage_pending_withdraw","aggregation":"count","icon":"banknote","color":"orange"}},
  {"id":"bkr-invite-binds","type":"counter","title":"邀请绑定总次数","position":{"x":9,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@brokerage_invite_binds","aggregation":"count","icon":"link","color":"purple"}},
  {"id":"bkr-amount-trend","type":"echarts","title":"佣金发放趋势（按业务类型）","position":{"x":0,"y":2,"w":8,"h":4},"config":{"type":"echarts","statsType":"trend","chartType":"bar","metric":"brokerage_amount","period":"day","stacked":true}},
  {"id":"bkr-broker-trend","type":"echarts","title":"新增分销员趋势","position":{"x":8,"y":2,"w":4,"h":4},"config":{"type":"echarts","statsType":"trend","chartType":"line","metric":"brokerage_new_broker","period":"day"}},
  {"id":"bkr-status-pie","type":"echarts","title":"佣金流水状态分布","position":{"x":0,"y":6,"w":4,"h":4},"config":{"type":"echarts","statsType":"distribution","chartType":"pie","metric":"brokerage_record_status"}},
  {"id":"bkr-biz-pie","type":"echarts","title":"佣金来源构成","position":{"x":4,"y":6,"w":4,"h":4},"config":{"type":"echarts","statsType":"distribution","chartType":"pie","metric":"brokerage_biz_type"}},
  {"id":"bkr-withdraw-pie","type":"echarts","title":"提现状态分布","position":{"x":8,"y":6,"w":4,"h":4},"config":{"type":"echarts","statsType":"distribution","chartType":"pie","metric":"brokerage_withdraw_status"}}
]'::jsonb
WHERE preset_key = 'brokerage';

-- 4. 营销看板：6 个 counter h:3→h:2；第二行 counter y:3→y:2；channel-dist y:3→y:2；底部 chart/list y:7→y:6
UPDATE sys_dashboard_preset
SET widgets = '[
  {"id":"mkt-total","type":"counter","title":"线索总数","position":{"x":0,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"ops_guest_lead","aggregation":"count","icon":"users","color":"blue"}},
  {"id":"mkt-visit","type":"counter","title":"访客访问","position":{"x":3,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@lead_visit","aggregation":"count","icon":"globe","color":"cyan"}},
  {"id":"mkt-chat","type":"counter","title":"对话意向","position":{"x":6,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@lead_chat","aggregation":"count","icon":"message-circle","color":"green"}},
  {"id":"mkt-newsletter","type":"counter","title":"邮箱订阅","position":{"x":9,"y":0,"w":3,"h":2},"config":{"type":"counter","entity":"@lead_newsletter","aggregation":"count","icon":"mail","color":"yellow"}},
  {"id":"mkt-contact","type":"counter","title":"联系留言","position":{"x":0,"y":2,"w":3,"h":2},"config":{"type":"counter","entity":"@lead_contact","aggregation":"count","icon":"phone","color":"purple"}},
  {"id":"mkt-feedback","type":"counter","title":"用户反馈","position":{"x":3,"y":2,"w":3,"h":2},"config":{"type":"counter","entity":"@lead_feedback","aggregation":"count","icon":"message-square","color":"orange"}},
  {"id":"mkt-channel-dist","type":"chart","title":"渠道分布","position":{"x":6,"y":2,"w":6,"h":4},"config":{"type":"chart","entity":"ops_guest_lead","xField":"channel","yField":"id"}},
  {"id":"mkt-status-dist","type":"chart","title":"处理状态分布","position":{"x":0,"y":6,"w":6,"h":4},"config":{"type":"chart","entity":"ops_guest_lead","xField":"status","yField":"id"}},
  {"id":"mkt-recent-leads","type":"list","title":"最近线索（10 条）","position":{"x":6,"y":6,"w":6,"h":4},"config":{"type":"list","entity":"ops_guest_lead","columns":["id","channel","email","region","create_time"],"limit":10}}
]'::jsonb
WHERE preset_key = 'marketing';
