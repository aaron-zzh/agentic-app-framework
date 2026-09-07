package com.xuejiai.aaf.module.ai.assistant.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.security.FileSecurityScanPort;

/**
 * 启发式文件安全扫描适配器——{@link FileSecurityScanPort} 的默认实现。
 *
 * <p>只做确定性启发式检测，不接入外部病毒扫描引擎（如 ClamAV）：
 *
 * <ul>
 *   <li>ZIP 类文件（DOCX）解压比超过阈值判定为 {@link ScanStatus#INFECTED}（zip bomb 特征）
 *   <li>检出 XML 外部实体声明特征字符串判定为 {@link ScanStatus#INFECTED}（XXE 特征）
 *   <li>其余情况判定为 {@link ScanStatus#CLEAN}
 * </ul>
 *
 * <p><b>已知局限</b>：不做特征码病毒扫描，无法检测传统意义的恶意软件负载。真实病毒扫描能力（如 ClamAV
 * 集成）留作后续任务——引入外部扫描服务是新增运行时依赖，需要单独的部署与运维评估，不在本任务隐式引入。
 */
@Component
public class HeuristicFileSecurityScanAdapter implements FileSecurityScanPort {

    private static final String SCANNER_NAME = "aaf-heuristic-scanner";
    private static final String SCANNER_VERSION = "1.0.0";

    /** ZIP local file header 魔数。 */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4b, 0x03, 0x04};

    /** DOCX（ZIP 容器）判定为 zip bomb 的最大允许解压比。 */
    private static final int MAX_INFLATE_RATIO = 100;

    /** XXE 特征字符串（大小写不敏感）。 */
    private static final String[] XXE_MARKERS = {"<!doctype", "<!entity"};

    @Override
    public ScanResult scan(byte[] content, String filename, String mimeType) {
        if (content == null || content.length == 0) {
            return new ScanResult(ScanStatus.ERROR, SCANNER_NAME, SCANNER_VERSION, "文件内容为空");
        }
        if (containsXxeMarker(content)) {
            return new ScanResult(
                    ScanStatus.INFECTED, SCANNER_NAME, SCANNER_VERSION, "检出 XML 外部实体声明特征");
        }
        if (isZipContainer(content) && exceedsInflateRatio(content)) {
            return new ScanResult(
                    ScanStatus.INFECTED, SCANNER_NAME, SCANNER_VERSION, "ZIP 容器解压比超过安全阈值");
        }
        return new ScanResult(ScanStatus.CLEAN, SCANNER_NAME, SCANNER_VERSION, null);
    }

    private boolean isZipContainer(byte[] content) {
        if (content.length < ZIP_MAGIC.length) return false;
        for (var i = 0; i < ZIP_MAGIC.length; i++) {
            if (content[i] != ZIP_MAGIC[i]) return false;
        }
        return true;
    }

    /**
     * 实际解压并统计解压后字节数来估算解压比，超阈值判定为 zip bomb 特征。
     *
     * <p>不能依赖 {@link ZipEntry#getSize()}——DEFLATED 压缩条目在流式读取阶段该字段为 -1（size 信息在 data descriptor 或
     * central directory 中，{@link ZipInputStream} 顺序读取时不可用），只有 STORED 模式才能提前拿到准确值。DOCX 文件普遍使用
     * DEFLATED，因此必须边解压边计数，与 POI {@code ZipSecureFile} 的检测方式一致。解压总量超过阈值即提前终止，避免真正的 zip bomb 撑爆内存。
     */
    private boolean exceedsInflateRatio(byte[] content) {
        var maxUncompressed = (long) content.length * MAX_INFLATE_RATIO;
        try (var zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            long uncompressedTotal = 0;
            var buffer = new byte[8192];
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                int read;
                while ((read = zipInput.read(buffer)) != -1) {
                    uncompressedTotal += read;
                    if (uncompressedTotal > maxUncompressed) {
                        return true;
                    }
                }
                zipInput.closeEntry();
            }
            return false;
        } catch (IOException failure) {
            // ZIP 结构本身损坏交由后续 importer 解析阶段报告，扫描阶段不因结构异常判定为感染
            return false;
        }
    }

    private boolean containsXxeMarker(byte[] content) {
        // 仅在文件前 4KB 内探测，避免对大文件做全量字符串扫描
        var probeLength = Math.min(content.length, 4096);
        var probe =
                new String(content, 0, probeLength, StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
        for (var marker : XXE_MARKERS) {
            if (probe.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
