package com.xuejiai.aaf.module.system.tool;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry.ToolMeta;

import lombok.RequiredArgsConstructor;

/**
 * 工具管理服务——对外提供工具查询、调用、生命周期管理和 MCP Server 管理能力
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolRegistry toolRegistry;
    private final ToolCallDispatcher toolCallDispatcher;
    private final ToolPermissionChecker permissionChecker;

    /** 已禁用的工具名集合 */
    private final ConcurrentHashMap<String, Boolean> disabledTools = new ConcurrentHashMap<>();

    /** MCP Server 列表（内存管理，后续可持久化） */
    private final ConcurrentHashMap<Long, McpServerVO> mcpServers = new ConcurrentHashMap<>();
    private long mcpServerIdSeq = 0;

    /**
     * 查询所有已注册工具
     *
     * @param source 工具来源过滤（LOCAL/MCP/CUSTOM），为 null 时查全部
     * @return 工具列表
     */
    public List<ToolVO> list(String source) {
        var tools = source != null ? toolRegistry.listBySource(source) : toolRegistry.listAll();
        return tools.stream().map(this::toVO).toList();
    }

    /**
     * 调用工具（统一入口，不管调用方是谁）
     *
     * @param toolName 工具名称
     * @param arguments 调用参数（JSON 字符串）
     * @return 工具调用结果
     */
    public ToolCallResult invoke(String toolName, String arguments) {
        if (disabledTools.containsKey(toolName)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工具已禁用: " + toolName);
        }
        if (toolRegistry.getCallback(toolName).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + toolName);
        }
        return toolCallDispatcher.dispatch(toolName, arguments);
    }

    /**
     * 按 Role 获取可用工具列表
     *
     * @param roleId 角色 ID
     * @return 该角色白名单内的工具列表
     */
    public List<ToolVO> listByRole(String roleId) {
        return toolRegistry.resolveForRole(roleId).stream()
                .map(
                        cb -> {
                            var name = cb.getToolDefinition().name();
                            var meta =
                                    toolRegistry.listAll().stream()
                                            .filter(m -> m.name().equals(name))
                                            .findFirst()
                                            .orElse(
                                                    new ToolMeta(
                                                            name,
                                                            cb.getToolDefinition().description(),
                                                            "UNKNOWN",
                                                            null));
                            return toVO(meta);
                        })
                .toList();
    }

    /**
     * 删除/注销工具
     *
     * @param name 工具名称
     */
    public void delete(String name) {
        if (toolRegistry.getCallback(name).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + name);
        }
        toolRegistry.getCallback(name); // 确认存在
        disabledTools.remove(name);
        // 从注册中心移除（ToolRegistry 当前无 unregister 方法，标记为禁用等效删除）
        disabledTools.put(name, true);
    }

    /**
     * 禁用工具
     *
     * @param name 工具名称
     */
    public void disable(String name) {
        if (toolRegistry.getCallback(name).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + name);
        }
        disabledTools.put(name, true);
    }

    /**
     * 启用工具
     *
     * @param name 工具名称
     */
    public void enable(String name) {
        if (toolRegistry.getCallback(name).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + name);
        }
        disabledTools.remove(name);
    }

    /**
     * 添加 MCP Server
     *
     * @param dto MCP Server 添加请求
     * @return 添加后的 MCP Server 信息
     */
    public McpServerVO addMcpServer(McpServerAddDTO dto) {
        var id = ++mcpServerIdSeq;
        var vo = new McpServerVO(id, dto.name(), dto.url(), dto.description());
        mcpServers.put(id, vo);
        return vo;
    }

    /**
     * 查询 MCP Server 列表
     *
     * @return MCP Server 列表
     */
    public List<McpServerVO> listMcpServers() {
        return List.copyOf(mcpServers.values());
    }

    /**
     * 移除 MCP Server
     *
     * @param id MCP Server ID
     */
    public void removeMcpServer(Long id) {
        if (mcpServers.remove(id) == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "MCP Server 不存在");
        }
    }

    private ToolVO toVO(ToolMeta meta) {
        return new ToolVO(meta.name(), meta.description(), meta.source(), meta.parametersSchema());
    }

    /**
     * 用户批准工具调用权限（临时授权）
     *
     * @param sessionId 会话 ID
     * @param toolName 工具名称
     */
    public void approve(String sessionId, String toolName) {
        permissionChecker.grantTemporary(sessionId, toolName);
    }
}
