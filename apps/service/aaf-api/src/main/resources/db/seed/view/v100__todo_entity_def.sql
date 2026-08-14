-- ============================================================
-- Todo 内置 EntityDef
-- 仅提供 UI 元数据；数据始终由 /api/todos 的类型化服务管理。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'todo',
    $json$
    {
      "kind": "code",
      "resource": "system.todo",
      "label": "待办",
      "labelPlural": "待办",
      "icon": "check-square",
      "group": "system",
      "groupLabel": "系统管理",
      "fields": [
        {
          "type": "text",
          "name": "title",
          "label": "标题",
          "required": true
        },
        {
          "type": "select",
          "name": "category",
          "label": "分类",
          "dictType": "sys_todo_category"
        },
        {
          "type": "select",
          "name": "status",
          "label": "状态",
          "dictType": "sys_todo_status"
        },
        {
          "type": "recordReference",
          "name": "source",
          "label": "关联来源",
          "writeKey": "source",
          "idValueType": "number",
          "excludeEntitySlugs": [
            "todo",
            "user",
            "role",
            "permission",
            "org",
            "organization",
            "workspace",
            "menu",
            "dict"
          ],
          "readOnlyWhen": "$record.sourceType !== 'manual'"
        },
        {
          "type": "relationship",
          "name": "assignee",
          "label": "执行人",
          "relationTo": "system.user",
          "writeKey": "assigneeId",
          "displayModes": [
            "create",
            "edit"
          ],
          "visibleRoles": [
            "org_admin",
            "super_admin"
          ]
        },
        {
          "type": "relationship",
          "name": "participants",
          "label": "参与人",
          "relationTo": "system.user",
          "hasMany": true,
          "writeKey": "participantIds"
        },
        {
          "type": "date",
          "name": "dueDate",
          "label": "截止时间",
          "includeTime": true
        }
      ],
      "actions": [
        {
          "key": "clearDoneSync",
          "label": "清理已完成待办",
          "type": "entity",
          "execution": "sync",
          "endpoint": "/api/todos/actions/clear-done-sync",
          "confirmMessage": "将立即清理当前组织内全部用户的已完成待办，且不可恢复。是否继续？",
          "position": "listToolbar"
        },
        {
          "key": "clearDone",
          "label": "清理已完成待办（异步）",
          "type": "entity",
          "execution": "async",
          "endpoint": "/api/todos/actions/clear-done",
          "confirmMessage": "将清理当前组织内全部用户的已完成待办，且不可恢复。是否继续？",
          "position": "listToolbar"
        }
      ],
      "listView": {
        "columns": [
          "title",
          "category",
          "status",
          "assignee",
          "dueDate",
          "createTime"
        ],
        "defaultSort": "id:desc",
        "searchableFields": ["title"],
        "quickFilters": [
          {
            "label": "今日到期",
            "conditions": [
              {
                "field": "dueDate",
                "operator": "between",
                "values": ["$todayStart", "$tomorrowStart"]
              }
            ]
          },
          {
            "label": "未来三天",
            "conditions": [
              {
                "field": "dueDate",
                "operator": "between",
                "values": ["$now", "$nowPlus3Days"]
              }
            ]
          },
          {
            "label": "已逾期",
            "conditions": [
              {
                "field": "dueDate",
                "operator": "lt",
                "values": ["$now"]
              }
            ]
          }
        ],
        "batchActions": [
          "delete"
        ]
      },
      "formView": {
        "autosave": {
          "enabled": true,
          "debounceMs": 2000
        }
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb,
    TRUE,
    TRUE
)
-- 若存在未删除的同 slug 记录，则以本次 VALUES 行（EXCLUDED）的内置 UI 配置覆盖它。
-- 已软删除记录不参与冲突判定，因此可保留历史记录并插入新的活动 Todo 定义。
ON CONFLICT (slug) WHERE deleted = FALSE DO UPDATE
SET config = EXCLUDED.config,
    builtin = EXCLUDED.builtin,
    enabled = EXCLUDED.enabled,
    update_time = CURRENT_TIMESTAMP;
