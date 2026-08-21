# 设计：AAF-100 User Studio v0.1 MVP（接口契约 + 表结构）

> 上游：[user-studio-mvp.md v0.2.0](../../../design/apps/webui/user-studio-mvp.md)
> 任务：tasks.md（待创建）| 开发记录：dev-log.md（待创建）
> 创建：2026-06-22 | 风险等级：🔴 高（5 张新表 + 数据隔离横切）

## 设计原则

- **接口契约先行**：本文档是前后端并行开发的合同。后端按此实现 Controller/Service，前端按此写 hook，零 mock 直连。
- **复用为主**：后端继承 `BaseCrudController` + `BaseCrudService`，复用现有 CRUD 模板。
- **数据隔离硬约束**：所有 user-facing endpoint 必须按 `SecurityContext.userId` 过滤，写接口校验 ownership。
- **统一返回**：`Result<T>` 包装；分页用 `PageResult<T>`；错误用 GlobalExceptionHandler 标准化。
- **路径前缀**：业务接口统一 `/api/{module}/...`；工具类接口 `/api/tools/...`。

## 接口契约

### BE-1 项目类型枚举扩展（仅 DDL）

`aigc_project.type` 当前枚举：`VIDEO_DRAMA / IMAGE_POST / SHORT_VIDEO / MIXED / MUSIC / VOICE / MODEL_3D`。

**追加**：`LIFE / STUDY / WORK / CONTENT_OPS`（非 AIGC 通用项目类型，D5 决策）。

无新接口，仅 DDL（不动表结构，仅扩枚举注释）。前端 `TYPE_LABELS` 映射加 4 项即可。

### BE-2 项目模板 `/api/aigc/project-templates`

#### 表结构 `user_project_template`

```sql
CREATE TABLE user_project_template (
  id            BIGSERIAL PRIMARY KEY,
  code          VARCHAR(100) NOT NULL UNIQUE,         -- 模板唯一编码（用于种子和检索）
  name          VARCHAR(200) NOT NULL,
  description   VARCHAR(1000),
  cover_url     VARCHAR(1000),
  category      VARCHAR(50) NOT NULL,                 -- LIFE/STUDY/WORK/CONTENT_OPS/AIGC
  project_type  VARCHAR(30) NOT NULL,                 -- 对应 aigc_project.type
  template_config JSONB NOT NULL DEFAULT '{}'::jsonb, -- 配置：{prompt, defaultPersonaId, defaultKbIds[], workflowKey, recommendedAssetGroupIds[]}
  is_official   BOOLEAN NOT NULL DEFAULT FALSE,       -- v0.1 仅官方=TRUE，不开放用户自建
  usage_count   INTEGER NOT NULL DEFAULT 0,
  user_id       BIGINT,                                -- 官方模板=NULL；用户自建=user_id（v0.2 启用）
  sort_order    INTEGER NOT NULL DEFAULT 0,
  -- BaseEntity 字段
  create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_time   TIMESTAMP,
  deleted       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_upt_category ON user_project_template(category) WHERE deleted = FALSE;
CREATE INDEX idx_upt_official ON user_project_template(is_official) WHERE deleted = FALSE;
```

#### 接口

| Method | Path | 描述 | 鉴权 |
|--------|------|------|------|
| GET | `/api/aigc/project-templates` | 分页查询模板（支持 category/isOfficial 筛选）| `isAuthenticated()` |
| GET | `/api/aigc/project-templates/{id}` | 模板详情 | `isAuthenticated()` |
| POST | `/api/aigc/project-templates/{id}/fork` | **核心**：基于模板 fork 创建新项目，返回新项目 ID | `isAuthenticated()` |

#### DTO

```java
// UserProjectTemplateVO
class UserProjectTemplateVO {
  Long id;
  String code;
  String name;
  String description;
  String coverUrl;
  String category;        // LIFE/STUDY/WORK/CONTENT_OPS/AIGC
  String projectType;     // 对应 aigc_project.type
  Map<String, Object> templateConfig;
  Boolean isOfficial;
  Integer usageCount;
  String createTime;
  String updateTime;
}

// UserProjectTemplatePageDTO
class UserProjectTemplatePageDTO extends BasePageDTO {
  String category;        // 可选筛选
  Boolean isOfficial;     // 可选筛选
  String keyword;         // 名称模糊搜索
}

// UserProjectTemplateForkDTO
class UserProjectTemplateForkDTO {
  String name;            // 必填，用户自定义新项目名（默认=模板名+时间戳）
  String description;     // 可选
}
```

#### Fork 逻辑

```text
1. 校验模板存在且未删除
2. usage_count++（异步原子更新）
3. 创建 aigc_project：
   - userId = SecurityContext.userId
   - name/description = DTO 或模板默认
   - type = template.projectType
   - prompt = templateConfig.prompt
   - status = DRAFT
4. 若 templateConfig 含 defaultKbIds / defaultPersonaId / workflowKey，
   依赖 BE-3 写 user_project_resource 关联（同事务）
5. 返回 AigcProjectVO（新项目）
```

