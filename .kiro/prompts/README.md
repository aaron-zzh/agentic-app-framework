# 可复用提示词

管理项目中可复用的提示词模板，供 agent 配置引用或人类直接使用。

## 使用方式

agent 配置中通过 `file://` 引用：
```json
{ "prompt": "file://.kiro/prompts/code-review.md" }
```

或在对话中直接复制使用。

## 提示词列表

| 文件 | 用途 |
|------|------|
| `requirement-analysis.md` | 需求分析，输出用户故事和验收标准 |
| `technical-design.md` | 技术设计，输出接口定义和类结构 |
| `code-review.md` | 代码审查，输出评审报告和问题列表 |
| `test-writing.md` | 测试编写，输出单元测试和集成测试 |
