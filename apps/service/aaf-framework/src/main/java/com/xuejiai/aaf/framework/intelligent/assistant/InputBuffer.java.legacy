package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 输入缓冲区——Agent 执行期间接收用户追加输入，分类后决定处理时机。
 *
 * <p>会话级，内存存储。Agent 通过 Checkpoint 回调检查新输入。
 */
public class InputBuffer {

    /** 输入类型 */
    public enum InputType {
        /** 取消/中断：立即中断当前执行 */
        CANCEL,
        /** 修改指令：标记当前结果待废弃，重新规划 */
        MODIFY,
        /** 补充信息：注入当前执行上下文（下一个 Checkpoint 可见） */
        SUPPLEMENT,
        /** 无关/闲聊：排队，当前任务完成后处理 */
        UNRELATED
    }

    /** 缓冲条目 */
    public record BufferedInput(InputType type, String content, long timestamp) {}

    private final Queue<BufferedInput> buffer = new ConcurrentLinkedQueue<>();

    /** 追加一条输入到缓冲区 */
    public void offer(InputType type, String content) {
        buffer.offer(new BufferedInput(type, content, System.currentTimeMillis()));
    }

    /** 取出并移除下一条输入（无则返回 null） */
    public BufferedInput poll() {
        return buffer.poll();
    }

    /** 查看下一条输入但不移除 */
    public BufferedInput peek() {
        return buffer.peek();
    }

    /** 是否有待处理的取消/中断请求 */
    public boolean hasCancelRequest() {
        return buffer.stream().anyMatch(i -> i.type() == InputType.CANCEL);
    }

    /** 取出所有补充信息（注入上下文用） */
    public java.util.List<BufferedInput> drainSupplements() {
        var supplements = new java.util.ArrayList<BufferedInput>();
        buffer.removeIf(
                i -> {
                    if (i.type() == InputType.SUPPLEMENT) {
                        supplements.add(i);
                        return true;
                    }
                    return false;
                });
        return supplements;
    }

    /** 缓冲区是否为空 */
    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /** 清空缓冲区 */
    public void clear() {
        buffer.clear();
    }
}
