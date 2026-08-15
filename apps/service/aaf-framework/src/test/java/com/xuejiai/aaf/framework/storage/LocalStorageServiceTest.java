package com.xuejiai.aaf.framework.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LocalStorageService 单元测试（B14 路径穿越防护）。 */
class LocalStorageServiceTest {

    @TempDir Path baseDir;

    private StorageClient service;

    @BeforeEach
    void setUp() {
        service = new LocalStorageService(new LocalStorageSpec(baseDir.toString(), "/files"));
    }

    /** B14：下载越过 basePath 的相对路径 → 非法路径拒绝。 */
    @Test
    void download_路径穿越拒绝() {
        assertThatThrownBy(() -> service.download("../../etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("非法路径");
    }

    /** B14：删除越权路径 → 非法路径拒绝。 */
    @Test
    void delete_路径穿越拒绝() {
        assertThatThrownBy(() -> service.delete("../outside.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("非法路径");
    }

    /** 本地配置缺少 domain 时，禁止生成形如 null/key 的无效访问地址。 */
    @Test
    void getUrl_缺少domain拒绝() {
        var withoutDomain = new LocalStorageService(new LocalStorageSpec(baseDir.toString(), null));

        assertThatThrownBy(() -> withoutDomain.getUrl("2026/08/15/image.png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("缺少后端访问 domain");
    }

    /** 合法 key 通过路径校验（文件不存在时报下载失败，而非非法路径）。 */
    @Test
    void download_合法key通过校验() {
        assertThatThrownBy(() -> service.download("2026/05/30/a.png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("下载失败");
    }
}
