package com.xuejiai.aaf.framework.intelligent.shared.event;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 事件的编排节点身份：本次执行属于哪个节点、什么角色。
 *
 * <p>用途有两个：让 AG-UI 投影能区分"面向用户的应答者"与"内部执行者"（前者发标准 {@code TEXT_MESSAGE_*}，后者降级 {@code CUSTOM} 并带
 * source 路径），以及让事件可按角色聚合做监控。DIRECT 直答等无编排板的场景整体为 {@code null}。
 *
 * <p>放在 {@code shared/event} 而非 {@code assistant/model}：它是 {@link ExecutionEvent} 的字段，需要被 L2 的
 * {@code InvocationContext} 与 L3 的编排层同时引用。自带 {@link NodeKind} 而不复用 {@code TaskBoard.Kind}，避免 L2
 * 反向依赖 L3——由编排层负责把 {@code TaskBoard.Kind} 映射进来。
 *
 * <p><b>安全约束</b>：{@code subTaskId} 在动态分解模式下是**模型生成的自由字符串**—— {@code
 * CoordinationPlan.ExecutorAssignment.subTaskId} 来自协调者输出的 plan JSON，{@code decodeAndValidatePlan}
 * 只校验格式不校验含义。因此它仅可用作展示标签与 source 路径段，**禁止用作授权、租户隔离或幂等键**；那些一律用 {@code executionId} / {@code
 * tenantId}。静态 Team 模式下该值必须匹配预定义 worker，协调者无命名权。
 *
 * <p>{@code roleKey} 与 {@code skillKey} 来自配置而非模型输出，是稳定的类型维度，监控聚合应使用它们； {@code AgentId} 是 Agent
 * 定义标识，不等于"角色"。
 *
 * @param subTaskId 板内节点键；动态分解时由协调者命名，仅作展示与路径
 * @param kind 节点类型
 * @param roleKey 角色键，稳定聚合维度；可空
 * @param skillKey 技能键，稳定聚合维度；可空
 */
public record NodeIdentity(
        String subTaskId, NodeKind kind, String roleKey, String skillKey, boolean delivery) {

    /** 节点键只允许安全字符：它会进入 AG-UI 的 source 路径，不能带斜杠或控制字符。 */
    private static final Pattern SAFE_NODE_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._\\-]{0,127}");

    /**
     * 编排节点类型；与 {@code TaskBoard.Kind} 一一对应，由编排层映射，避免跨层依赖。
     *
     * <p>四类 kind 保留在领域模型里（各有真实职责），但下游投影只关心 {@code delivery} 这个**二分**结论：交付类节点的输出
     * 是面向用户的最终回复，内部类节点的输出是中间产物。二分的判定不能只看 kind，见 {@link #userFacing()}。
     */
    public enum NodeKind {
        /** 协调者：前注意、拆解、产计划；在 PASS_THROUGH / ORDERED_CONCAT 契约下同时是交付者。 */
        COORDINATOR,
        /** 执行者：处理分配的子任务，恒为内部节点。 */
        EXECUTOR,
        /** 评估者：有界迭代组的收敛判定者，本质是被标记的执行者，恒为内部节点。 */
        EVALUATOR,
        /** 聚合者：仅在 AGGREGATOR_REDUCE 契约下由系统追加，此时它是唯一交付者。 */
        AGGREGATOR
    }

    public NodeIdentity {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (subTaskId == null || !SAFE_NODE_KEY.matcher(subTaskId).matches()) {
            throw new IllegalArgumentException("subTaskId 必须是安全节点键（模型输出需经此校验）: " + subTaskId);
        }
        if (delivery && (kind == NodeKind.EXECUTOR || kind == NodeKind.EVALUATOR)) {
            throw new IllegalArgumentException("执行者与评估者不能是交付节点: " + kind);
        }
        roleKey = blankToNull(roleKey);
        skillKey = blankToNull(skillKey);
    }

    /**
     * 是否面向用户的应答者：只有它的事件走标准 AG-UI 文本事件，内部节点一律降级 CUSTOM。
     *
     * <p><b>为什么是存储字段而非按 kind 推导</b>：交付角色取决于本板的聚合契约，光看 kind 判不出—— {@code AGGREGATOR_REDUCE}
     * 契约下板上同时存在 COORDINATOR 与 AGGREGATOR，若按 {@code kind == COORDINATOR || kind == AGGREGATOR}
     * 推导会出现两个交付节点、客户端收到两条"最终回复"。判定因此前移到建板期（那里才知道契约）， 一次算定后随事件流走；投影层不反查板结构，否则 AG-UI 层会依赖编排层。
     *
     * <p>映射规则见 {@code AssistantCommand.nodeIdentityOf}：{@code PASS_THROUGH} / {@code
     * ORDERED_CONCAT} → COORDINATOR 交付；{@code AGGREGATOR_REDUCE} → AGGREGATOR 交付且 COORDINATOR
     * 转为内部；EXECUTOR / EVALUATOR 恒内部（构造器强制）。
     */
    public boolean userFacing() {
        return delivery;
    }

    /**
     * AG-UI source 路径段。
     *
     * <p>对齐官方 {@code AgentEvent.getSource()} 的斜杠路径约定（父为 null、子为 {@code "main/researcher"}）。AAF 是平级
     * 编排，core 给出的 source 恒为 null，因此这里自行合成同形状路径。应答者返回 {@code null} 表示"父"。
     */
    public String sourcePath() {
        return userFacing() ? null : "coordinator/" + subTaskId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
