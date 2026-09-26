package com.example.cgroove.controller;

import com.example.cgroove.config.FileProperties;
import com.example.cgroove.config.WebConfig;
import com.example.cgroove.dto.user.PasswordUpdateRequest;
import com.example.cgroove.dto.user.UserResponse;
import com.example.cgroove.dto.user.UserUpdateRequest;
import com.example.cgroove.security.JwtFilter;
import com.example.cgroove.security.JwtUtil;
import com.example.cgroove.security.WithCustomMockUser;
import com.example.cgroove.service.FileStorageService;
import com.example.cgroove.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class)
        }
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private FileProperties fileProperties;

    @Test
    @DisplayName("내 정보 조회 성공")
    @WithCustomMockUser(userId = 1L)
    void getMe_Success() throws Exception {
        // given
        UserResponse response = new UserResponse(1L, "email", "nickname", "img", null);
        given(userService.getUser(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("nickname"));
    }

    @Test
    @DisplayName("내 프로필 수정 성공 (이미지 포함)")
    @WithCustomMockUser(userId = 1L)
    void updateUser_Success() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile("profileImage", "test.jpg", "image/jpeg", "content".getBytes());
        String nickname = "NewNick";
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(new UserUpdateRequest(nickname)));

        UserResponse response = new UserResponse(1L, "email", nickname, "path/to/img.jpg", null);
        given(userService.updateUser(eq(1L), any(UserUpdateRequest.class), any())).willReturn(response);

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/users")
                        .file(image)
                        .file(requestPart)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.profileImage").value("path/to/img.jpg"));
    }

    @Test
    @DisplayName("비밀번호 수정 성공")
    @WithCustomMockUser(userId = 1L)
    void updatePassword_Success() throws Exception {
        // given
        PasswordUpdateRequest request = new PasswordUpdateRequest("newPassword123!");
        UserResponse response = new UserResponse(1L, "email", "nickname", "img", null);

        given(userService.updatePassword(eq(1L), any(PasswordUpdateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호 수정 성공"));
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    @WithCustomMockUser(userId = 1L)
    void deleteUser_Success() throws Exception {
        // when & then
        mockMvc.perform(delete("/users")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
    }

    @Test
    @DisplayName("회원 정보 조회 성공")
    @WithCustomMockUser
    void getUser_Success() throws Exception {
        // given
        UserResponse response = new UserResponse(2L, "other@email.com", "OtherUser", "img.jpg", null);
        given(userService.getUser(2L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/{userId}", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 정보 조회 성공"))
                .andExpect(jsonPath("$.data.userId").value(2L))
                .andExpect(jsonPath("$.data.nickname").value("OtherUser"));
    }

    @Test
    @DisplayName("프로필 이미지 삭제 성공")
    @WithCustomMockUser(userId = 1L)
    void deleteProfileImage_Success() throws Exception {
        // given
        UserResponse response = new UserResponse(1L, "email", "nickname", null, null);
        given(userService.deleteProfileImage(1L)).willReturn(response);

        // when & then
        mockMvc.perform(delete("/users/profile-image")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프로필 이미지 삭제 성공"))
                .andExpect(jsonPath("$.data.profileImage").isEmpty());
    }
}