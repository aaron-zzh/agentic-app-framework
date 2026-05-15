package com.xuejiai.aaf.module.system.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.system.domain.User;
import com.xuejiai.aaf.module.system.repository.UserRepository;
import com.xuejiai.aaf.module.system.vo.SearchResultVO;
import com.xuejiai.aaf.module.system.vo.SearchResultVO.SearchItem;

import lombok.RequiredArgsConstructor;

/**
 * 全局搜索服务。当前支持 User 实体，后续新增实体注册搜索提供者即可扩展。
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final UserRepository userRepository;

    /** 跨实体搜索。 */
    public List<SearchResultVO> search(String keyword, String entities, int limit) {
        var results = new ArrayList<SearchResultVO>();
        if ("all".equals(entities) || entities.contains("user")) {
            results.add(searchUsers(keyword, limit));
        }
        // 后续新增实体在此扩展
        return results;
    }

    private SearchResultVO searchUsers(String keyword, int limit) {
        Specification<User> spec = (root, query, cb) -> cb.or(
                cb.like(root.get("username"), "%" + keyword + "%"),
                cb.like(root.get("nickname"), "%" + keyword + "%"));
        var page = userRepository.findAll(spec, PageRequest.of(0, limit));
        var items = page.getContent().stream()
                .map(u -> new SearchItem(u.getId(), u.getUsername(), u.getNickname()))
                .toList();
        return new SearchResultVO("user", "用户", items);
    }
}