#### 种子数据（5 个官方模板）

```sql
INSERT INTO user_project_template (code, name, category, project_type, template_config, is_official, sort_order)
VALUES
  ('xhs-redbook', '小红书爆款', 'CONTENT_OPS', 'IMAGE_POST',
   '{"prompt":"小红书爆款笔记，标题+正文+标签，符合平台算法","defaultPersonaId":null}', TRUE, 1),
  ('voiceover-30s', '30 秒口播视频', 'CONTENT_OPS', 'SHORT_VIDEO',
   '{"prompt":"30 秒短视频口播脚本，含 3 秒钩子","defaultPersonaId":null}', TRUE, 2),
  ('viral-copy', '爆款复刻', 'CONTENT_OPS', 'IMAGE_POST',
   '{"prompt":"参考爆款进行复刻创作"}', TRUE, 3),
  ('ip-builder', '个人 IP 打造', 'WORK', 'MIXED',
   '{"prompt":"围绕个人 IP 进行内容生产"}', TRUE, 4),
  ('study-notes', '学习笔记', 'STUDY', 'MIXED',
   '{"prompt":"学习笔记整理与思维导图"}', TRUE, 5);
```

### BE-3 项目-资源关联 `/api/aigc/projects/{id}/resources`

#### 表结构 `user_project_resource`

```sql
CREATE TABLE user_project_resource (
  id            BIGSERIAL PRIMARY KEY,
  project_id    BIGINT NOT NULL,
  resource_type VARCHAR(20) NOT NULL,    -- ASSISTANT/KNOWLEDGE_BASE/WORKFLOW/ASSET_GROUP/SKILL
  resource_id   BIGINT NOT NULL,
  role          VARCHAR(20),             -- DEFAULT_ASSISTANT/REF/TARGET/...
  sort_order    INTEGER NOT NULL DEFAULT 0,
  -- BaseEntity
  create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_time   TIMESTAMP,
  deleted       BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (project_id, resource_type, resource_id) -- 防重复
);
CREATE INDEX idx_upr_project ON user_project_resource(project_id) WHERE deleted = FALSE;
```

#### 接口

| Method | Path | 描述 | 鉴权 |
|--------|------|------|------|
| GET | `/api/aigc/projects/{projectId}/resources` | 列项目的所有资源（按 type 分组） | `isAuthenticated()` + 校验 project ownership |
| POST | `/api/aigc/projects/{projectId}/resources` | 关联资源（body 含 resourceType/resourceId/role） | 同上 |
| DELETE | `/api/aigc/projects/{projectId}/resources/{id}` | 解除关联 | 同上 |

#### DTO

```java
class UserProjectResourceVO {
  Long id;
  Long projectId;
  String resourceType;   // ASSISTANT/KNOWLEDGE_BASE/WORKFLOW/ASSET_GROUP/SKILL
  Long resourceId;
  String role;
  Integer sortOrder;
  // 联表查询时回填，便于前端展示
  String resourceName;
  String resourceCoverUrl;
  String createTime;
}

class UserProjectResourceLinkDTO {
  String resourceType;   // 必填
  Long resourceId;       // 必填
  String role;           // 可选
  Integer sortOrder;     // 可选，默认 0
}
```

### BE-4 助理装扮 `/api/avatar-outfits` + `/api/user-avatar-inventory`

简化策略：v0.1 仅 `AVATAR / OUTFIT` 两类（头像 + 服饰），不实现 `COCKPIT_THEME / ACCESSORY`。

#### 表结构 `avatar_outfit` + `user_avatar_inventory`

```sql
CREATE TABLE avatar_outfit (
  id            BIGSERIAL PRIMARY KEY,
  code          VARCHAR(100) NOT NULL UNIQUE,
  name          VARCHAR(200) NOT NULL,
  type          VARCHAR(20) NOT NULL,            -- AVATAR/OUTFIT（v0.1 仅此两类）
  asset_url     VARCHAR(1000) NOT NULL,          -- 资源图片 URL
  thumbnail_url VARCHAR(1000),                   -- 缩略图 URL
  rarity        VARCHAR(20) DEFAULT 'COMMON',    -- COMMON/RARE/EPIC/LEGENDARY
  unlock_condition VARCHAR(100),                 -- DEFAULT/PURCHASE/REWARD/REDEEM/VIP
  entitlement_code VARCHAR(50),                  -- VIP 限定时的权益编码
  price         BIGINT,                           -- 商城价（积分），0 或 NULL = 不出售
  sort_order    INTEGER NOT NULL DEFAULT 0,
  create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_time   TIMESTAMP,
  deleted       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_outfit_type ON avatar_outfit(type) WHERE deleted = FALSE;

CREATE TABLE user_avatar_inventory (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  persona_id    BIGINT,                           -- 哪个助理的装扮，NULL = 全局默认
  outfit_id     BIGINT NOT NULL,
  obtained_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  obtained_source VARCHAR(20) NOT NULL,           -- DEFAULT/PURCHASE/REWARD/REDEEM
  equipped      BOOLEAN NOT NULL DEFAULT FALSE,
  -- BaseEntity
  create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_time   TIMESTAMP,
  deleted       BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (user_id, persona_id, outfit_id)
);
CREATE INDEX idx_inv_user ON user_avatar_inventory(user_id) WHERE deleted = FALSE;
```

