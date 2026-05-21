package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.skill.SkillMatchEngine;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agent.runtime.AgentPool;
import com.xuejiai.aaf.framework.intelligent.agent.runtime.AgentSandbox;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;
import com.xuejiai.aaf.framework.intelligent.cognition.pipeline.MemoryPipelineFactory;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;
import com.xuejiai.aaf.framework.intelligent.core.memory.PipelineInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AssistantExecutor 默认实现： 会话管理 → 记忆拉取（按 MemoryStrategy）→ Skill 匹配 → Agent 调度 → 记忆写回。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAssistantExecutor implements AssistantExecutor {

    private final AssistantDefinitionRepository assistantRepo;
    private final SessionManager sessionManager;
    private final SkillMatchEngine skillMatch;
    private final AgentRegistryService agentRegistry;
    private final AgentPool agentPool;
    private final AgentSandbox agentSandbox;
    private final ShortTermMemoryService shortTermMemory;
    private final MemoryPipelineFactory pipelineFactory;

    @Override
    public AssistantResponse chat(
            String sessionId, String assistantId, Long userId, String userMessage) {
        // 1. 加载 Assistant 配置
        var assistant = assistantRepo.findByAssistantId(assistantId).orElse(null);
        if (assistant == null) {
            return AssistantResponse.error(sessionId, "Assistant 不存在: " + assistantId);
        }

        // 2. 会话管理
        sessionManager
                .getSession(sessionId)
                .orElseGet(() -> sessionManager.createSession(userId, assistantId));
        sessionManager.updateStatus(sessionId, SessionManager.SessionStatus.PROCESSING);

        // 3. 记录用户消息
        shortTermMemory.append(sessionId, new MemoryMessage("user", userMessage, null));

        // 4. 按 MemoryStrategy 拉取上下文
        var pipeline = pipelineFactory.create(assistant.getMemoryStrategy());
        var memoryContext =
                pipeline.execute(
                        new PipelineInput(
                                userMessage, userId, sessionId, assistant.getKnowledgeBaseId()));

        // 5. Skill 匹配
        var skill = skillMatch.match(assistantId, userMessage);
        var agentId = skill.map(s -> s.agentId()).orElse(null);

        // 6. Agent 调度
        String response;
        boolean success;
        if (agentId != null) {
            var definition = agentRegistry.findById(agentId);
            if (definition != null) {
                // 将记忆上下文注入 Agent 系统提示词
                var contextPrompt = memoryContext.toPromptSection();
                if (!contextPrompt.isBlank()) {
                    definition.setSystemPrompt(
                            skill.map(s -> s.systemPrompt()).orElse(definition.getSystemPrompt())
                                    + "\n\n## 上下文记忆\n"
                                    + contextPrompt);
                }
                var executor = agentPool.borrow(definition);
                try {
                    var result =
                            agentSandbox.execute(
                                    executor,
                                    userMessage,
                                    Duration.ofSeconds(definition.getTimeoutSeconds()));
                    response = result.success() ? result.output() : "执行失败: " + result.error();
                    success = result.success();
                } finally {
                    agentPool.release(agentId, executor);
                }
            } else {
                response = "抱歉，当前无法处理该请求。";
                success = false;
            }
        } else {
            response = "我不确定如何帮助你，请尝试更具体的描述。";
            success = false;
        }

        // 7. 记录响应到短期记忆
        shortTermMemory.append(sessionId, new MemoryMessage("assistant", response, null));
        sessionManager.updateStatus(sessionId, SessionManager.SessionStatus.ACTIVE);

        return success
                ? AssistantResponse.success(response, sessionId)
                : AssistantResponse.error(sessionId, response);
    }
}

@Repository
interface AssistantDefinitionRepository extends JpaRepository<AssistantDefinition, Long> {
    Optional<AssistantDefinition> findByAssistantId(String assistantId);
}
