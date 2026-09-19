---
level: Practice
layer: Product
purpose: ModBot 产品与技术设计索引
status: published
version: 1.3.0
date: 2026-09-17
author: Kiro
changelog:
  - 2026-09-17 | v1.3.0 新增视觉感知融合设计，数字人第二节点升级为多模态驱动融合引擎
  - 2026-09-17 | v1.2.0 新增数字人语义计划与驱动转换设计
  - 2026-09-09 | v1.1.0 通信设计升级为整体协议规划基线
scope:
  includes:
    - ModBot 产品概念
    - ModBot 与 AAF 的机器人端通信设计
    - ModBot 数字人语义计划与多模态驱动融合设计
    - 开发板目标检测与视觉大模型协同的视觉感知融合设计
gains:
  - 能找到 ModBot 的产品定位、通信、数字人驱动与视觉感知方案
---

# ModBot

> 模块化桌面机器人产品及其接入 AAF 助理的技术设计。

## 文档列表

1. [产品概念](./product-concept.md) — 模块化桌面机器人的产品理念、模块和应用场景
2. [Qt 机器人通信设计](./qt-robot-communication.md) — 已采纳、尚未实现的整体协议矩阵与安全通信基线
3. [数字人语义计划与多模态驱动融合设计](./avatar-performance-driving.md) — 大模型生成表演语义计划，融合 TTS、视觉状态和资产生成多通道时序驱动
4. [视觉感知融合设计](./visual-perception-fusion.md) — 开发板高频目标检测与视觉大模型按需语义理解的 SceneState 融合方案