#### 接口

| Method | Path | 描述 | 鉴权 |
|--------|------|------|------|
| GET | `/api/avatar-outfits` | 装扮商城列表（分页 + type 筛选） | `isAuthenticated()` |
| GET | `/api/avatar-outfits/{id}` | 装扮详情 | `isAuthenticated()` |
| POST | `/api/avatar-outfits/{id}/purchase` | 购买装扮（扣积分 + 写库存） | `isAuthenticated()` |
| GET | `/api/user-avatar-inventory/me` | 我的库存（按 type 分组） | `isAuthenticated()`，强制 user_id |
| POST | `/api/user-avatar-inventory/equip` | 装备某装扮 body=`{outfitId, personaId?}` | 同上 |
| POST | `/api/user-avatar-inventory/unequip` | 卸下装备 body=`{outfitId, personaId?}` | 同上 |

#### DTO

```java
class AvatarOutfitVO {
  Long id;
  String code;
  String name;
  String type;
  String assetUrl;
  String thumbnailUrl;
  String rarity;
  String unlockCondition;
  String entitlementCode;
  Long price;
  Integer sortOrder;
  Boolean owned;          // 当前用户是否拥有（联表回填）
  Boolean equipped;       // 当前用户是否装备
}

class UserAvatarInventoryVO {
  Long id;
  Long outfitId;
  Long personaId;         // null = 全局
  String obtainedAt;
  String obtainedSource;
  Boolean equipped;
  AvatarOutfitVO outfit;  // 联表
}

class EquipDTO {
  Long outfitId;          // 必填
  Long personaId;         // 可选
}
```

#### 种子数据（10 个 starter pack：5 头像 + 5 服饰）

```sql
INSERT INTO avatar_outfit (code, name, type, asset_url, rarity, unlock_condition, sort_order)
VALUES
  ('avatar-default-girl', '默认少女', 'AVATAR', '/assets/outfits/avatar-girl.png', 'COMMON', 'DEFAULT', 1),
  ('avatar-default-boy', '默认少年', 'AVATAR', '/assets/outfits/avatar-boy.png', 'COMMON', 'DEFAULT', 2),
  ('avatar-cyber', '赛博女孩', 'AVATAR', '/assets/outfits/avatar-cyber.png', 'RARE', 'PURCHASE', 3),
  ('avatar-tech', '科技工程师', 'AVATAR', '/assets/outfits/avatar-tech.png', 'RARE', 'PURCHASE', 4),
  ('avatar-magic', '魔法师', 'AVATAR', '/assets/outfits/avatar-magic.png', 'EPIC', 'VIP', 5),
  ('outfit-tshirt', '基础 T 恤', 'OUTFIT', '/assets/outfits/outfit-tshirt.png', 'COMMON', 'DEFAULT', 11),
  ('outfit-suit', '商务套装', 'OUTFIT', '/assets/outfits/outfit-suit.png', 'COMMON', 'DEFAULT', 12),
  ('outfit-hoodie', '潮酷卫衣', 'OUTFIT', '/assets/outfits/outfit-hoodie.png', 'RARE', 'PURCHASE', 13),
  ('outfit-yukata', '夏日浴衣', 'OUTFIT', '/assets/outfits/outfit-yukata.png', 'RARE', 'PURCHASE', 14),
  ('outfit-armor', '太空战甲', 'OUTFIT', '/assets/outfits/outfit-armor.png', 'LEGENDARY', 'VIP', 15);

-- 默认装扮：所有用户注册时自动 INSERT 进 user_avatar_inventory（默认+赠送）
-- 这一步在 UserService.register 触发器里加，或者通过事件监听
```

### BE-5 收藏夹 `/api/user-favorites`

#### 表结构 `user_favorite`

```sql
CREATE TABLE user_favorite (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  target_type   VARCHAR(20) NOT NULL,    -- DOC/ASSET/CONVERSATION/PROMPT/PROJECT
  target_id     BIGINT NOT NULL,
  note          VARCHAR(500),             -- 收藏备注
  sort_order    INTEGER NOT NULL DEFAULT 0,
  create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_time   TIMESTAMP,
  deleted       BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (user_id, target_type, target_id)
);
CREATE INDEX idx_fav_user_type ON user_favorite(user_id, target_type) WHERE deleted = FALSE;
```

#### 接口

| Method | Path | 描述 | 鉴权 |
|--------|------|------|------|
| GET | `/api/user-favorites` | 我的收藏（分页 + targetType 筛选）| `isAuthenticated()` |
| POST | `/api/user-favorites` | 添加收藏 body=`{targetType, targetId, note?}` | 同上 |
| DELETE | `/api/user-favorites/{id}` | 删除收藏 | 同上，校验 ownership |
| DELETE | `/api/user-favorites/by-target?targetType=&targetId=` | 按目标删除（toggle 用） | 同上 |

