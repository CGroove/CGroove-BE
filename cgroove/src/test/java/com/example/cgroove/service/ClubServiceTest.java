package com.example.cgroove.service;

import com.example.cgroove.dto.club.ClubCreateRequest;
import com.example.cgroove.dto.club.ClubResponse;
import com.example.cgroove.dto.club.ClubUpdateRequest;
import com.example.cgroove.entity.Club;
import com.example.cgroove.entity.User;
import com.example.cgroove.enums.ClubJoinStatus;
import com.example.cgroove.enums.ClubRole;
import com.example.cgroove.enums.ClubType;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.repository.ClubRepository;
import com.example.cgroove.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @InjectMocks
    private ClubService clubService;

    @Mock
    private ClubRepository clubRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClubAuthService clubAuthService;
    @Mock
    private ClubJoinService clubJoinService;
    @Mock
    private EventJoinService eventJoinService;
    @Mock
    private PostService postService;
    @Mock
    private EventService eventService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private EntityManager em;

    @Test
    @DisplayName("동아리 생성 성공 - 생성자가 리더로 등록됨")
    void createClub_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().userId(userId).build();
        ClubCreateRequest request = ClubCreateRequest.builder()
                .clubName("Dance Crew")
                .intro("Intro")
                .description("Desc")
                .locationName("Seoul")
                .clubType(ClubType.CREW)
                .tags(List.of("hiphop"))
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        Club savedClub = Club.builder()
                .clubId(10L)
                .clubName("Dance Crew")
                .clubType(ClubType.CREW)
                .build();

        given(clubRepository.save(any(Club.class))).willReturn(savedClub);

        // when
        ClubResponse response = clubService.createClub(userId, request, null);

        // then
        ArgumentCaptor<Club> clubCaptor = ArgumentCaptor.forClass(Club.class);
        verify(clubRepository).save(clubCaptor.capture());
        Club capturedClub = clubCaptor.getValue();

        assertThat(response.clubName()).isEqualTo("Dance Crew");
        assertThat(capturedClub.getMembers()).hasSize(1);
        assertThat(capturedClub.getMembers().getFirst().getUser().getUserId()).isEqualTo(userId);
        assertThat(capturedClub.getMembers().getFirst().getRole()).isEqualTo(ClubRole.LEADER);
        assertThat(capturedClub.getMembers().getFirst().getStatus()).isEqualTo(ClubJoinStatus.ACTIVE);
    }

    @Test
    @DisplayName("동아리 수정 성공 - 권한 있음")
    void updateClub_Success() {
        // given
        Long userId = 1L;
        Long clubId = 10L;

        Club club = spy(Club.builder()
                .clubId(clubId)
                .clubName("Old Name")
                .clubImage("old.jpg")
                .build());

        ClubUpdateRequest request = ClubUpdateRequest.builder()
                .clubName("New Name")
                .intro("New Intro")
                .description("Desc")
                .locationName("Loc")
                .clubType(ClubType.CLUB)
                .tags(List.of("tag"))
                .build();

        doNothing().when(clubAuthService).validateClubAuthority(userId, clubId);
        given(clubAuthService.findByClubId(clubId)).willReturn(club);
        given(clubRepository.save(any(Club.class))).willReturn(club);

        // when
        clubService.updateClub(userId, clubId, request, null);

        // then
        verify(fileStorageService, never()).deleteFile(anyString());
        verify(club).updateClub(
                eq("New Name"), any(), any(), any(), any(), eq("old.jpg"), any()
        );
    }

    @Test
    @DisplayName("동아리 수정 실패 - 권한 없음")
    void updateClub_Fail_NoAuth() {
        // given
        Long userId = 99L;
        Long clubId = 10L;
        ClubUpdateRequest request = ClubUpdateRequest.builder()
                .clubName("Name")
                .intro("Intro")
                .description("Desc")
                .locationName("Loc")
                .clubType(ClubType.CLUB)
                .tags(null)
                .build();

        doThrow(new AccessDeniedException("권한 없음")).when(clubAuthService).validateClubAuthority(userId, clubId);

        // when & then
        assertThrows(AccessDeniedException.class, () ->
                clubService.updateClub(userId, clubId, request, null)
        );
    }

    @Test
    @DisplayName("동아리 이미지 삭제 성공")
    void deleteClubImage_Success() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        Club club = spy(Club.builder().clubId(clubId).clubImage("image.jpg").build());

        doNothing().when(clubAuthService).validateClubAuthority(userId, clubId);
        given(clubAuthService.findByClubId(clubId)).willReturn(club);
        given(clubRepository.save(club)).willReturn(club);

        // when
        clubService.deleteClubImage(userId, clubId);

        // then
        verify(fileStorageService).deleteFile("image.jpg");
        verify(club).deleteImage();
    }

    @Test
    @DisplayName("동아리 삭제 성공 - 연관 데이터 모두 삭제됨")
    void deleteClub_Success() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        Club club = spy(Club.builder().clubId(clubId).clubImage("image.jpg").build());

        doNothing().when(clubAuthService).validateLeaderAuthority(userId, clubId);
        given(clubAuthService.findByClubId(clubId)).willReturn(club);

        // when
        clubService.deleteClub(userId, clubId);

        // then
        verify(fileStorageService).deleteFile("image.jpg");
        verify(postService).softDeleteByClubId(clubId);
        verify(eventService).softDeleteByClubId(clubId);
        verify(clubJoinService).softDeleteByClubId(clubId);
        verify(eventJoinService).softDeleteByClubId(clubId);

        verify(club).delete();
        verify(em).flush();
    }
}