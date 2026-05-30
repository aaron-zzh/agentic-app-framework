/**
 * 组件语义注册表
 * 管理所有组件的语义描述，支持按名称/能力查找
 * @author AaronZZH & Kiro
 */

import type { DataFieldDef, EntityDef } from "@/lib/types/entity"

import type { ComponentSemantics } from "../types"

class SemanticRegistryImpl {
  private registry = new Map<string, ComponentSemantics>()

  /** 注册组件语义 */
  register(semantics: ComponentSemantics): void {
    this.registry.set(semantics.name, semantics)
  }

  /** 按名称查找 */
  find(name: string): ComponentSemantics | undefined {
    return this.registry.get(name)
  }

  /** 按能力查找（返回所有包含指定能力的组件） */
  findByCapabilities(capabilities: string[]): ComponentSemantics[] {
    return Array.from(this.registry.values()).filter((s) =>
      capabilities.every((c) => s.capabilities.includes(c))
    )
  }

  /** 获取所有已注册组件 */
  getAll(): ComponentSemantics[] {
    return Array.from(this.registry.values())
  }

  /** 从 FieldDef 生成字段语义描述 */
  generateFieldSemantics(field: DataFieldDef): ComponentSemantics {
    return {
      name: `field:${field.name}`,
      description: field.label ?? field.name,
      category: "form",
      capabilities: ["input", "validate", field.type],
      inputs: [
        {
          name: "value",
          type: field.type,
          description: `${field.label ?? field.name}的值`,
          required: field.required ?? false
        }
      ],
      outputs: [
        {
          name: "onChange",
          type: field.type,
          description: "值变更事件",
          trigger: "用户修改字段值"
        }
      ],
      actions: [
        {
          name: "setValue",
          description: `设置${field.label ?? field.name}的值`,
          params: { value: field.type },
          sideEffects: ["form-dirty"],
          reversible: true
        }
      ],
      constraints: {
        validStates: field.readOnly ? ["readonly"] : ["editable", "readonly"]
      }
    }
  }

  /** 从 EntityDef 生成视图语义描述 */
  generateViewSemantics(entity: EntityDef, view: string): ComponentSemantics {
    const viewConfigs: Record<string, () => ComponentSemantics> = {
      list: () => ({
        name: `view:${entity.slug}:list`,
        description: `${entity.label}列表视图`,
        category: "view",
        capabilities: ["browse", "filter", "sort", "search", "batch-action", "paginate"],
        inputs: [
          { name: "filters", type: "FilterCondition[]", description: "筛选条件", required: false },
          { name: "sort", type: "string", description: "排序字段", required: false }
        ],
        outputs: [
          { name: "onSelect", type: "string[]", description: "选中记录", trigger: "用户勾选行" },
          {
            name: "onNavigate",
            type: "string",
            description: "跳转记录详情",
            trigger: "用户点击行"
          }
        ],
        actions: [
          {
            name: "create",
            description: `新建${entity.label}`,
            sideEffects: ["navigate"],
            reversible: false
          },
          {
            name: "delete",
            description: `删除选中${entity.label}`,
            sideEffects: ["data-mutation"],
            reversible: true
          },
          {
            name: "export",
            description: "导出数据",
            sideEffects: ["download"],
            reversible: false
          }
        ],
        constraints: { requiredPermissions: ["read"] }
      }),
      form: () => ({
        name: `view:${entity.slug}:form`,
        description: `${entity.label}表单视图`,
        category: "form",
        capabilities: ["edit", "validate", "save", "autosave"],
        inputs: [{ name: "recordId", type: "string", description: "记录 ID", required: true }],
        outputs: [
          { name: "onSave", type: "Record", description: "保存成功", trigger: "用户提交表单" }
        ],
        actions: [
          {
            name: "save",
            description: "保存记录",
            sideEffects: ["data-mutation"],
            reversible: false
          },
          {
            name: "reset",
            description: "重置表单",
            sideEffects: ["form-reset"],
            reversible: true
          }
        ],
        constraints: { requiredPermissions: ["update"] }
      }),
      kanban: () => ({
        name: `view:${entity.slug}:kanban`,
        description: `${entity.label}看板视图`,
        category: "view",
        capabilities: ["browse", "drag-drop", "status-change"],
        inputs: [{ name: "statusField", type: "string", description: "状态字段", required: true }],
        outputs: [
          {
            name: "onMove",
            type: "{ id: string; status: string }",
            description: "卡片移动",
            trigger: "用户拖拽卡片"
          }
        ],
        actions: [
          {
            name: "moveCard",
            description: "移动卡片到新状态",
            params: { targetStatus: "string" },
            sideEffects: ["data-mutation"],
            reversible: true
          }
        ],
        constraints: { requiredPermissions: ["update"] }
      }),
      calendar: () => ({
        name: `view:${entity.slug}:calendar`,
        description: `${entity.label}日历视图`,
        category: "view",
        capabilities: ["browse", "date-navigate", "create-event"],
        inputs: [{ name: "dateField", type: "string", description: "日期字段", required: true }],
        outputs: [
          {
            name: "onDateSelect",
            type: "Date",
            description: "选中日期",
            trigger: "用户点击日期"
          }
        ],
        actions: [
          {
            name: "createAtDate",
            description: "在指定日期创建记录",
            params: { date: "string" },
            sideEffects: ["data-mutation"],
            reversible: false
          }
        ],
        constraints: { requiredPermissions: ["read", "create"] }
      }),
      canvas: () => ({
        name: `view:${entity.slug}:canvas`,
        description: `${entity.label}画布视图`,
        category: "view",
        capabilities: ["free-layout", "drag-drop", "zoom", "connect"],
        inputs: [],
        outputs: [
          {
            name: "onLayoutChange",
            type: "Layout",
            description: "布局变更",
            trigger: "用户拖拽元素"
          }
        ],
        actions: [
          {
            name: "addNode",
            description: "添加节点",
            sideEffects: ["layout-change"],
            reversible: true
          }
        ],
        constraints: { requiredPermissions: ["update"] }
      })
    }

    const generator = viewConfigs[view]
    if (generator) return generator()

    // 默认 fallback
    return {
      name: `view:${entity.slug}:${view}`,
      description: `${entity.label} ${view}视图`,
      category: "view",
      capabilities: ["browse"],
      inputs: [],
      outputs: [],
      actions: [],
      constraints: {}
    }
  }
}

/** 全局单例 */
export const SemanticRegistry = new SemanticRegistryImpl()