#### DTO

```java
class UserFavoriteVO {
  Long id;
  String targetType;
  Long targetId;
  String note;
  Integer sortOrder;
  String createTime;
  // 联表展示字段（按 targetType 拼装）
  String targetTitle;
  String targetCoverUrl;
}

class UserFavoriteCreateDTO {
  String targetType;       // 必填，枚举校验
  Long targetId;           // 必填
  String note;             // 可选
}
```

### BE-6 工具-天气 `/api/tools/weather`

外部 API 代理 + Redis 缓存（key: `tools:weather:{city}`，TTL=30 分钟）。

| Method | Path | 描述 | 鉴权 |
|--------|------|------|------|
| GET | `/api/tools/weather?city=...` | 查城市天气 | `isAuthenticated()` |

```java
class WeatherVO {
  String city;
  String description;       // "晴" / "多云" / ...
  Integer temperature;      // 摄氏度
  Integer humidity;         // 百分比
  String windDirection;
  Integer windSpeed;
  String forecast3Days;     // 简短描述
  String dataSource;        // "openweathermap" / "amap" / mock
  String updatedAt;
}
```

集成方式：v0.1 用 OpenWeatherMap（开放 API）或 mock JSON（无 key 时降级）。

### BE-7 个人提示词模板 `/api/ai/prompts`

复用现有 `GenerationTemplate` 表 + Controller。**仅前端封装个人视图**，后端给一个 `/me` 入口（强制 user_id 过滤）。

| Method | Path | 描述 | 鉴权 |
|--------|------|------|------|
| GET | `/api/ai/prompts/me` | 我的提示词模板（分页 + tag 筛选）| `isAuthenticated()` |
| POST | `/api/ai/prompts` | 新建（自动写 user_id）| `isAuthenticated()` |
| PUT | `/api/ai/prompts/{id}` | 更新（校验 ownership）| 同上 |
| DELETE | `/api/ai/prompts/{id}` | 删除 | 同上 |
| POST | `/api/ai/prompts/{id}/use` | 使用（usage_count++ + 跳到 image composer）| 同上，前端用 |

DTO 复用 `GenerationTemplateVO/CreateDTO/UpdateDTO`。

### BE-8 数据隔离审计

**横切任务**：审计所有 user-facing controller，确保 GET/PUT/DELETE 按 user_id 过滤，写入校验 ownership。

#### 审计清单（按优先级）

| 模块 | 接口 | 审计点 | 优先级 |
|------|------|------|------|
| `module/ai/aigc/project/*` | `/aigc/projects/*` | 列表 user_id 过滤 + 写入 ownership 校验 | P0 |
| `module/ai/aigc/media/*` | `/aigc/assets/*` | 同上 | P0 |
| `module/ai/aigc/history/*` | `/aigc/history` | 列表 user_id 过滤 | P0 |
| `module/ai/aigc/task/*` | `/aigc/tasks` | 列表 user_id 过滤 | P0 |
| `module/ai/aigc/image/*` | `/system/images/draw` | 异步任务关联 user_id | P0 |
| `module/ai/aigc/voice/*` | `/aigc/voice/*` | 同上 | P1 |
| `module/ai/aigc/avatar/*` | `/aigc/avatar/*` | 同上 | P1 |
| `module/knowledge/*` | `/docs/*` `/knowledge-bases/*` | 列表 user_id 过滤 | P0 |
| `module/system/user/*` | `/profile/*` `/notifications/*` | 自动 user_id（已有） | P1（验证） |

**修复策略**：

1. Service 层从 `SecurityContextHolder` 取 currentUserId
2. Repository 用 Specification/QueryDSL 强制注入 `userId = :currentUserId`
3. 单条查询（findById）查到后比对 `entity.userId == currentUserId`，不等抛 `ResourceNotFoundException`（不暴露 403 → 降到 404 防探测）
4. 写入接口同样比对，不等抛 `ResourceNotFoundException`
5. 跨用户操作必须显式 `@AdminOnly` 注解

产出：`docs/task/v0.1.1/AAF-100/audit-data-isolation.md`，逐接口列出"已隔离 / 已修 / 待修"。

### BE-9 文案智能体技能种子

`ai_skill_definition` 表已存在。补 7 个文案智能体：

