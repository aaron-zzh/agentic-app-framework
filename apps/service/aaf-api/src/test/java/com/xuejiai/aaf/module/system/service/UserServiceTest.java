package com.xuejiai.aaf.module.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.system.domain.User;
import com.xuejiai.aaf.module.system.repository.UserRepository;
import com.xuejiai.aaf.module.system.vo.UserChangePasswordDTO;
import com.xuejiai.aaf.module.system.vo.UserCreateDTO;
import com.xuejiai.aaf.module.system.vo.UserPageDTO;
import com.xuejiai.aaf.module.system.vo.UserUpdateDTO;
import com.xuejiai.aaf.module.system.vo.UserVO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class UserServiceTest extends BaseMockitoUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encoded");
        user.setNickname("测试用户");
        user.setStatus(1);
    }

    @Test
    @DisplayName("Given 用户名不存在 When 创建用户 Then 返回新用户信息")
    void should_create_user_when_username_not_exists() {
        // 准备参数
        var request = new UserCreateDTO("newuser", "123456", "新用户");

        // mock 方法
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            User e = inv.getArgument(0);
                            e.setId(1L);
                            return e;
                        });

        // 调用
        UserVO response = userService.create(request);

        // 断言
        assertThat(response.username()).isEqualTo("newuser");
        assertThat(response.nickname()).isEqualTo("新用户");
    }

    @Test
    @DisplayName("Given 用户名已存在 When 创建用户 Then 抛出业务异常")
    void should_throw_exception_when_username_already_exists() {
        // 准备参数
        var request = new UserCreateDTO("existing", "123456", "已存在");

        // mock 方法
        var existing = new User();
        existing.setId(99L);
        when(userRepository.findByUsername("existing")).thenReturn(Optional.of(existing));

        // 调用 + 断言
        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    @DisplayName("Given 用户存在 When 按 ID 查询 Then 返回用户信息")
    void should_return_user_when_id_exists() {
        // mock 方法
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // 调用
        UserVO response = userService.getById(1L);

        // 断言
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Given 用户不存在 When 按 ID 查询 Then 抛出 NOT_FOUND 异常")
    void should_throw_exception_when_id_not_exists() {
        // mock 方法
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // 调用 + 断言
        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("Given 有用户数据 When 分页查询 Then 返回分页结果")
    void should_return_page_result_when_query_users() {
        // mock 方法
        var page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        // 调用
        PageResult<UserVO> result = userService.page(new UserPageDTO());

        // 断言
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().getFirst().username()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Given 用户存在 When 更新昵称和状态 Then 返回更新后的用户")
    void should_update_user_when_id_exists() {
        // mock 方法
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        // 调用
        UserVO response = userService.update(1L, new UserUpdateDTO("新昵称", 0));

        // 断言
        assertThat(response.nickname()).isEqualTo("新昵称");
        assertThat(response.status()).isEqualTo(0);
    }

    @Test
    @DisplayName("Given 用户存在 When 删除 Then 调用 deleteById")
    void should_delete_user_when_id_exists() {
        // mock 方法
        when(userRepository.existsById(1L)).thenReturn(true);

        // 调用
        userService.delete(1L);

        // 断言
        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Given 旧密码正确 When 修改密码 Then 密码更新成功")
    void should_change_password_when_old_password_correct() {
        // mock 方法
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("new_encoded");
        when(userRepository.save(any())).thenReturn(user);

        // 调用
        userService.changePassword(1L, new UserChangePasswordDTO("oldpass", "newpass"));

        // 断言
        assertThat(user.getPassword()).isEqualTo("new_encoded");
    }

    @Test
    @DisplayName("Given 旧密码错误 When 修改密码 Then 抛出异常")
    void should_throw_when_old_password_incorrect() {
        // mock 方法
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                userService.changePassword(
                                        1L, new UserChangePasswordDTO("wrong", "newpass")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧密码不正确");
    }

    @Test
    @DisplayName("Given 用户存在 When 管理员重置密码 Then 密码更新成功")
    void should_reset_password_when_admin_request() {
        // mock 方法
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("reset123")).thenReturn("reset_encoded");
        when(userRepository.save(any())).thenReturn(user);

        // 调用
        userService.resetPassword(1L, "reset123");

        // 断言
        assertThat(user.getPassword()).isEqualTo("reset_encoded");
    }
}
