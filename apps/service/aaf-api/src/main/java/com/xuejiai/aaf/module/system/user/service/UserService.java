package com.xuejiai.aaf.module.system.user.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.USER_ADMIN_DELETE_FORBIDDEN;
import static com.xuejiai.aaf.module.system.enums.LogRecordConstants.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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
import com.xuejiai.aaf.framework.bizlog.annotation.LogRecord;
import com.xuejiai.aaf.framework.bizlog.context.LogRecordContext;
import com.xuejiai.aaf.framework.bizlog.service.impl.DiffParseFunction;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinitionRepository;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.mapper.UserConvert;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.module.system.user.vo.UserChangePasswordDTO;
import com.xuejiai.aaf.module.system.user.vo.UserCreateDTO;
import com.xuejiai.aaf.module.system.user.vo.UserExportVO;
import com.xuejiai.aaf.module.system.user.vo.UserImportVO;
import com.xuejiai.aaf.module.system.user.vo.UserPageDTO;
import com.xuejiai.aaf.module.system.user.vo.UserProfileUpdateDTO;
import com.xuejiai.aaf.module.system.user.vo.UserProfileVO;
import com.xuejiai.aaf.module.system.user.vo.UserSimpleVO;
import com.xuejiai.aaf.module.system.user.vo.UserUpdateDTO;
import com.xuejiai.aaf.module.system.user.vo.UserVO;
import com.xuejiai.aaf.util.ImportExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户管理业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final jakarta.validation.Validator validator;
    private final SystemConfigService systemConfigService;
    private final AssistantDefinitionRepository assistantRepo;

    @Value("${aaf.assistant.auto-create-on-register:true}")
    private boolean autoCreateAssistant;

    /**
     * 创建用户
     *
     * @param request 创建用户请求
     * @return 用户视图对象
     */
    @Transactional
    @LogRecord(
            type = SYSTEM_USER_TYPE,
            subType = SYSTEM_USER_CREATE_SUB_TYPE,
            bizNo = "{{#user.id}}",
            success = SYSTEM_USER_CREATE_SUCCESS)
    public UserVO create(UserCreateDTO request) {
        validateUsernameUnique(null, request.username());
        var user = new User();
        user.setUsername(request.username());
        user.changePassword(passwordEncoder, request.password());
        user.setNickname(
                request.nickname() != null ? request.nickname() : NicknameGenerator.generate());
        userRepository.save(user);
        // 新增后 id 才有值，通过 LogRecordContext 注入供模板引用
        LogRecordContext.putVariable("user", user);
        if (autoCreateAssistant) {
            initDefaultAssistant(user.getId());
        }
        return toVO(user);
    }

    /**
     * 查询用户详情
     *
     * @param id 用户 ID
     * @return 用户视图对象
     */
    public UserVO getById(Long id) {
        return toVO(requireUser(id));
    }

    /**
     * 分页查询用户
     *
     * @param req 分页查询参数
     * @return 分页结果
     */
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

    /**
     * 更新用户
     *
     * @param id 用户 ID
     * @param request 更新请求
     * @return 更新后的用户视图对象
     */
    @Transactional
    @LogRecord(
            type = SYSTEM_USER_TYPE,
            subType = SYSTEM_USER_UPDATE_SUB_TYPE,
            bizNo = "{{#id}}",
            success = SYSTEM_USER_UPDATE_SUCCESS)
    public UserVO update(Long id, UserUpdateDTO request) {
        var user = requireUser(id);
        // 保存旧对象供 {_DIFF{#updateReqVO}} 比较（executeBefore 函数在执行前取快照）
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, user);
        LogRecordContext.putVariable("user", user);
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userRepository.save(user);
        LogRecordContext.putVariable("updateReqVO", request);
        return toVO(user);
    }

    /**
     * 查询简要列表（下拉选择场景）
     *
     * @return 用户简要列表
     */
    public List<UserSimpleVO> getSimpleList() {
        return userRepository.findSimpleList();
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     */
    @Transactional
    @LogRecord(
            type = SYSTEM_USER_TYPE,
            subType = SYSTEM_USER_DELETE_SUB_TYPE,
            bizNo = "{{#id}}",
            success = SYSTEM_USER_DELETE_SUCCESS,
            fail = "删除用户 ID={{#id}} 失败：{{#_errorMsg}}")
    public void delete(Long id) {
        validateNotAdmin(id);
        var user =
                userRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "用户不存在"));
        LogRecordContext.putVariable("user", user);
        userRepository.delete(user);
    }

    /**
     * 批量删除用户
     *
     * @param ids 用户 ID 列表
     */
    @Transactional
    @LogRecord(
            type = SYSTEM_USER_TYPE,
            subType = SYSTEM_USER_DELETE_BATCH_SUB_TYPE,
            bizNo = "batch",
            success = SYSTEM_USER_DELETE_BATCH_SUCCESS)
    public void deleteBatch(List<Long> ids) {
        ids.forEach(this::validateNotAdmin);
        userRepository.deleteAllById(ids);
    }

    /**
     * 修改用户状态
     *
     * @param id 用户 ID
     * @param status 目标状态
     */
    @Transactional
    @LogRecord(
            type = SYSTEM_USER_TYPE,
            subType = SYSTEM_USER_UPDATE_STATUS_SUB_TYPE,
            bizNo = "{{#id}}",
            success = SYSTEM_USER_UPDATE_STATUS_SUCCESS)
    public void updateStatus(Long id, Integer status) {
        var user = requireUser(id);
        LogRecordContext.putVariable("user", user);
        user.setStatus(status);
        userRepository.save(user);
    }

    /**
     * 修改密码（用户自己操作，需验证旧密码）
     *
     * @param id 用户 ID
     * @param request 修改密码请求
     */
    @Transactional
    public void changePassword(Long id, UserChangePasswordDTO request) {
        var user = requireUser(id);
        if (!user.checkPassword(passwordEncoder, request.oldPassword())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "旧密码不正确");
        }
        user.changePassword(passwordEncoder, request.newPassword());
        userRepository.save(user);
    }

    /**
     * 重置密码（管理员操作）
     *
     * @param id 用户 ID
     * @param newPassword 新密码
     */
    @Transactional
    @LogRecord(
            type = SYSTEM_USER_TYPE,
            subType = SYSTEM_USER_UPDATE_PASSWORD_SUB_TYPE,
            bizNo = "{{#id}}",
            success = SYSTEM_USER_UPDATE_PASSWORD_SUCCESS)
    public void resetPassword(Long id, String newPassword) {
        var user = requireUser(id);
        LogRecordContext.putVariable("user", user);
        user.changePassword(passwordEncoder, newPassword);
        userRepository.save(user);
    }

    // ==================== 导入 ====================

    /**
     * 批量导入用户。先全部校验，通过后分批入库
     *
     * @param list 导入数据列表
     * @param updateSupport 是否支持更新已存在用户
     * @return 导入结果
     */
    @Transactional
    public ImportExecutor.ImportResult importUsers(List<UserImportVO> list, boolean updateSupport) {
        return ImportExecutor.<UserImportVO>builder()
                .validator(validator)
                .data(list)
                .duplicateChecker(
                        row -> {
                            if (!updateSupport
                                    && userRepository.existsByUsername(row.getUsername())) {
                                return "用户名 " + row.getUsername() + " 已存在";
                            }
                            return null;
                        })
                .consumer(batch -> batch.forEach(this::saveImportRow))
                .build()
                .execute();
    }

    private void saveImportRow(UserImportVO row) {
        var existing = userRepository.findByUsername(row.getUsername());
        if (existing.isPresent()) {
            var user = existing.get();
            if (row.getNickname() != null) user.setNickname(row.getNickname());
            if (row.getStatus() != null) user.setStatus(row.getStatus());
            userRepository.save(user);
        } else {
            var user = new User();
            user.setUsername(row.getUsername());
            user.setNickname(row.getNickname() != null ? row.getNickname() : row.getUsername());
            String pwd =
                    row.getPassword() != null
                            ? row.getPassword()
                            : systemConfigService.getString("user.default_password", "123456");
            user.changePassword(passwordEncoder, pwd);
            if (row.getStatus() != null) user.setStatus(row.getStatus());
            userRepository.save(user);
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 导出用户列表（不分页，应用筛选条件）
     *
     * @param req 筛选条件
     * @return 导出数据列表
     */
    public List<UserExportVO> listForExport(UserPageDTO req) {
        Specification<User> spec = buildSpec(req);
        Sort sort = req.buildSort();
        if (!sort.isSorted()) {
            sort = Sort.by("id").descending();
        }
        return userRepository.findAll(spec, sort).stream()
                .map(
                        u ->
                                new UserExportVO(
                                        u.getId(),
                                        u.getUsername(),
                                        u.getNickname(),
                                        u.getStatus(),
                                        u.getCreateTime()))
                .toList();
    }

    /**
     * 批量查询用户
     *
     * @param ids 用户 ID 集合
     * @return 用户列表
     */
    public List<User> getUserList(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : userRepository.findAllById(ids);
    }

    /**
     * 批量查询用户并转为 Map
     *
     * @param ids 用户 ID 集合
     * @return ID → UserVO 映射
     */
    public Map<Long, UserVO> getUserMap(Collection<Long> ids) {
        return getUserList(ids).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, this::toVO));
    }

    /**
     * 查询指定状态的用户列表
     *
     * @param status 用户状态
     * @return 用户简要列表
     */
    public List<UserSimpleVO> getSimpleListByStatus(Integer status) {
        return userRepository.findSimpleListByStatus(status);
    }

    // ==================== 个人中心 ====================

    /**
     * 获取用户个人信息
     *
     * @param userId 用户 ID
     * @return 个人信息
     */
    public UserProfileVO getProfile(Long userId) {
        var user = requireUser(userId);
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getCreateTime());
    }

    /**
     * 修改个人信息
     *
     * @param userId 用户 ID
     * @param req 修改请求
     * @return 更新后的个人信息
     */
    @Transactional
    public UserProfileVO updateProfile(Long userId, UserProfileUpdateDTO req) {
        var user = requireUser(userId);
        if (req.nickname() != null) user.setNickname(req.nickname());
        if (req.avatar() != null) user.setAvatar(req.avatar());
        if (req.email() != null) user.setEmail(req.email());
        if (req.phone() != null) user.setPhone(req.phone());
        userRepository.save(user);
        return getProfile(userId);
    }

    // ==================== 内部方法 ====================

    private static final Long ADMIN_USER_ID = 1L;

    private void validateNotAdmin(Long id) {
        if (ADMIN_USER_ID.equals(id)) {
            throw exception(USER_ADMIN_DELETE_FORBIDDEN);
        }
    }

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

    /** 为新用户克隆 default-assistant（以 userId=0 的第一个 active assistant 为模板） */
    private void initDefaultAssistant(Long userId) {
        try {
            var defaultDef =
                    assistantRepo.findByUserIdAndStatus(0L, "active").stream()
                            .findFirst()
                            .orElse(null);
            if (defaultDef == null) return;
            var assistant = new AssistantDefinition();
            assistant.setUserId(userId);
            assistant.setPersonaId(defaultDef.getPersonaId());
            assistant.setDefaultRoleId(defaultDef.getDefaultRoleId());
            assistant.setMemoryStrategy(defaultDef.getMemoryStrategy());
            assistantRepo.save(assistant);
        } catch (Exception e) {
            log.warn("为用户 {} 自动创建助理失败，跳过: {}", userId, e.getMessage());
        }
    }
}
