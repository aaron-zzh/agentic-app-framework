package com.xuejiai.aaf.module.customerservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.customerservice.model.entity.WecomKfAccountBinding;

/** 企微客服账号绑定 Repository */
public interface WecomKfAccountBindingRepository
        extends JpaRepository<WecomKfAccountBinding, Long> {

    Optional<WecomKfAccountBinding> findByOpenKfId(String openKfId);

    Optional<WecomKfAccountBinding> findByOpenKfIdAndEnabledTrue(String openKfId);
}
