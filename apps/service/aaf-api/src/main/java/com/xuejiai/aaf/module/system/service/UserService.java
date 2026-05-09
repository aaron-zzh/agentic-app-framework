package com.xuejiai.aaf.module.system.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.util.NicknameGenerator;
import com.xuejiai.aaf.module.system.domain.User;
import com.xuejiai.aaf.module.system.mapper.UserConvert;
import com.xuejiai.aaf.module.system.repository.UserRepository;
import com.xuejiai.aaf.module.system.vo.UserChangePasswordDTO;
import com.xuejiai.aaf.module.system.vo.UserCreateDTO;
import com.xuejiai.aaf.module.system.vo.UserPageDTO;
import com.xuejiai.aaf.module.system.vo.UserSimpleVO;
import com.xuejiai.aaf.module.system.vo.UserUpdateDTO;
import com.xuejiai.aaf.module.system.vo.UserVO;

import lombok.RequiredArgsConstructor;

/** 用户管理业务逻辑。 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 创建用户 */
    @Transactional
    public UserVO create(UserCreateDTO request) {
        validateUsernameUnique(null, request.username());
        var user = new User();
        user.setUsername(request.username());
        user.changePassword(passwordEncoder, request.password());
        user.setNickname(
                request.nickname() != null ? request.nickname() : NicknameGenerator.generate());
        userRepository.save(user);
        return toVO(user);
    }

    /** 查询用户详情 */
    public UserVO getById(Long id) {
        return toVO(requireUser(id));
    }

    /** 分页查询 */
    public PageResult<UserVO> page(UserPageDTO req) {
        var pageRequest =
                PageRequest.of(req.pageNo() - 1, req.pageSize(), Sort.by("id").descending());
        Page<User> page = userRepository.findAll(pageRequest);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 更新用户 */
    @Transactional
    public UserVO update(Long id, UserUpdateDTO request) {
        var user = requireUser(id);
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userRepository.save(user);
        return toVO(user);
    }

    /** 查询简要列表（下拉选择场景） */
    public List<UserSimpleVO> getSimpleList() {
        return userRepository.findSimpleList();
    }

    /** 删除用户 */
    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "用户不存在");
        }
        userRepository.deleteById(id);
    }

    /** 修改密码（用户自己操作，需验证旧密码） */
    @Transactional
    public void changePassword(Long id, UserChangePasswordDTO request) {
        var user = requireUser(id);
        if (!user.checkPassword(passwordEncoder, request.oldPassword())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "旧密码不正确");
        }
        user.changePassword(passwordEncoder, request.newPassword());
        userRepository.save(user);
    }

    /** 重置密码（管理员操作） */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        var user = requireUser(id);
        user.changePassword(passwordEncoder, newPassword);
        userRepository.save(user);
    }

    // ==================== 内部方法 ====================

    private User requireUser(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "用户不存在"));
    }

    /** 校验用户名唯一（创建时 id=null，更新时传当前 id 排除自身） */
    private void validateUsernameUnique(Long id, String username) {
        userRepository
                .findByUsername(username)
                .ifPresent(
                        existing -> {
                            if (!existing.getId().equals(id)) {
                                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "用户名已存在");
                            }
                        });
    }

    private UserVO toVO(User user) {
        return UserConvert.INSTANCE.toVO(user);
    }
}
