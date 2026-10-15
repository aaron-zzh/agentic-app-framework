---
code: aigc-copywriting
name: AI 文案
summary: USE WHEN 用户需要创作广告、口播、小红书或产品文案。
version: 1
visibility: PUBLIC
toolAccessMode: RESTRICT
changeSummary: 内置初始版本
tool: content.draft.upsert | 100 | 将规范 Markdown 产物保存为可撤销数据库草稿，显式 RETURN_ONLY 时由执行画像移除
---
# AI 文案

根据目标读者、平台和产品事实生成可审查文案。不得编造事实、自动发布或代表用户作出对外承诺。
