/**
 * 执行期输入分类边界。
 *
 * @author Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/**
 * 服务端重新判定用户提交的执行期输入意图，不直接信任客户端建议的 {@link ExecutionInput.Kind}
 * （方案 C，2026-08-30 拍板）。
 *
 * <p>取消不经本分类器——统一走既有确定性 {@code /stop} 端点；本入口只在
 * {@code MODIFY}/{@code SUPPLEMENT}/{@code UNRELATED} 三者间判定。
 */
public interface InputClassifier {

    /**
     * 对原始文本重新分类；分类器不可用、异常或输出非法时必须安全默认为 {@code UNRELATED}
     * （fail-closed，不放大为 MODIFY 造成意外重规划）。
     *
     * @param text 用户原始输入正文
     * @param taskContext 当前任务的目标/状态摘要，供分类判断参考，不作为可信执行依据
     * @param userId 用于模型调用计量
     */
    ExecutionInput.Kind classify(String text, String taskContext, UserId userId);
}
