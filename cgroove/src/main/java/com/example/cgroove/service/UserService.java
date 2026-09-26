package com.example.cgroove.service;

import com.example.cgroove.dto.user.PasswordUpdateRequest;
import com.example.cgroove.dto.user.UserResponse;
import com.example.cgroove.dto.user.UserUpdateRequest;
import com.example.cgroove.entity.User;
import com.example.cgroove.exception.ConflictException;
import com.example.cgroove.exception.NotFoundException;
import com.example.cgroove.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.cgroove.enums.ImageType;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final PostService postService;
    private final EventService eventService;
    private final ClubJoinService clubJoinService;
    private final EventJoinService eventJoinService;
    private final EntityManager em;

    @Transactional
    public UserResponse createUser(String email, String password, String nickname, String profileImage) {
        if (this.existsByEmail(email)) {
            throw new ConflictException("이미 사용 중인 이메일입니다");
        }
        if (this.existsByNickname(nickname)) {
            throw new ConflictException("이미 사용 중인 닉네임입니다");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .profileImage(profileImage)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse getUser(Long userId) {
        return UserResponse.from(findByUserId(userId));
    }

    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request, MultipartFile profileImage) {
        validateNickname(request.getNickname(), userId);
        User user = this.findByUserId(userId);
        String currentProfileImage = user.getProfileImage();
        String newProfileImage = currentProfileImage;

        if (profileImage != null && !profileImage.isEmpty()) {
            newProfileImage = fileStorageService.saveImage(profileImage, ImageType.PROFILE);

            if (currentProfileImage != null) {
                fileStorageService.deleteFile(currentProfileImage);
            }
        }

        user.updateUser(
                request.getNickname(),
                newProfileImage);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updatePassword(Long userId, PasswordUpdateRequest request) {
        User user = this.findByUserId(userId);

        user.updatePassword(
                passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    @Transactional
    public UserResponse deleteProfileImage(Long userId) {
        User user = this.findByUserId(userId);

        if (user.getProfileImage() != null) {
            fileStorageService.deleteFile(user.getProfileImage());
            user.deleteImage();
        }

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findByUserId(userId);

        if (user.getProfileImage() != null) {
            fileStorageService.deleteFile(user.getProfileImage());
        }

        postService.softDeleteByUserId(userId);
        eventService.softDeleteByUserId(userId);
        clubJoinService.softDeleteByUserId(userId);
        eventJoinService.softDeleteByUserId(userId);
        user.delete();

        em.flush();
        em.clear();
    }

    public boolean matchesPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public User findByUserId(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("등록되지 않은 사용자"));
    }

    public Optional<User> findOptionalByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    public void validateNickname(String nickname, Long userId) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return;
        }

        if (userRepository.existsByNicknameAndUserIdNot(nickname, userId)) {
            throw new ConflictException("이미 사용 중인 닉네임입니다");
        }
    }
}