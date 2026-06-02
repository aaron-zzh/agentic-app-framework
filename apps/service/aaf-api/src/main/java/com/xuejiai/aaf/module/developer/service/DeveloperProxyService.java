package com.xuejiai.aaf.module.developer.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.developer.domain.DeveloperProxy;
import com.xuejiai.aaf.module.developer.repository.DeveloperAccountRepository;
import com.xuejiai.aaf.module.developer.repository.DeveloperProxyRepository;
import com.xuejiai.aaf.module.developer.vo.DeveloperProxyCreateDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeveloperProxyService {

    private final DeveloperProxyRepository proxyRepository;
    private final DeveloperAccountRepository accountRepository;

    @Transactional
    public DeveloperProxy create(Long parentDeveloperId, DeveloperProxyCreateDTO dto) {
        var parent =
                accountRepository
                        .findById(parentDeveloperId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "上级开发者不存在"));
        if (!Boolean.TRUE.equals(parent.getAllowSubProxy())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "当前开发者未授权子代理能力");
        }
        var depth = 1;
        if (depth > parent.getMaxProxyDepth()) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "子代理层级超过授权上限");
        }
        accountRepository
                .findById(dto.childDeveloperId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "下级开发者不存在"));
        var proxy = new DeveloperProxy();
        proxy.setParentDeveloperId(parentDeveloperId);
        proxy.setChildDeveloperId(dto.childDeveloperId());
        proxy.setProxyDepth(depth);
        proxy.setTokenLimit(dto.tokenLimit());
        proxy.setAllowSubProxy(Boolean.TRUE.equals(dto.allowSubProxy()));
        return proxyRepository.save(proxy);
    }

    @Transactional(readOnly = true)
    public List<DeveloperProxy> list(Long parentDeveloperId) {
        return proxyRepository.findByParentDeveloperId(parentDeveloperId);
    }
}
