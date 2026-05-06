# 待实现

| Skill | 借鉴来源 | 角色 | 用途 | 优先级 |
|-------|----------|------|------|--------|
| `design-consultation` | gstack `/design-consultation` | designer | 从零构建设计系统，产出 `DESIGN.md` 作为视觉规范真理源 | v0.2（webui 有实际页面后） |
| `design-review` | gstack `/design-review` | designer / tester | 实现后视觉审计，80 项检查 + 原子提交修复 | v0.2（webui 有实际页面后） |
| `coverage-audit` | gstack `/ship` coverage | developer | PR 前测试覆盖审计，检测 diff 中无对应测试的 public 方法 | v0.1 后期 |
| `sprint-orchestration` | gstack 流程理念 | 协调者 | 三级派发模板（Think→Plan→Build→Review→Test→Ship→Reflect） | v0.2 |
| `scope-review` | gstack `/plan-ceo-review` | product | 需求 scope 四模式（Expansion/Selective/Hold/Reduction） | v0.2 |
| `cross-model-review` | gstack `/codex` | architect | 跨模型第二意见审查，🔴 高风险变更强制不同 LLM 对抗性审查 | v0.2 |
| `browse` | gstack `/browse` | tester | 浏览器自动化验证，给 agent 实时视觉能力 | 依赖 Playwright E2E（AAF-023 #6） |
