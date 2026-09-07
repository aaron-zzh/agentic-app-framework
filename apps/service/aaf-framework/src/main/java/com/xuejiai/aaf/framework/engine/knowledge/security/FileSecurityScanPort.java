package com.xuejiai.aaf.framework.engine.knowledge.security;

/**
 * 文件安全扫描端口——文档解析前的恶意内容检测边界。
 *
 * <p>只有 {@link ScanStatus#CLEAN} 允许进入 {@link
 * com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory} 解析；{@code PENDING}、{@code
 * UNAVAILABLE}、{@code INFECTED}、{@code ERROR} 一律隔离或拒绝，禁止在扫描不可用时降级放行。
 */
public interface FileSecurityScanPort {

    /**
     * 扫描文件内容。
     *
     * @param content 文件字节内容
     * @param filename 原始文件名（供扫描实现按扩展名分流，可选）
     * @param mimeType 文件 MIME 类型（可选）
     * @return 扫描结果，包含状态、扫描器标识与诊断详情
     */
    ScanResult scan(byte[] content, String filename, String mimeType);

    /** 扫描状态。 */
    enum ScanStatus {
        /** 未检出风险，允许进入解析。 */
        CLEAN,
        /** 检出恶意内容，禁止使用。 */
        INFECTED,
        /** 扫描尚在进行中（异步扫描场景），当前不可放行。 */
        PENDING,
        /** 扫描服务不可用，不能降级为放行。 */
        UNAVAILABLE,
        /** 扫描过程发生错误，不能降级为放行。 */
        ERROR
    }

    /**
     * 扫描结果。
     *
     * @param status 扫描状态
     * @param scannerName 扫描器名称，用于 provenance 记录
     * @param scannerVersion 扫描器版本，用于 provenance 记录
     * @param detail 诊断详情（不包含敏感信息，可直接记录日志）
     */
    record ScanResult(
            ScanStatus status, String scannerName, String scannerVersion, String detail) {}
}
