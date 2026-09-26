package com.example.cgroove.controller;

import com.example.cgroove.config.FileProperties;
import com.example.cgroove.config.WebConfig;
import com.example.cgroove.dto.like.PostLikeResponse;
import com.example.cgroove.dto.post.PostCreateRequest;
import com.example.cgroove.dto.post.PostResponse;
import com.example.cgroove.dto.post.PostUpdateRequest;
import com.example.cgroove.enums.ImageType;
import com.example.cgroove.security.JwtFilter;
import com.example.cgroove.security.JwtUtil;
import com.example.cgroove.security.WithCustomMockUser;
import com.example.cgroove.service.FileStorageService;
import com.example.cgroove.service.PostLikeService;
import com.example.cgroove.service.PostService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PostController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class)
        }
)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private PostLikeService postLikeService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private FileProperties fileProperties;

    // 테스트용 더미 응답 생성
    private PostResponse createMockResponse() {
        return new PostResponse(
                1L, 1L, "User", null, "GLOBAL", null, null,
                "Title", "Content", List.of("tag"), List.of("img.jpg"),
                0L, 0L, false, 3, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("게시글 생성 성공")
    @WithCustomMockUser
    void createPost_Success() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile("images", "test.jpg", "image/jpeg", "data".getBytes());
        given(fileStorageService.saveImage(any(), eq(ImageType.POST))).willReturn("path/img.jpg");
        given(postService.createPost(any(), any(PostCreateRequest.class))).willReturn(createMockResponse());

        // when & then
        PostCreateRequest request = PostCreateRequest.builder()
                .scope("GLOBAL").title("Title").content("Content").build();
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/posts")
                        .file(image)
                        .file(requestPart)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("게시물 생성 성공"));
    }

    @Test
    @DisplayName("게시글 조회 성공")
    @WithCustomMockUser
    void getPost_Success() throws Exception {
        given(postService.getPost(eq(1L), any())).willReturn(createMockResponse());

        mockMvc.perform(get("/posts/{postId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(1L));
    }

    @Test
    @DisplayName("전체 게시글 조회 성공")
    @WithCustomMockUser
    void getPosts_Success() throws Exception {
        given(postService.getPosts(any())).willReturn(List.of(createMockResponse()));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].postId").value(1L));
    }

    @Test
    @DisplayName("Hot Groove 조회 성공")
    @WithCustomMockUser
    void getHotPosts_Success() throws Exception {
        given(postService.getHotPosts(any())).willReturn(List.of(createMockResponse()));

        mockMvc.perform(get("/posts/hot"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("내 게시글 수정 성공")
    @WithCustomMockUser
    void updatePost_Success() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile("images", "new.jpg", "image/jpeg", "data".getBytes());
        given(fileStorageService.saveImage(any(), eq(ImageType.POST))).willReturn("path/new.jpg");
        given(postService.updatePost(eq(1L), any(), any(PostUpdateRequest.class))).willReturn(createMockResponse());

        // when & then
        PostUpdateRequest request = PostUpdateRequest.builder()
                .title("New Title").content("New Content").keepImages(List.of()).build();
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart(HttpMethod.PATCH, "/posts/{postId}", 1L)
                        .file(image)
                        .file(requestPart)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시물 수정 성공"));
    }

    @Test
    @DisplayName("게시글 삭제 성공")
    @WithCustomMockUser
    void deletePost_Success() throws Exception {
        mockMvc.perform(delete("/posts/{postId}", 1L)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(postService).deletePost(any(), eq(1L));
    }

    @Test
    @DisplayName("게시글 좋아요 토글 성공")
    @WithCustomMockUser
    void toggleLike_Success() throws Exception {
        PostLikeResponse response = new PostLikeResponse(true, 10L);
        given(postLikeService.toggleLike(any(), eq(1L))).willReturn(response);

        mockMvc.perform(post("/posts/{postId}/like", 1L)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isLiked").value(true));
    }

    @Test
    @DisplayName("게시글 생성 실패 - 잘못된 공개 범위는 400 (커스텀 제약 @ValidScopePost 동작 확인)")
    @WithCustomMockUser
    void createPost_Fail_InvalidScope() throws Exception {
        PostCreateRequest request = PostCreateRequest.builder()
                .scope("CLUB").title("Title").content("Content").build(); // CLUB인데 clubId 없음
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/posts")
                        .file(requestPart)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(postService, never()).createPost(any(), any());
    }
}
