# 开发记录：AAF-115 Task 执行与持久编排

## 缺陷记录

- 🐞 TASK 路径提交后 SSE 立即闭合 — AAF-115 把 TASK 改为"提交 Task 后由独立 worker 执行"，入口提交后返回空 Flux，worker 产出的增量没有送到连接上；同时协调者交付契约矛盾、`MESSAGE_DELTA` 每个增量落库造成写放大。修复见 [AAF-117 tasks](../AAF-117/tasks.md) #11701 至 #11705，设计依据 [ADR-008](../../../design/adr/ADR-008-task-execution-shape-and-streaming.md)（2026-10-12）
