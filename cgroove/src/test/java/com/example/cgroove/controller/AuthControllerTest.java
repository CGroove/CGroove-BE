package com.example.cgroove.controller;

import com.example.cgroove.config.FileProperties;
import com.example.cgroove.config.WebConfig;
import com.example.cgroove.dto.auth.AuthResponse;
import com.example.cgroove.dto.auth.LoginRequest;
import com.example.cgroove.dto.auth.SignupRequest;
import com.example.cgroove.dto.user.UserResponse;
import com.example.cgroove.security.JwtFilter;
import com.example.cgroove.security.JwtUtil;
import com.example.cgroove.service.AuthService;
import com.example.cgroove.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class)
        }
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private FileProperties fileProperties;

    @Test
    @DisplayName("회원가입 성공")
    @WithMockUser
    void signup_Success() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile("profileImage", "test.jpg", "image/jpeg", "content".getBytes());
        SignupRequest request = new SignupRequest("test@email.com", "password1234!!!", "nick");
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

        UserResponse userResponse = new UserResponse(1L, "test@email.com", "nick", "path/img.jpg", null);
        AuthResponse authResponse = new AuthResponse(userResponse, "");

        given(authService.signup(any(SignupRequest.class), any())).willReturn(authResponse);

        // when & then
        mockMvc.perform(multipart("/auth/signup")
                        .file(image)
                        .file(requestPart)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입 성공"));
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 존재하는 이메일/닉네임 (Conflict)")
    @WithMockUser
    void signup_Fail_Conflict() throws Exception {
        // given
        SignupRequest request = new SignupRequest("duplicate@test.com", "password1234", "nick");
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

        given(authService.signup(any(SignupRequest.class), any()))
                .willThrow(new com.example.cgroove.exception.ConflictException("이미 사용 중인 이메일입니다"));

        // when & then
        mockMvc.perform(multipart("/auth/signup")
                        .file(requestPart)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("이미 사용 중인 이메일입니다"));
    }

    @Test
    @DisplayName("로그인 성공")
    @WithMockUser
    void login_Success() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "pw");
        AuthResponse response = new AuthResponse(new UserResponse(1L, "e", "n", null, null), "token");

        given(authService.login(any(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("token"));
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치 (Unauthorized)")
    @WithMockUser
    void login_Fail_Unauthorized() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@email.com", "wrong-pw");
        given(authService.login(any(), any()))
                .willThrow(new com.example.cgroove.exception.AuthException("비밀번호가 일치하지 않습니다"));

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("비밀번호가 일치하지 않습니다"));
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 쿠키값 읽기")
    @WithMockUser
    void refresh_Success() throws Exception {
        // given
        String refreshToken = "valid-refresh-token";
        AuthResponse response = new AuthResponse(null, "new-access-token");

        given(authService.refresh(refreshToken)).willReturn(response);

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 유효하지 않은 리프레시 토큰")
    @WithMockUser
    void refresh_Fail_InvalidToken() throws Exception {
        // given
        String invalidToken = "invalid-token";
        given(authService.refresh(invalidToken))
                .willThrow(new com.example.cgroove.exception.AuthException("유효하지 않은 refreshToken"));

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", invalidToken))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("유효하지 않은 refreshToken"));
    }


    @Test
    @DisplayName("토큰 재발급 실패 - 쿠키 없음")
    @WithMockUser
    void refresh_Fail_NoCookie() throws Exception {
        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("refreshToken")));
    }

    @Test
    @DisplayName("로그아웃 성공")
    @WithMockUser
    void logout_Success() throws Exception {
        // when & then
        mockMvc.perform(post("/auth/logout")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃 성공"));

        verify(authService).logout(any());
    }
}