package com.example.cgroove.service;

import com.example.cgroove.dto.user.PasswordUpdateRequest;
import com.example.cgroove.dto.user.UserResponse;
import com.example.cgroove.dto.user.UserUpdateRequest;
import com.example.cgroove.entity.User;
import com.example.cgroove.exception.ConflictException;
import com.example.cgroove.exception.NotFoundException;
import com.example.cgroove.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PostService postService;
    @Mock
    private EventService eventService;
    @Mock
    private ClubJoinService clubJoinService;
    @Mock
    private EventJoinService eventJoinService;
    @Mock
    private EntityManager em;

    @Test
    @DisplayName("회원가입 성공")
    void createUser_Success() {
        // given
        String email = "test@example.com";
        String password = "password";
        String nickname = "Dancer";
        String profileImage = "image.jpg";

        given(userRepository.existsByEmail(email)).willReturn(false);
        given(userRepository.existsByNickname(nickname)).willReturn(false);
        given(passwordEncoder.encode(password)).willReturn("encodedPassword");

        User savedUser = User.builder()
                .userId(1L)
                .email(email)
                .password("encodedPassword")
                .nickname(nickname)
                .profileImage(profileImage)
                .build();

        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // when
        UserResponse response = userService.createUser(email, password, nickname, profileImage);

        // then
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.nickname()).isEqualTo(nickname);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void createUser_Fail_DuplicateEmail() {
        // given
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        // when & then
        assertThrows(ConflictException.class, () -> userService.createUser("test@example.com", "pw", "nick", null));
    }

    @Test
    @DisplayName("회원가입 실패 - 닉네임 중복")
    void createUser_Fail_DuplicateNickname() {
        // given
        String email = "new@test.com";
        String nickname = "ExistingNick";

        given(userRepository.existsByEmail(email)).willReturn(false);
        given(userRepository.existsByNickname(nickname)).willReturn(true);

        // when & then
        assertThrows(ConflictException.class, () -> userService.createUser(email, "pw", nickname, null));
    }

    @Test
    @DisplayName("회원 정보 수정 성공 - 닉네임 및 이미지 변경")
    void updateUser_Success() {
        // given
        Long userId = 1L;
        User user = spy(User.builder()
                .userId(userId)
                .nickname("OldNick")
                .profileImage("old.jpg")
                .build());

        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.isEmpty()).willReturn(false);
        UserUpdateRequest request = new UserUpdateRequest("NewNick");

        given(userRepository.existsByNicknameAndUserIdNot("NewNick", userId)).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willReturn(user);
        given(fileStorageService.saveImage(any(), any())).willReturn("new.jpg");

        // when
        UserResponse response = userService.updateUser(userId, request, mockFile);

        // then
        verify(fileStorageService).deleteFile("old.jpg");
        assertThat(user.getNickname()).isEqualTo("NewNick");
        assertThat(user.getProfileImage()).isEqualTo("new.jpg");
    }

    @Test
    @DisplayName("회원 정보 수정 실패 - 중복된 닉네임")
    void updateUser_Fail_DuplicateNickname() {
        // given
        Long userId = 1L;
        UserUpdateRequest request = new UserUpdateRequest("DuplicateNick");

        given(userRepository.existsByNicknameAndUserIdNot("DuplicateNick", userId)).willReturn(true);

        // when & then
        assertThrows(ConflictException.class, () -> userService.updateUser(userId, request, null));
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void updatePassword_Success() {
        // given
        Long userId = 1L;
        User user = spy(User.builder().userId(userId).password("oldHash").build());
        PasswordUpdateRequest request = new PasswordUpdateRequest("newPassword");

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPassword")).willReturn("newHash");
        given(userRepository.save(user)).willReturn(user);

        // when
        userService.updatePassword(userId, request);

        // then
        assertThat(user.getPassword()).isEqualTo("newHash");
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 연관 데이터 삭제 호출 확인")
    void deleteUser_Success() {
        // given
        Long userId = 1L;
        User user = spy(User.builder().userId(userId).profileImage("profile.jpg").build());

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        userService.deleteUser(userId);

        // then
        verify(fileStorageService).deleteFile("profile.jpg");
        verify(postService).softDeleteByUserId(userId);
        verify(eventService).softDeleteByUserId(userId);
        verify(clubJoinService).softDeleteByUserId(userId);
        verify(eventJoinService).softDeleteByUserId(userId);
        verify(user).delete();
        verify(em).flush();
        verify(em).clear();
    }

    @Test
    @DisplayName("회원 정보 조회 성공")
    void getUser_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().userId(userId).email("test@a.com").nickname("Nick").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getUser(userId);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("test@a.com");
    }

    @Test
    @DisplayName("회원 정보 조회 실패 - 존재하지 않는 유저")
    void getUser_Fail_NotFound() {
        // given
        Long userId = 999L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThrows(NotFoundException.class, () -> userService.getUser(userId));
    }

    @Test
    @DisplayName("프로필 이미지 삭제 성공 - 이미지가 있는 경우")
    void deleteProfileImage_Success() {
        // given
        Long userId = 1L;
        User user = spy(User.builder().userId(userId).profileImage("profile.jpg").build());

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        // when
        userService.deleteProfileImage(userId);

        // then
        verify(fileStorageService).deleteFile("profile.jpg");
        assertThat(user.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("프로필 이미지 삭제 - 이미지가 없으면 파일 삭제 호출 안 함")
    void deleteProfileImage_NoImage() {
        // given
        Long userId = 1L;
        User user = spy(User.builder().userId(userId).profileImage(null).build());

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        // when
        userService.deleteProfileImage(userId);

        // then
        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("비밀번호 일치 확인")
    void matchesPassword_True() {
        // given
        User user = User.builder().password("encodedPW").build();
        String rawPassword = "password123";

        given(passwordEncoder.matches(rawPassword, "encodedPW")).willReturn(true);

        // when
        boolean result = userService.matchesPassword(user, rawPassword);

        // then
        assertThat(result).isTrue();
    }
}