```sql
INSERT INTO ai_skill_definition (code, name, description, category, system_prompt, priority, is_active, is_builtin)
VALUES
  ('voiceover', '口播文案', '短视频/直播口播稿，带节奏 + 钩子 + 转化',
   'COPYWRITING', '你是一个短视频口播专家...（完整 prompt）', 100, TRUE, TRUE),
  ('redbook', '小红书爆款', '标题 + 正文 + 标签，符合平台算法偏好',
   'COPYWRITING', '你是小红书爆款博主...', 90, TRUE, TRUE),
  ('product-copy', '产品文案', '卖点提炼 / 详情页 / 落地页 / 转化文案',
   'COPYWRITING', '你是电商产品文案专家...', 80, TRUE, TRUE),
  ('ip-position', 'IP 定位', '个人品牌定位、人设打磨、内容策略',
   'STRATEGY', '你是个人 IP 操盘手...', 70, TRUE, TRUE),
  ('short-script', '短视频脚本', '分镜 / 台词 / 节奏，按平台时长适配',
   'COPYWRITING', '你是短视频编剧...', 60, TRUE, TRUE),
  ('title-topic', '标题选题', '标题打磨 + 选题推荐，热点借势',
   'COPYWRITING', '你是爆款标题专家...', 50, TRUE, TRUE),
  ('biz-analysis', '商业分析', '市场洞察 / 竞品对标 / SWOT 分析',
   'STRATEGY', '你是资深商业分析师...', 40, TRUE, TRUE);
```

前端通过 `/api/ai/skills?category=COPYWRITING&isActive=true` 拉取，按 priority 排序。

## 前端 hook 契约（developer-webui 使用）

后端按上述实现完成后，前端需新增 hooks：

```ts
// lib/queries/use-project-templates.ts
export function useProjectTemplates(params: { category?, isOfficial?, keyword? })
export function useProjectTemplate(id: number)
export function useForkProjectTemplate()  // mutation: { templateId, name, description } => projectId

// lib/queries/use-project-resources.ts
export function useProjectResources(projectId: number)
export function useLinkProjectResource()
export function useUnlinkProjectResource()

// lib/queries/use-avatar-outfits.ts
export function useAvatarOutfits(params: { type?, ownedOnly? })
export function usePurchaseOutfit()
export function useMyAvatarInventory()
export function useEquipOutfit()
export function useUnequipOutfit()

// lib/queries/use-user-favorites.ts
export function useUserFavorites(params: { targetType? })
export function useAddFavorite()
export function useRemoveFavorite()
export function useToggleFavorite()  // 复合：判断已存在 → DELETE，否则 POST

// lib/queries/use-weather.ts
export function useWeather(city: string)

// lib/queries/use-prompt-templates.ts（沿用 generation-templates）
export function useMyPromptTemplates(params)
export function useCreatePromptTemplate()
export function useUpdatePromptTemplate()
export function useDeletePromptTemplate()

// lib/queries/use-ai-skills.ts（已有时复用，无则新建）
export function useAiSkills(params: { category?, isActive? })
```

每个 hook 严格按 TanStack Query 模式：`queryKey` + `queryFn` + 必要的 `invalidate`，错误用 `onError` toast。

## 前端页面契约（按真实接口实现，零 mock）

| 页面 | 接口绑定 |
|------|---------|
| `/studio/templates` | `useProjectTemplates` 列表 + 分类 tab + fork 按钮 |
| `/studio/projects/new` | 选模板（`useProjectTemplates`）→ fork → 跳详情 |
| `/studio/projects/[id]` 资源面板 | `useProjectResources` + 添加/删除 |
| `/studio/me/outfits` | `useAvatarOutfits` 商城 + `useMyAvatarInventory` 我的 + 装备/购买 |
| `/studio/knowledge/favorites` | `useUserFavorites` + 按 targetType 分组 + 移除 |
| `/studio/assets/prompts` | `useMyPromptTemplates` + CRUD + 复用按钮 |
| `/studio/create/copy` | **变真**：`useAiSkills({category:"COPYWRITING"})` 替换硬编码 7 项 |
| `/studio/create/viral` | **变真**：接 `CopywritingController` 4 步真实链路 |
| `/studio/create/tools/weather` | `useWeather(city)` |
| `/studio/me/account` | `/system/user/profile` 已有 |
| `/studio/me/membership` | `/subscriptions/*` `/credit-token-rules` 已有 |
| `/studio/me/credits` | `/credits/*` `/redeem-codes` 已有 |
| `/studio/me/invite` | `/brokerage/*` 已有，迁移交互 |
| `/studio/assets/works,materials,history` | 已有接口 |
| `/studio/knowledge`、`/studio/knowledge/docs` | 已有接口，迁移 |

## ADR（关键设计决策）

| # | 决策 | 备选 | 选定 | 理由 |
|---|------|-----|-----|------|
| 1 | 用 `Result<T>` 还是直接返回 | 已有规范 | `Result<T>` | 统一错误处理 |
| 2 | 模板 fork 是事务还是异步 | 异步 | 同步事务 | fork 后必须立刻拿到 projectId 跳详情，不能异步 |
| 3 | 装扮购买扣积分 | 走积分系统 | 复用 `CreditService.spend` | 避免双系统 |
| 4 | 数据隔离失败返回 403 还是 404 | 403 | 404 | 防探测：跨用户访问不暴露资源存在性 |
| 5 | 收藏唯一约束 | (user, target) 联合唯一 | (user, type, id) 联合唯一 + 软删除 | 支持取消后重新收藏，软删保留历史 |
| 6 | 装扮表 v0.1 是否包 COCKPIT_THEME | 包 | 不包 | 简化版只做 AVATAR/OUTFIT，COCKPIT 留 v0.2 |
| 7 | 模板 categoty 用 enum 还是 dict | dict | varchar 软枚举 | 后续扩展不动 schema |

