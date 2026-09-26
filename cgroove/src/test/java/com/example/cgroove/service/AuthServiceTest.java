package com.example.cgroove.service;

import com.example.cgroove.dto.auth.AuthResponse;
import com.example.cgroove.dto.auth.LoginRequest;
import com.example.cgroove.dto.auth.SignupRequest;
import com.example.cgroove.dto.user.UserResponse;
import com.example.cgroove.entity.User;
import com.example.cgroove.exception.AuthException;
import com.example.cgroove.security.CookieUtil;
import com.example.cgroove.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserService userService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private CookieUtil cookieUtil;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private HttpServletResponse response;

    @Test
    @DisplayName("회원가입 성공")
    void signup_Success() {
        // given
        SignupRequest request = new SignupRequest("test@email.com", "pw", "nick");
        UserResponse userResponse = new UserResponse(1L, "test@email.com", "nick", null, null);

        given(userService.createUser(request.getEmail(), request.getPassword(), request.getNickname(), null))
                .willReturn(userResponse);

        // when
        AuthResponse result = authService.signup(request, null);

        // then
        assertThat(result.userResponse().email()).isEqualTo("test@email.com");
        verify(userService).createUser(any(), any(), any(), any());
    }

    @Test
    @DisplayName("로그인 성공 - 토큰 발급 및 쿠키 설정")
    void login_Success() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pw");
        User user = User.builder().userId(1L).email("test@email.com").nickname("nick").build();

        given(userService.findOptionalByEmail(request.getEmail())).willReturn(Optional.of(user));
        given(userService.matchesPassword(user, request.getPassword())).willReturn(true);

        given(jwtUtil.generateAccessToken(1L)).willReturn("access-token");
        given(jwtUtil.generateRefreshToken(1L)).willReturn("refresh-token");

        // when
        AuthResponse result = authService.login(request, response);

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(cookieUtil).setRefreshTokenCookie(response, "refresh-token");
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_Fail_PasswordMismatch() {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "wrong-pw");
        User user = User.builder().userId(1L).build();

        given(userService.findOptionalByEmail(request.getEmail())).willReturn(Optional.of(user));
        given(userService.matchesPassword(user, "wrong-pw")).willReturn(false);

        // when & then
        AuthException ex = assertThrows(AuthException.class, () -> authService.login(request, response));
        assertThat(ex.getMessage()).isEqualTo("이메일 또는 비밀번호가 일치하지 않습니다");
    }

    @Test
    @DisplayName("로그인 실패 - 없는 이메일도 비밀번호 불일치와 같은 예외 · 메시지 (가입 여부 노출 방지)")
    void login_Fail_UnknownEmail_SameResponseAsWrongPassword() {
        // given
        LoginRequest request = new LoginRequest("nobody@email.com", "pw");
        given(userService.findOptionalByEmail(request.getEmail())).willReturn(Optional.empty());

        // when & then
        AuthException ex = assertThrows(AuthException.class, () -> authService.login(request, response));
        assertThat(ex.getMessage()).isEqualTo("이메일 또는 비밀번호가 일치하지 않습니다");
        verify(userService, never()).matchesPassword(any(), any());
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void refresh_Success() {
        // given
        String refreshToken = "valid-refresh-token";
        Long userId = 1L;
        User user = User.builder().userId(userId).email("test@email.com").nickname("nick").build();

        given(jwtUtil.validateToken(refreshToken)).willReturn(true);
        given(jwtUtil.isRefreshToken(refreshToken)).willReturn(true);
        given(jwtUtil.getUserId(refreshToken)).willReturn(userId);
        given(userService.findByUserId(userId)).willReturn(user);
        given(jwtUtil.generateAccessToken(userId)).willReturn("new-access-token");

        // when
        AuthResponse result = authService.refresh(refreshToken);

        // then
        assertThat(result.accessToken()).isEqualTo("new-access-token");
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 유효하지 않은 토큰")
    void refresh_Fail_InvalidToken() {
        // given
        String refreshToken = "invalid-token";
        given(jwtUtil.validateToken(refreshToken)).willReturn(false);

        // when & then
        assertThrows(AuthException.class, () -> authService.refresh(refreshToken));
    }
}