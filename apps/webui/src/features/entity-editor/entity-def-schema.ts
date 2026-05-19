/**
 * EntityDef JSON Schema——供 Monaco Editor 校验和自动补全
 * @author AaronZZH & Kiro
 *
 * 基于 src/features/entity-engine/types/ 中的 TypeScript 类型手动映射为 JSON Schema。
 * Monaco 通过此 schema 提供实时校验、属性补全和文档提示。
 */

/** EntityDef JSON Schema 定义 */
export const entityDefJsonSchema = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "EntityDef",
  description: "AAF 实体配置定义",
  type: "object",
  required: ["slug", "label", "apiPath", "fields", "listView"],
  properties: {
    slug: { type: "string", description: "URL 路径 + 唯一标识" },
    label: { type: "string", description: "显示名称" },
    labelPlural: { type: "string", description: "复数名称" },
    apiPath: { type: "string", description: "后端 API 路径" },
    icon: { type: "string", description: "侧边栏图标（lucide 图标名）" },
    group: { type: "string", description: "侧边栏分组（英文 slug）" },
    groupLabel: { type: "string", description: "分组显示名称" },
    description: { type: "string", description: "实体描述" },
    mixins: { type: "array", items: { type: "string" }, description: "Mixin 名称列表" },
    extends: { type: "string", description: "继承的父实体 slug" },
    fields: {
      type: "array",
      description: "字段定义列表",
      items: { $ref: "#/definitions/FieldDef" }
    },
    listView: { $ref: "#/definitions/ListViewConfig" },
    formView: { $ref: "#/definitions/FormViewConfig" },
    kanbanView: { $ref: "#/definitions/KanbanViewConfig" },
    pivotView: { $ref: "#/definitions/PivotViewConfig" }
  },
  definitions: {
    FieldDef: {
      type: "object",
      required: ["name", "type"],
      properties: {
        name: { type: "string", description: "字段标识" },
        type: {
          type: "string",
          enum: [
            "text", "textarea", "number", "email", "date", "checkbox",
            "select", "relationship", "richText", "json", "code", "upload",
            "group", "tabs", "row"
          ],
          description: "字段类型"
        },
        label: { type: "string", description: "显示标签" },
        required: { type: "boolean", description: "是否必填" },
        hidden: { type: "boolean", description: "是否隐藏" },
        readOnly: { type: "boolean", description: "是否只读" },
        placeholder: { type: "string", description: "占位文本" },
        description: { type: "string", description: "字段描述" },
        visibleWhen: { type: "string", description: "条件可见性表达式" },
        readOnlyWhen: { type: "string", description: "条件只读表达式" },
        requiredWhen: { type: "string", description: "条件必填表达式" },
        options: {
          type: "array",
          description: "选项列表（select 类型）",
          items: {
            type: "object",
            required: ["label", "value"],
            properties: {
              label: { type: "string" },
              value: { type: "string" },
              color: { type: "string" },
              icon: { type: "string" }
            }
          }
        },
        multiple: { type: "boolean", description: "是否多选" },
        relationTo: { type: "string", description: "关联实体 slug" },
        hasMany: { type: "boolean", description: "是否一对多" },
        maxLength: { type: "number", description: "最大长度" },
        min: { type: "number", description: "最小值" },
        max: { type: "number", description: "最大值" },
        accept: { type: "string", description: "上传文件类型" },
        maxSize: { type: "number", description: "上传文件大小限制" }
      }
    },
    ListViewConfig: {
      type: "object",
      required: ["columns"],
      properties: {
        columns: {
          type: "array",
          items: {
            oneOf: [
              { type: "string" },
              {
                type: "object",
                required: ["name"],
                properties: {
                  name: { type: "string" },
                  width: { type: "string" },
                  fixed: { type: "string", enum: ["left", "right"] },
                  sortable: { type: "boolean" },
                  hidden: { type: "boolean" }
                }
              }
            ]
          },
          description: "列配置"
        },
        defaultSort: { type: "string", description: "默认排序" },
        searchableFields: { type: "array", items: { type: "string" } },
        filterableFields: { type: "array", items: { type: "string" } },
        inlineEdit: { type: "boolean" },
        batchActions: { type: "array", items: { type: "string" } },
        pageSize: { type: "number" },
        draggable: { type: "boolean" },
        orderField: { type: "string" }
      }
    },
    FormViewConfig: {
      type: "object",
      properties: {
        layout: { type: "array", description: "表单布局" },
        autosave: {
          type: "object",
          properties: {
            enabled: { type: "boolean" },
            debounceMs: { type: "number" }
          }
        },
        labelLayout: { type: "string", enum: ["top", "left"] }
      }
    },
    KanbanViewConfig: {
      type: "object",
      required: ["statusField", "cardTitle"],
      properties: {
        statusField: { type: "string", description: "分列字段" },
        cardTitle: { type: "string", description: "卡片标题字段" },
        cardDescription: { type: "string" },
        cardAvatar: { type: "string" },
        columnOrder: { type: "array", items: { type: "string" } }
      }
    },
    PivotViewConfig: {
      type: "object",
      required: ["enabled", "dimensions", "measures"],
      properties: {
        enabled: { type: "boolean" },
        dimensions: { type: "array", items: { type: "string" } },
        measures: {
          type: "array",
          items: {
            type: "object",
            required: ["field", "aggregations"],
            properties: {
              field: { type: "string" },
              aggregations: {
                type: "array",
                items: { type: "string", enum: ["count", "sum", "avg", "min", "max"] }
              },
              label: { type: "string" }
            }
          }
        }
      }
    }
  }
} as const