## Flyway 迁移文件

```
db/migration/v16__user_studio_project_type_ext.sql       -- BE-1
db/migration/v17__user_studio_tables.sql                 -- BE-2/3/4/5（5 张新表）
db/seed/v18__user_studio_seed.sql                        -- 模板 + 装扮 + 文案技能种子
```

## 完工验证（前后端联调）

每个功能交付时，前后端联调清单：

- [ ] 后端 `pnpm nx test service` 全绿
- [ ] 前端 `pnpm nx test webui` 全绿
- [ ] Postman/curl 命中接口验证（含未登录 401、跨用户 404、参数校验 400）
- [ ] 前端页面真机操作走通（生图/视频/项目/装扮等）
- [ ] **数据隔离验证**：A 用户登录看不到 B 用户的项目/资产/装扮（核心安全门）



## Studio 首页四行改版

> 本节是 AAF-100 首页改版的增量设计，只覆盖 [requirement.md](./requirement.md) 中定义的 Reduction MVP。前文历史方案继续保留；与本节冲突的首页结构、蓝图封面字段和 Flyway 编号，以本节为准。

### 目标与边界

本次改造复用现有 AIGC `configuration`、`project` 与 Studio 页面能力，不新增平行模板、项目或创作体系。

- 首页严格保留四个顶层内容区块，顺序固定为：统计卡、最近项目、蓝图、快速创作。
- 删除首页对 `HomeRecentAssets`（“最近生成”）的导入与渲染，但不在本任务删除其组件或改造资产页。
- 复用现有 `HomeDataCapsules`、`RecentProjectGrid`、蓝图查询和项目 `_materialize` 链路；统计口径、最近项目口径与项目详情页不变。
- 五个既有创作能力直接进入原路由；数字人只进入统一创作入口的占位模式。
- `HomeChatLauncher` 不在首页四区中使用，本任务不重构、不迁移、不删除该组件。
- 不新增蓝图管理、排序、发布接口，不重构既有图像、视频、文案、配音和音乐页面。

### 全局配置租户语义

项目类型、项目蓝图、渠道规格、领域扩展、项目类型兼容包和执行绑定是平台级版本化定义，统一使用 `TenantScope.GLOBAL`，对应实体标注 `@OrgIgnore`。这些表的种子记录与运行时记录保持 `org_id = NULL`、`workspace_id = NULL`，与现有全局唯一键一致。

- `member`、`org_admin` 可读取和使用全局定义，但不能创建、更新、删除或发布。
- `admin`、`super_admin` 才可维护全局定义。
- 项目、品牌/IP、媒体、任务和资产等业务数据继续按组织/工作区隔离。
- 若未来需要组织自定义蓝图，应新增明确的组织覆盖/扩展模型，不在全局定义表中混存组织数据。
- `@OrgIgnore` 与 `TenantScope.GLOBAL` 必须成对使用：前者关闭 Hibernate 组织过滤，后者令 CRUD 安全决策只接受 `org_id/workspace_id` 均为空的记录。

### 首页结构

`apps/webui/src/app/studio/page.tsx` 的内容容器只渲染以下四个直接子 `section`。页面标题、全局导航、欢迎页重定向和 toast 不计入内容区块，但不得插入为第五个业务 `section`。

| 顺序 | 区块 | 复用或新增 | 数据与失败边界 |
|------|------|------------|----------------|
| 第一行 | 统计卡 | 复用 `HomeDataCapsules` | 各统计查询沿用现状；失败不阻塞其他区块 |
| 第二行 | 最近项目 | 复用 `RecentProjectGrid` | 沿用现有最近排序和数量；空态/失败态只占本区 |
| 第三行 | 蓝图 | 新增首页蓝图区，复用项目类型和蓝图 Query | 类型 Tabs、已发布蓝图卡、空态/失败态、建项 Dialog 均封装在本区 |
| 第四行 | 快速创作 | 新增快速入口区 | 静态路由卡不依赖前三行数据，可独立导航 |

响应式只允许区块内部卡片网格换行，不改变四个顶层区块的数量和 DOM 顺序。`HomeRecentAssets` 不再出现在首页；其原有媒体查询也不得由首页触发。

### 数据库基线

当前项目尚未部署，AIGC 基线迁移仍允许按既有批准直接重建。本增量不新增 Flyway 版本，直接修改：

`apps/service/aaf-api/src/main/resources/db/migration/v7__aigc_schema.sql`

在 `aigc_project_blueprint` 建表定义中加入：

```sql
cover_url VARCHAR(1000),
```

并增加列注释：

```sql
COMMENT ON COLUMN aigc_project_blueprint.cover_url IS '蓝图卡封面 URL，可空；前端加载失败时按项目类型使用渐变封面';
```

约束如下：

