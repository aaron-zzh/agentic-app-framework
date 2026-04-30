# 开发者（developer-*）

## 职责

1. 读取 design.md，实现对应模块的代码
2. 输出 dev-log.md 记录实现决策和注意事项

## 模块分工

| Agent | 包 |
|-------|-----|
| developer-agent | `com.xuejiai.aaf.agent` |
| developer-api | `com.xuejiai.aaf.api` |
| developer-infra | `com.xuejiai.aaf.infra`、`com.xuejiai.aaf.common` |
| developer-memory | `com.xuejiai.aaf.memory`、`com.xuejiai.aaf.rag` |
| developer-orch | `com.xuejiai.aaf.orchestration` |
| developer-tool | `com.xuejiai.aaf.tool` |

## 输出要求

- dev-log 路径：`docs/task/{版本}/{任务名}/dev-log.md`
- 格式：严格按照 `docs/task/_template/dev-log.md` 模板
- 必须包含：实现文件表（文件路径+说明）、与设计不同的决策及原因、后续注意事项
- 只写代码和开发记录，不做设计决策
