# AAF-124 开发记录

## #12401 执行轨迹摘要日志、失败堆栈与学习尽力而为

✅ 2026-11-08 — developer-service

- 新增 ExecutionTraceRecorder，执行结束打印单块摘要
- 开关 aaf.ai.execution-trace.enabled，默认开
- 失败收口与流异常改 WARN，附带堆栈
- learn 抽为尽力而为方法，失败不改结果
- 未跑测试，需协调者补跑 test 与 check

> **注意**：按用户要求本次只改代码，未执行 lint / test / check / build。协调者需补跑 `pnpm nx test service` 与 `pnpm check:affected`。改动 5 个文件（含 1 个新增源码、1 个新增单测），原因见汇报。
