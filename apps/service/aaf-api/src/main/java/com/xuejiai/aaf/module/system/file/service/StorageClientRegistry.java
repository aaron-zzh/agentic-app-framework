package com.xuejiai.aaf.module.system.file.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.storage.StorageClient;
import com.xuejiai.aaf.framework.storage.StorageClientFactory;
import com.xuejiai.aaf.framework.storage.StorageCredentialProvider;
import com.xuejiai.aaf.framework.storage.StorageException;
import com.xuejiai.aaf.framework.storage.StorageSpec;
import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;

/** 单实例一期动态存储客户端注册表。 */
@Component
public class StorageClientRegistry {

    private static final String VALIDATION_CONTENT_TYPE = "text/plain";

    private final Map<StorageType, StorageClientFactory<?>> factories;
    private final StorageCredentialProvider credentialProvider;
    private final Map<Long, RegisteredClient> clients = new ConcurrentHashMap<>();

    public StorageClientRegistry(
            List<StorageClientFactory<?>> factories, StorageCredentialProvider credentialProvider) {
        var indexed = new EnumMap<StorageType, StorageClientFactory<?>>(StorageType.class);
        for (var factory : factories) {
            if (indexed.put(factory.type(), factory) != null) {
                throw new IllegalStateException("存储类型工厂重复: " + factory.type());
            }
        }
        this.factories = Map.copyOf(indexed);
        this.credentialProvider = credentialProvider;
    }

    public StorageClient resolve(FileConfig config) {
        var signature = signature(config);
        var registered =
                clients.compute(
                        config.getId(),
                        (id, current) -> {
                            if (current != null && current.signature().equals(signature)) {
                                return current;
                            }
                            var replacement = new RegisteredClient(signature, create(config));
                            if (current != null) {
                                current.client().close();
                            }
                            return replacement;
                        });
        return registered.client();
    }

    /** 通过上传、回读和删除临时对象验证存储配置的完整可用性。 */
    public void validate(StorageType type, String configJson) {
        try (var client = create(type, configJson)) {
            probe(client);
        }
    }

    private void probe(StorageClient client) {
        var token = UUID.randomUUID().toString();
        var filename = "aaf-storage-validation-" + token + ".txt";
        var expected = ("AAF storage validation: " + token).getBytes(StandardCharsets.UTF_8);
        String key = null;
        RuntimeException originalFailure = null;
        try {
            key = client.upload(new ByteArrayInputStream(expected), filename, VALIDATION_CONTENT_TYPE);
            try (var downloaded = client.download(key)) {
                if (downloaded == null || !Arrays.equals(expected, downloaded.readAllBytes())) {
                    throw new StorageException("存储配置回读内容不一致", null);
                }
            } catch (IOException failure) {
                throw new StorageException("存储配置回读失败", failure);
            }
        } catch (RuntimeException failure) {
            originalFailure = failure;
            throw failure;
        } finally {
            if (key != null) {
                deleteProbeObject(client, key, originalFailure);
            }
        }
    }

    private void deleteProbeObject(
            StorageClient client, String key, RuntimeException originalFailure) {
        try {
            client.delete(key);
        } catch (RuntimeException cleanupFailure) {
            if (originalFailure == null) {
                throw cleanupFailure;
            }
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    public void invalidate(Long configId) {
        var removed = clients.remove(configId);
        if (removed != null) {
            removed.client().close();
        }
    }

    private StorageClient create(FileConfig config) {
        return create(StorageType.valueOf(config.getStorageType()), config.getConfig());
    }

    private StorageClient create(StorageType type, String configJson) {
        var factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("未注册存储类型工厂: " + type);
        }
        return createTyped(factory, configJson);
    }

    private <S extends StorageSpec> StorageClient createTyped(
            StorageClientFactory<S> factory, String configJson) {
        var spec = JsonUtils.parseObject(configJson, factory.specType());
        return factory.create(spec, credentialProvider);
    }

    private String signature(FileConfig config) {
        return "%s:%s:%s"
                .formatted(config.getStorageType(), config.getVersion(), config.getConfig());
    }

    private record RegisteredClient(String signature, StorageClient client) {}
}
