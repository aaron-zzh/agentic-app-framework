package com.xuejiai.aaf.module.tool;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry.ToolMeta;
import com.xuejiai.aaf.framework.engine.tool.mcp.McpConnectionService;
import com.xuejiai.aaf.framework.engine.tool.mcp.McpConnectionService.McpServerConfig;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具管理服务——对外提供工具查询、调用、生命周期管理和 MCP Server 管理能力
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolRegistry toolRegistry;
    private final ToolCallDispatcher toolCallDispatcher;
    private final ToolPermissionChecker permissionChecker;
    private final OperatorContext operatorContext;
    private final McpConnectionService mcpConnectionService;
    private final McpServerRepository mcpServerRepository;

    /** 应用启动后重连所有已启用的 MCP Server。 */
    @EventListener(ApplicationReadyEvent.class)
    public void reconnectMcpServers() {
        var servers =
                mcpServerRepository.findByEnabledTrue().stream()
                        .map(
                                s ->
                                        new McpServerConfig(
                                                s.getName(),
                                                s.getUrl(),
                                                s.getTransport() != null
                                                        ? s.getTransport()
                                                        : "HTTP"))
                        .toList();
        if (!servers.isEmpty()) {
            mcpConnectionService.reconnectAll(servers);
        }
    }

    /** 查询所有已注册工具 */
    public List<ToolVO> list(String source) {
        var tools = source != null ? toolRegistry.listBySource(source) : toolRegistry.listAll();
        return tools.stream().map(this::toVO).toList();
    }

    /** 调用工具（统一入口） */
    public ToolCallResult invoke(String toolName, String arguments) {
        if (toolRegistry.getCallback(toolName).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + toolName);
        }
        var userId = operatorContext.currentUserId().orElse(null);
        return toolCallDispatcher.dispatchWithPermission(null, userId, null, toolName, arguments);
    }

    /** 按 Role 获取可用工具列表 */
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

    /** 真正注销工具（从 ToolRegistry 彻底移除）。 */
    public void delete(String name) {
        if (toolRegistry.getCallback(name).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + name);
        }
        toolRegistry.unregister(name);
    }

    /** 禁用工具（保留注册，调用时拦截） */
    public void disable(String name) {
        // 由 ToolCatalogProvider 策略层控制 enabled，此处委托 ToolCallDispatcher 检查
        if (toolRegistry.getCallback(name).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + name);
        }
        toolRegistry.unregister(name);
        log.info("工具已禁用（注销）: {}", name);
    }

    /** 启用工具（重新注册，LOCAL 工具需重启，MCP 工具重连即可） */
    public void enable(String name) {
        log.warn("enable 需重新注册工具，LOCAL 工具需重启服务: {}", name);
    }

    // ==================== MCP Server 管理 ====================

    /** 添加并连接 MCP Server，成功后持久化到 DB。 */
    @Transactional
    public McpServerVO addMcpServer(McpServerAddDTO dto) {
        if (mcpServerRepository.existsByName(dto.name())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "MCP Server 名称已存在: " + dto.name());
        }
        var transport = dto.transport() != null ? dto.transport().toUpperCase() : "HTTP";

        // 先连接，成功再持久化
        mcpConnectionService.connect(dto.name(), dto.url(), transport);

        var entity = new McpServer();
        entity.setName(dto.name());
        entity.setUrl(dto.url());
        entity.setDescription(dto.description());
        entity.setTransport(transport);
        entity.setStatus("connected");
        mcpServerRepository.save(entity);

        return toVO(entity);
    }

    /** 查询 MCP Server 列表 */
    public List<McpServerVO> listMcpServers() {
        return mcpServerRepository.findAll().stream().map(this::toVO).toList();
    }

    /** 移除 MCP Server 并注销其工具 */
    @Transactional
    public void removeMcpServer(Long id) {
        var entity =
                mcpServerRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "MCP Server 不存在"));
        mcpConnectionService.disconnect(entity.getName());
        mcpServerRepository.deleteById(id);
    }

    /** 用户批准工具调用权限（临时授权）。 */
    public void approve(String sessionId, String toolName) {
        permissionChecker.grantTemporary(sessionId, toolName);
    }

    private ToolVO toVO(ToolMeta meta) {
        return new ToolVO(meta.name(), meta.description(), meta.source(), meta.parametersSchema());
    }

    private McpServerVO toVO(McpServer entity) {
        return new McpServerVO(
                entity.getId(),
                entity.getName(),
                entity.getUrl(),
                entity.getDescription(),
                entity.getTransport(),
                entity.getStatus());
    }
}
