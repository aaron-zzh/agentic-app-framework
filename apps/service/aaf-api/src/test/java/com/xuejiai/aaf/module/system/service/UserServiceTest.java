package com.xuejiai.aaf.module.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.system.domain.User;
import com.xuejiai.aaf.module.system.repository.UserRepository;
import com.xuejiai.aaf.module.system.vo.UserCreateReqVO;
import com.xuejiai.aaf.module.system.vo.UserRespVO;
import com.xuejiai.aaf.module.system.vo.UserUpdateReqVO;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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
        user.setStatus((short) 1);
    }

    @Test
    void create_成功() {
        var request = new UserCreateReqVO("newuser", "123456", "新用户");
        when(userRepository.existsByUsernameAndDeletedFalse("newuser")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        UserRespVO response = userService.create(request);

        assertThat(response.username()).isEqualTo("newuser");
        assertThat(response.nickname()).isEqualTo("新用户");
        verify(passwordEncoder).encode("123456");
    }

    @Test
    void create_用户名已存在_抛异常() {
        var request = new UserCreateReqVO("existing", "123456", "已存在");
        when(userRepository.existsByUsernameAndDeletedFalse("existing")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void getById_存在() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

        UserRespVO response = userService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("testuser");
    }

    @Test
    void getById_不存在_抛异常() {
        when(userRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void page_返回分页结果() {
        var page = new PageImpl<>(List.of(user));
        when(userRepository.findAllByDeletedFalse(any(Pageable.class))).thenReturn(page);

        PageResult<UserRespVO> result = userService.page(new PageParam(1, 10));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list()).hasSize(1);
        assertThat(result.list().getFirst().username()).isEqualTo("testuser");
    }

    @Test
    void update_成功() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UserRespVO response = userService.update(1L, new UserUpdateReqVO("新昵称", (short) 0));

        assertThat(response.nickname()).isEqualTo("新昵称");
        assertThat(response.status()).isEqualTo((short) 0);
    }

    @Test
    void delete_逻辑删除() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.delete(1L);

        assertThat(user.getDeleted()).isTrue();
        assertThat(user.getDeleteTime()).isNotNull();
        verify(userRepository).save(user);
    }
}