- PostgreSQL 列为 `aigc_project_blueprint.cover_url VARCHAR(1000)`，nullable，不设默认值。
- 不新增 `v104` 或其他增量迁移；所有未部署环境按 Flyway 基线重新构建。
- 同步修改基线 `v102__aigc_entity_def.sql` 的蓝图字段配置，使管理视图可编辑 `coverUrl`。
- 不创建索引；首页不按 `cover_url` 查询或排序。

### Java 字段与映射

现有 `AigcProjectBlueprint` CRUD 契约按同名字段贯通，HTTP 路径和资源名称不变。

```java
// AigcProjectBlueprint
@Column(name = "cover_url", length = 1000)
private String coverUrl;

// AigcProjectBlueprintCreateDTO
@Size(max = 1000) String coverUrl

// AigcProjectBlueprintUpdateDTO
Patch<String> coverUrl

// AigcProjectBlueprintVO
String coverUrl
```

具体映射要求：

- `AigcProjectBlueprintCreateDTO` 在 `description` 后增加 nullable `coverUrl`；MapStruct `AigcProjectBlueprintConvert` 依同名字段自动映射到 Entity。
- `AigcProjectBlueprintUpdateDTO` 增加 `Patch<String> coverUrl`，规范构造器执行 `coverUrl = normalize(coverUrl)`，`fromJson` 使用 `Patch.parse(coverUrl, AigcConfigurationPatchDecoder::text)`。缺席表示不修改，显式 `null` 表示清空。
- `AigcProjectBlueprintVO` 在 `description` 后返回 `coverUrl`。由于 VO 使用 `NON_NULL`，数据库为 `null` 时响应可省略该字段；前端须同时接受缺失与 `null`。
- `AigcProjectBlueprintService.toVO` 增加 `entity.getCoverUrl()`；`updateEntity` 使用 `AigcConfigurationPatchSupport.nullable(request.coverUrl(), entity::setCoverUrl)`。
- 发布后蓝图仍遵循既有不可变规则；`coverUrl` 只允许在草稿状态随 CRUD 修改，不绕过 `requireDraft`。
- 创建、更新、读取和清空封面均需单元测试，验证 1000 字符上限及 null 语义。

### 前端蓝图类型

`apps/webui/src/lib/api/rest/ai/aigc/configuration.ts` 的唯一蓝图类型增加：

```ts
export interface AigcProjectBlueprint {
  // 既有字段保持不变
  coverUrl?: string | null
}
```

不新建首页专用蓝图 DTO，不把 Query 结果复制到 Zustand。首页只消费服务端既有 `/aigc/project-types` 与 `/aigc/project-blueprints` 契约。

### 类型 Tabs 与蓝图卡

- Tabs 来源为状态有效的项目类型定义，沿用 `AigcProjectType.code/name/sortOrder`；前端不得新建第二套项目类型枚举。
- 蓝图查询使用 `useAigcProjectBlueprints({ projectTypeCode: activeType, status: "published" })`。Query key 已包含参数，每个 Tab 独立缓存、加载、空态和失败态。
- 即使某类型查询结果为空，其 Tab 和第三行仍保留，不用其他类型蓝图填充。
- 蓝图卡至少展示名称、项目类型、生产模式和封面区；整张卡的主要点击区域打开建项 Dialog，不先跳详情页。
- 客户端可再次断言 `blueprint.status === "published"` 且 `projectTypeCode === activeType` 作为显示保护，但服务端筛选仍是数据边界。

封面表现由蓝图卡组件内部管理，不写回 TanStack Query：

```text
coverUrl 为 null / undefined / 空白 → 直接显示项目类型渐变
coverUrl 非空且图片未失败          → 显示图片
图片 onError                       → 标记当前卡失败并立即切到项目类型渐变
blueprint.id 或 coverUrl 变化       → 重置失败标记
```

图片使用空 `alt` 或等价装饰语义，名称由卡片文本提供。`onError` 后移除或隐藏失败图片，不能保留浏览器破图图标。渐变映射以 `projectTypeCode` 为键，复用/扩展 `project-type-config` 的稳定视觉配置；未知类型使用统一中性渐变，同一类型始终一致，不同已知类型可区分。

### 蓝图建项 Dialog

Dialog 以点击时的 `selectedBlueprint` 为不可编辑上下文，展示 `projectTypeCode` 对应类型名称、蓝图名称与 `productionMode`。可编辑字段为项目名称、单选 IP、渠道和高级设置中的补充说明。

提交继续复用：

`POST /aigc/projects/_materialize`

请求映射如下：

| Dialog 字段 | `AigcProjectMaterializeInput` | 规则 |
|-------------|-------------------------------|------|
| 项目名称 | `name` | 提交前 `trim()`；空值不发请求 |
| 项目类型 | `projectTypeCode` | 取 `selectedBlueprint.projectTypeCode`，只读 |
| 蓝图 | `blueprintVersionId` | 取 `selectedBlueprint.id`，只读 |
| 生产模式 | `productionMode` | 取 `selectedBlueprint.productionMode`，只读 |
| IP | `brandProfileVersionIds` | 未选为 `[]`；已选时提交该 IP 当前已发布版本 ID 的单元素数组 |
| 渠道 | `channelSpecVersionIds` | 未选为 `[]`；已选时提交有权访问且有效的渠道规格版本 ID |
| 补充说明 | `briefJson` | trim 后为空则省略；高级设置折叠不清空 |

