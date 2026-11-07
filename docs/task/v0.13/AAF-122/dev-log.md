# 开发记录：AAF-122 知识图谱领域本体与实例双层抽取

执行者：AI/developer-service（协调者复核）

## #12201 精确别名匹配加类型一致条件

✅ 2026-11-07 — developer-service

- 精确匹配与候选召回均按规范化类型过滤
- 空类型以写入值 Concept 参与匹配
- 同文档同名异类型解析为两实体
- 大模型消歧候选限同类型，杜绝误 LINK

## #12202 LITERAL 只允许短值

✅ 2026-11-07 — developer-service

- LITERAL 限 32 字符，禁句子分隔符
- 违规者逐条丢弃，同块其他事实不受影响
- 提示词补规则与整句反例
- 契约升 knowledge-fact-schema-v6，计入 fingerprint

> 未编译、未运行测试与 check（按人类 11-07 指示）。合入前需补跑 `pnpm nx test service`。

## #12220 图谱检索降噪

✅ 2026-11-07 — 协调者

- 直接命中删 predicate 条件，只按实体名
- 实体名不足 2 字不参与命中
- 截断前按双端命中、predicate、名长排序
- 邻居扩展按置信度降序截断

## #12221 predicate 规范化提示词

✅ 2026-11-07 — 协调者

- predicate 限 2-6 字规范动词或属性名
- 情态词移入 attributes.modality
- 禁描述性短语，附正反例
- 契约升 knowledge-fact-schema-v7，计入 fingerprint

> 未编译、未运行测试与 check（按人类 11-07 指示）。本体设计 design.md 中的 `knowledge-fact-schema-v7` 需顺延为 v8。

## 实现文件

| 文件 | 说明 |
|------|------|
| `aaf-framework/.../trusted/TrustedKnowledgeStore.java` | `findExactEntities`、`findEntityCandidates` 新增 `normalizedType` 参数与类型条件 |
| `aaf-framework/.../trusted/IncrementalEntityResolver.java` | 传入 `MentionKey.normalizedType()`；`entityType()` 统一空类型回退 |
| `aaf-framework/.../graph/EntityExtractionPrompt.java` | `MAX_LITERAL_LENGTH`、`LITERAL_SENTENCE_DELIMITERS`、指令与契约版本 v6 |
| `aaf-framework/.../graph/EntityExtractionService.java` | `validateLiteralValue` |
| `IncrementalEntityResolverTest.java`、`EntityExtractionServiceTest.java` | 更新签名并覆盖 AC-17 至 AC-20 |
| `aaf-framework/.../trusted/KnowledgeGraphProjectionService.java` | `searchFactKeys` 直接命中与邻居扩展的 Cypher（#12220） |
| `KnowledgeGraphProjectionServiceTest.java` | 同步 Cypher 断言（#12220） |
| `aaf-framework/.../graph/EntityExtractionPrompt.java` | predicate 规则与契约版本 v7（#12221） |

## 实现决策

- 同名不同类型不再合并，抽取器对同一事物给出不同类型时会产生两个实体；本体回填（后续任务）负责归类后合并。
- SQL 侧类型规范化用 `lower(btrim(regexp_replace(...)))`，与 Java `normalize()` 对齐，无需改表。

## 注意事项

- 抽取契约升级后，已入库文档的 fingerprint 变化，重新入库会走新 run。
