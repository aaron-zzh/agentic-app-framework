package com.xuejiai.aaf.module.customerservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.customerservice.model.entity.WecomKfAccountBinding;

/** 企微客服账号绑定 Repository */
public interface WecomKfAccountBindingRepository
        extends JpaRepository<WecomKfAccountBinding, Long> {

    Optional<WecomKfAccountBinding> findByOpenKfId(String openKfId);

    Optional<WecomKfAccountBinding> findByOpenKfIdAndEnabledTrue(String openKfId);

    /** 按归属者列出绑定——绑定是个人资源，不跨用户可见。 */
    List<WecomKfAccountBinding> findByOwnerIdOrderByIdAsc(Long ownerId);

    /** 按归属者读取单条绑定，越权与不存在统一按不存在处理。 */
    Optional<WecomKfAccountBinding> findByIdAndOwnerId(Long id, Long ownerId);
}