服务端 `AigcProjectMaterializer` 继续通过 `configurationApi.resolve(...)` 重新解析蓝图、项目类型、生产模式和渠道兼容性，通过 `brandApi.requireVersions(...)` 校验 IP 版本及工作区权限；不得信任客户端自行组合的类型、模式、IP 或渠道。蓝图非 `published`、已删除、不可访问或与类型/模式不兼容时，事务失败且不创建项目。

交互规则：

- `selectedBlueprint`、Dialog 开关、`activeType`、名称、IP、渠道、高级设置开关、补充说明和同步提交锁属于页面本地 UI 状态。
- 项目类型、蓝图列表、最近项目、IP、渠道与创建结果属于服务端状态，只能由 TanStack Query hooks 管理；不得复制到 Zustand 或用 effect 制作第二份列表。
- 打开卡片时以该蓝图建立全新表单初值；主动关闭后清理表单。创建失败不关闭、不重置，保留全部输入。
- 名称错误显示在字段附近。提交函数使用同步 `ref`/等价单航班锁并结合 mutation `isPending` 禁用按钮，避免 React 状态刷新前的连点产生第二次请求；settled 后释放，允许失败重试。
- `useMaterializeAigcProject` 成功后关闭 Dialog，失效最近项目相关 Query，并直接 `router.push(`/studio/projects/${project.id}`)`；不经过项目列表。
- 创建错误由 Dialog 内可恢复错误提示承载，不导航、不清空输入。

### 快速创作路由

第四行使用 `Link` 或 `router.push` 直接指向现有页面，不通过 `HomeChatLauncher` 转发：

| 入口 | 路由 |
|------|------|
| 图像 | `/studio/create/image` |
| 视频 | `/studio/create/video` |
| 文案 | `/studio/create/copy` |
| 配音 | `/studio/create/voice` |
| 音乐 | `/studio/create/music` |
| 数字人 | `/studio/create?mode=digital-human` |

`/studio/create` 只需识别 `mode=digital-human` 并在现有统一创作容器内展示“能力尚未开放”的明确占位状态；该状态不渲染数字人提交按钮、不调用生成 mutation、不伪造任务或结果。其他 query 参数和五个既有页面的结构、参数及生成流程不在本任务修改。

### 加载、失败与权限

四区不建立聚合式总 loading：每区独立读取自己的 Query 状态并在区内显示 skeleton、空态或重试入口。项目类型、蓝图与渠道规格作为全局定义按发布状态和平台权限读取；最近项目、IP 与新建项目继续按登录用户及组织/工作区过滤。认证失效沿用统一 `backendApi` 处理。任一区失败不得遮挡或禁用其他区。

### 测试设计

后端 developer 单测（`*Test.java`）：

- Entity/DDL 映射可读写 null 与 1000 字符 `coverUrl`。
- CreateDTO → Entity、Entity → VO、UpdateDTO Patch → Entity 的值、缺席和显式清空语义。
- `status=published + projectTypeCode` 查询不返回其他类型或非发布蓝图。
- 物化时蓝图下线、删除、类型/模式不匹配、无权 IP/渠道均失败且事务不产生项目。

前端 developer 单元/组件测试（`*.test.ts(x)`）：

- `AigcProjectBlueprint.coverUrl` 缺失、null、空白、有效图片及 `onError` 均得到正确封面，且无破图元素残留。
- Tabs 使用项目类型定义；每个 Tab 只渲染本类型 published 蓝图，空类型保留区块。
- Dialog 只读上下文、名称 trim/空值拦截、可选 IP/渠道、高级设置保值、切换蓝图重置、失败保值、重复点击单请求、成功直达详情。
- 首页恰有四个顶层业务区块且顺序固定，不渲染“最近生成”；各区失败互不阻塞。
- 六个快速入口路由精确匹配；数字人占位不暴露生成提交能力。
- 至少覆盖桌面和窄视口，确认卡片换行不改变顶层区块数量。

Tester 验收/集成测试使用 `*IT.java`、`*AcceptanceTest.java`、`*.accept.test.ts(x)` 或 Playwright，逐条映射 requirement.md 的 Gherkin AC；特别验证 AIGC 基线可重建、服务端防篡改、跨用户数据隔离、断开封面请求后的渐变回退和创建中连点。

验证命令：

```text
pnpm nx test service
pnpm nx test webui
pnpm check:affected
pnpm acceptance:affected
```

### 发布与回滚

当前项目尚未部署，不执行增量迁移。环境按更新后的 Flyway 基线重建后再部署后端与前端；未配置封面的蓝图自然使用类型渐变。

回滚采用代码与基线文件同步回退，不对已部署数据库执行删列脚本。正式部署前若迁移策略发生变化，必须重新评审并新增独立增量迁移，不得继续修改已执行的基线。
