package com.xuejiai.aaf.module.system.file.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.storage.StorageClient;
import com.xuejiai.aaf.framework.storage.StorageClientFactory;
import com.xuejiai.aaf.framework.storage.StorageCredentialProvider;
import com.xuejiai.aaf.framework.storage.StorageSpec;
import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;

/** 单实例一期动态存储客户端注册表。 */
@Component
public class StorageClientRegistry {

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

    public void validate(StorageType type, String configJson) {
        var client = create(type, configJson);
        client.close();
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
