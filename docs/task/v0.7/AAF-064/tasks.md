---
level: Practice
layer: Product
purpose: AAF-064 代码生成的技术任务清单
status: done
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 代码生成（AAF-064）

> 负责人：architect + developer-service | 创建：05-19

## 任务列表

### 模板生成

1. [x] #6401 EntityDef→后端 CRUD
   - 基于实体定义生成 Entity/Repository/Service/Controller 四层代码
   - FreeMarker 模板管理（模板 CRUD、变量注入、条件渲染）
   - 字段类型→Java 类型映射（含关联关系 o2m/m2m）
   - 生成代码符合 aaf 编码规范（包路径、命名、注解）
   - verify: 生成的 CRUD 代码编译通过，API 可正常调用

2. [x] #6402 AI 辅助代码生成
   - LLM 驱动的智能代码生成（基于需求描述 + 上下文生成业务逻辑）
   - 上下文感知（自动加载相关实体定义、接口签名、已有代码）
   - 代码补全与重构建议（识别重复代码、建议抽象）
   - 生成结果人工确认（diff 预览→确认→写入）
   - verify: AI 生成代码质量可用，人工确认流程正常

### 迁移与校验

3. [x] #6403 迁移脚本生成
   - EntityDef 变更检测（新增/修改/删除字段、类型变更）
   - 自动生成 Flyway 迁移脚本（DDL + 数据迁移）
   - AI 控制复杂映射（字段重命名、类型转换、数据填充策略）
   - 开发模式自动执行迁移（dev profile 下变更即生效）
   - verify: 实体变更后迁移脚本正确生成并执行

4. [x] #6404 代码质量校验
   - 生成代码自动 lint（Checkstyle/PMD 规则）+ 编译检查
   - 规范一致性验证（对照 coding-style-standard 检查）
   - 测试覆盖率检查（生成代码是否有对应单测）
   - 校验不通过→自动修复或标记人工处理
   - verify: 生成代码通过全部质量检查，不通过时正确标记

### 版本管理

5. [x] #6405 生成历史与回滚
   - 代码生成记录（时间、触发源、生成文件列表、diff）
   - 版本对比（任意两次生成结果 diff）
   - 一键回滚（恢复到指定生成版本）
   - 生成模板版本管理（模板变更不影响已生成代码）
   - verify: 回滚后代码恢复正确，编译通过
