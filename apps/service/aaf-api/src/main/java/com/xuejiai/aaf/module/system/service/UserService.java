package com.xuejiai.aaf.module.system.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.util.NicknameGenerator;
import com.xuejiai.aaf.module.system.domain.User;
import com.xuejiai.aaf.module.system.repository.UserRepository;
import com.xuejiai.aaf.module.system.vo.UserCreateReqVO;
import com.xuejiai.aaf.module.system.vo.UserRespVO;
import com.xuejiai.aaf.module.system.vo.UserUpdateReqVO;

import lombok.RequiredArgsConstructor;

/** 用户管理业务逻辑。 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 创建用户 */
    @Transactional
    public UserRespVO create(UserCreateReqVO request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() != null ? request.nickname() : NicknameGenerator.generate());
        userRepository.save(user);
        return toRespVO(user);
    }

    /** 查询用户详情 */
    public UserRespVO getById(Long id) {
        User user =
                userRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "用户不存在"));
        return toRespVO(user);
    }

    /** 分页查询 */
    public PageResult<UserRespVO> page(PageParam param) {
        PageRequest pageRequest =
                PageRequest.of(param.pageNo() - 1, param.pageSize(), Sort.by("id").descending());
        Page<User> page = userRepository.findAll(pageRequest);
        return new PageResult<>(
                page.getContent().stream().map(this::toRespVO).toList(),
                page.getTotalElements());
    }

    /** 更新用户 */
    @Transactional
    public UserRespVO update(Long id, UserUpdateReqVO request) {
        User user =
                userRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "用户不存在"));
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userRepository.save(user);
        return toRespVO(user);
    }

    /** 删除用户（@SoftDelete 自动处理逻辑删除） */
    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "用户不存在");
        }
        userRepository.deleteById(id);
    }

    private UserRespVO toRespVO(User user) {
        return new UserRespVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus(),
                user.getCreateTime(),
                user.getUpdateTime());
    }
}
