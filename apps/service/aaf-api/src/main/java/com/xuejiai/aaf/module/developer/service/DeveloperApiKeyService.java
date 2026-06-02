package com.xuejiai.aaf.module.developer.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.developer.domain.DeveloperApiKey;
import com.xuejiai.aaf.module.developer.repository.DeveloperApiKeyRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperApiKeyCreateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperApiKeyCreateVO;
import com.xuejiai.aaf.module.developer.vo.DeveloperApiKeyVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeveloperApiKeyService {

    public static final String KEY_PREFIX = "aaf_gw_";

    private final DeveloperApiKeyRepository apiKeyRepository;

    @Transactional
    public DeveloperApiKeyCreateVO create(Long developerId, DeveloperApiKeyCreateDTO dto) {
        var rawKey = DeveloperSecurityUtil.randomSecret(KEY_PREFIX, 40);
        var apiKey = new DeveloperApiKey();
        apiKey.setDeveloperId(developerId);
        apiKey.setName(dto.name());
        apiKey.setKeyHash(DeveloperSecurityUtil.sha256(rawKey));
        apiKey.setKeyPrefix(rawKey.substring(0, 14) + "...");
        apiKey.setScopes(
                dto.scopes() == null || dto.scopes().isBlank() ? "gateway:chat" : dto.scopes());
        if (dto.expiresInDays() != null && dto.expiresInDays() > 0) {
            apiKey.setExpiresAt(Instant.now().plusSeconds(dto.expiresInDays() * 86400L));
        }
        apiKeyRepository.save(apiKey);
        return new DeveloperApiKeyCreateVO(
                apiKey.getId(), apiKey.getName(), rawKey, apiKey.getKeyPrefix());
    }

    @Transactional(readOnly = true)
    public List<DeveloperApiKeyVO> list(Long developerId) {
        return apiKeyRepository.findByDeveloperIdOrderByCreateTimeDesc(developerId).stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public DeveloperApiKey verify(String rawKey, String requiredScope) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "无效的开发者 Gateway Key");
        }
        var apiKey =
                apiKeyRepository
                        .findByKeyHashAndEnabledTrue(DeveloperSecurityUtil.sha256(rawKey))
                        .filter(DeveloperApiKey::isValid)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.UNAUTHORIZED,
                                                "开发者 Gateway Key 不存在或已失效"));
        if (requiredScope != null
                && apiKey.getScopes() != null
                && !apiKey.getScopes().contains(requiredScope)
                && !apiKey.getScopes().contains("gateway:*")) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "开发者 Gateway Key scope 不足");
        }
        apiKey.setLastUsedAt(Instant.now());
        return apiKeyRepository.save(apiKey);
    }

    private DeveloperApiKeyVO toVO(DeveloperApiKey key) {
        return new DeveloperApiKeyVO(
                key.getId(),
                key.getName(),
                key.getKeyPrefix(),
                key.getScopes(),
                key.getEnabled(),
                key.getExpiresAt(),
                key.getLastUsedAt(),
                key.getCreateTime());
    }
}
