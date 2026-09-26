package com.example.cgroove.integration;

import com.example.cgroove.dto.auth.LoginRequest;
import com.example.cgroove.dto.auth.SignupRequest;
import com.example.cgroove.dto.club.ClubCreateRequest;
import com.example.cgroove.enums.ClubType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 필터 체인을 켠 상태(addFilters 기본값)로 인증 흐름을 검증한다.
 * 컨트롤러 단위 테스트는 필터를 끄고 실행되므로 URL 권한 설정 오류를 잡지 못한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Access Token 없이 refresh 쿠키만으로 토큰을 재발급한다")
    void refresh_WithoutAccessToken() throws Exception {
        // given
        signup("refresh@test.com", "refresher");
        MvcResult login = login("refresh@test.com", PASSWORD);

        Cookie refreshCookie = login.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        // when: Authorization 헤더 없이 (Access Token 만료 상황)
        MvcResult refreshed = mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        // then: 새 Access Token으로 보호된 API에 접근 가능
        String newAccessToken = readData(refreshed).get("accessToken").asText();
        assertThat(newAccessToken).isNotBlank();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("refresh@test.com"));
    }

    @Test
    @DisplayName("Access Token 없이 보호된 API를 호출하면 401")
    void protectedApi_WithoutAccessToken_Returns401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효하지 않은 refresh 쿠키로 재발급하면 401")
    void refresh_WithInvalidCookie_Returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", "invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("동아리 관리 권한이 없으면 401이 아닌 403 (로그아웃으로 이어지지 않음)")
    void clubManagement_WithoutPermission_Returns403() throws Exception {
        // given: 리더가 동아리를 만들고, 다른 회원이 가입 신청(PENDING · MEMBER)
        signup("leader@test.com", "leader");
        String leaderToken = accessToken(login("leader@test.com", PASSWORD));
        long clubId = createClub(leaderToken);

        signup("member@test.com", "member");
        String memberToken = accessToken(login("member@test.com", PASSWORD));
        mockMvc.perform(post("/clubs/{clubId}/apply", clubId).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isCreated());

        // when & then: 관리자 전용 API 호출
        mockMvc.perform(get("/clubs/{clubId}/applications", clubId).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/clubs/{clubId}/applications", clubId).header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 401 응답 (가입 여부 노출 방지)")
    void login_UnknownEmailAndWrongPassword_SameResponse() throws Exception {
        // given
        signup("exists@test.com", "exists");

        // when
        String unknownEmail = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nobody@test.com", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("exists@test.com", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // then
        assertThat(objectMapper.readTree(unknownEmail).get("detail"))
                .isEqualTo(objectMapper.readTree(wrongPassword).get("detail"));
    }

    private void signup(String email, String nickname) throws Exception {
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(new SignupRequest(email, PASSWORD, nickname)));

        mockMvc.perform(multipart("/auth/signup").file(requestPart))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private long createClub(String accessToken) throws Exception {
        ClubCreateRequest request = ClubCreateRequest.builder()
                .clubName("Test Crew")
                .clubType(ClubType.CREW)
                .intro("intro")
                .build();
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

        MvcResult result = mockMvc.perform(multipart("/clubs")
                        .file(requestPart)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn();
        return readData(result).get("clubId").asLong();
    }

    private String accessToken(MvcResult loginResult) throws Exception {
        return readData(loginResult).get("accessToken").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
