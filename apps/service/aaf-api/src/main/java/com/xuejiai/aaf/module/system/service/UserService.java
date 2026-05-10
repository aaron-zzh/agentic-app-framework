package com.xuejiai.aaf.module.system.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
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
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<User> spec = buildSpec(req);
        Page<User> page = userRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    private Specification<User> buildSpec(UserPageDTO req) {
        return SpecificationBuilder.<User>builder()
                .likeIfPresent("username", req.getUsername())
                .likeIfPresent("nickname", req.getNickname())
                .eqIfPresent("status", req.getStatus())
                .build();
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

    /** 批量删除用户 */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        userRepository.deleteAllByIdInBatch(ids);
    }

    /** 修改用户状态 */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        var user = requireUser(id);
        user.setStatus(status);
        userRepository.save(user);
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

    // ==================== 查询方法 ====================

    /** 批量查询用户 */
    public List<User> getUserList(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : userRepository.findAllById(ids);
    }

    /** 批量查询用户并转为 Map */
    public Map<Long, UserVO> getUserMap(Collection<Long> ids) {
        return getUserList(ids).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, this::toVO));
    }

    /** 查询指定状态的用户列表 */
    public List<UserSimpleVO> getSimpleListByStatus(Integer status) {
        return userRepository.findSimpleListByStatus(status);
    }

    // ==================== P2+ 占位（待对应模块就绪后实现） ====================

    // TODO P2: 用户注册（开放注册场景，区别于管理员创建）
    // public UserVO register(UserRegisterDTO request) { }

    // TODO P2: 更新最后登录信息（需扩展 User 实体加 lastLoginIp/lastLoginTime）
    // public void updateLoginInfo(Long id, String loginIp) { }

    // TODO P2: 修改个人信息（头像、邮箱、手机号等，需扩展字段）
    // public void updateProfile(Long id, UserProfileUpdateDTO request) { }

    // TODO P2: 校验用户有效性（存在且启用，供其他模块引用时调用）
    // public void validateUserList(Collection<Long> ids) { }

    // TODO P3: 导入用户（需引入 Excel 库）
    // public UserImportResultVO importUsers(List<UserImportDTO> users, boolean updateSupport) { }

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
