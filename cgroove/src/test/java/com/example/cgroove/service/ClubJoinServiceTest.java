package com.example.cgroove.service;

import com.example.cgroove.dto.club.ClubJoinResponse;
import com.example.cgroove.entity.Club;
import com.example.cgroove.entity.ClubJoin;
import com.example.cgroove.entity.User;
import com.example.cgroove.enums.ClubJoinStatus;
import com.example.cgroove.enums.ClubRole;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.exception.ConflictException;
import com.example.cgroove.exception.InvalidRequestException;
import com.example.cgroove.repository.ClubJoinRepository;
import com.example.cgroove.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClubJoinServiceTest {

    @InjectMocks
    private ClubJoinService clubJoinService;

    @Mock
    private ClubJoinRepository clubJoinRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClubAuthService clubAuthService;

    @Test
    @DisplayName("클럽 가입 신청 성공 - 신규 신청")
    void applyToClub_Success_New() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        User user = User.builder().userId(userId).build();
        Club club = Club.builder().clubId(clubId).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(clubAuthService.findByClubId(clubId)).willReturn(club);

        ClubJoin savedJoin = ClubJoin.builder().user(user).club(club).status(ClubJoinStatus.PENDING).role(ClubRole.MEMBER).build();
        given(clubJoinRepository.save(any(ClubJoin.class))).willReturn(savedJoin);

        // when
        ClubJoinResponse response = clubJoinService.applyToClub(userId, clubId);

        // then
        assertThat(response.status()).isEqualTo(ClubJoinStatus.PENDING.name());
        verify(clubJoinRepository, times(1)).save(any(ClubJoin.class));
    }

    @Test
    @DisplayName("클럽 가입 신청 성공 - 탈퇴 후 재가입")
    void applyToClub_Success_Left() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        User user = User.builder().userId(userId).build();
        Club club = Club.builder().clubId(clubId).build();
        ClubJoin leftUser = ClubJoin.builder().status(ClubJoinStatus.LEFT).role(ClubRole.MEMBER).user(user).club(club).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(leftUser));

        // when
        ClubJoinResponse response = clubJoinService.applyToClub(userId, clubId);

        // then
        assertThat(response.status()).isEqualTo(ClubJoinStatus.PENDING.name());
        assertThat(leftUser.getStatus()).isEqualTo(ClubJoinStatus.PENDING);
        verify(clubJoinRepository, never()).save(any());
    }

    @Test
    @DisplayName("클럽 가입 신청 성공 - 취소 당한 후 재가입")
    void applyToClub_Success_Canceled() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        User user = User.builder().userId(userId).build();
        Club club = Club.builder().clubId(clubId).build();
        ClubJoin canceledUser = ClubJoin.builder().status(ClubJoinStatus.CANCELED).role(ClubRole.MEMBER).user(user).club(club).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(canceledUser));

        // when
        ClubJoinResponse response = clubJoinService.applyToClub(userId, clubId);

        // then
        assertThat(response.status()).isEqualTo(ClubJoinStatus.PENDING.name());
        assertThat(canceledUser.getStatus()).isEqualTo(ClubJoinStatus.PENDING);
        verify(clubJoinRepository, never()).save(any());
    }

    @Test
    @DisplayName("클럽 가입 신청 성공 - 거절 당한 후 재가입")
    void applyToClub_Success_Rejected() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        User user = User.builder().userId(userId).build();
        Club club = Club.builder().clubId(clubId).build();
        ClubJoin rejectedUser = ClubJoin.builder().status(ClubJoinStatus.REJECTED).role(ClubRole.MEMBER).user(user).club(club).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(rejectedUser));

        // when
        ClubJoinResponse response = clubJoinService.applyToClub(userId, clubId);

        // then
        assertThat(response.status()).isEqualTo(ClubJoinStatus.PENDING.name());
        assertThat(rejectedUser.getStatus()).isEqualTo(ClubJoinStatus.PENDING);
        verify(clubJoinRepository, never()).save(any());
    }

    @Test
    @DisplayName("클럽 가입 신청 실패 - 이미 활동 중")
    void applyToClub_Fail_AlreadyActive() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin existingJoin = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(existingJoin));

        // when & then
        assertThrows(ConflictException.class, () -> clubJoinService.applyToClub(userId, clubId));
    }

    @Test
    @DisplayName("클럽 가입 신청 실패 - 이미 신청 대기 중(PENDING)")
    void applyToClub_Fail_AlreadyPending() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin existingJoin = ClubJoin.builder().status(ClubJoinStatus.PENDING).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(existingJoin));

        // when & then
        assertThrows(ConflictException.class, () -> clubJoinService.applyToClub(userId, clubId));
    }

    @Test
    @DisplayName("신청 취소 성공")
    void cancelApplication_Success() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().status(ClubJoinStatus.PENDING).build();

        given(clubAuthService.findClubJoin(userId, clubId)).willReturn(join);

        // when
        clubJoinService.cancelApplication(userId, clubId);

        // then
        assertThat(join.getStatus()).isEqualTo(ClubJoinStatus.CANCELED);
    }

    @Test
    @DisplayName("신청 취소 실패 - 대기 상태가 아님")
    void cancelApplication_Fail_NotPending() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).build();

        given(clubAuthService.findClubJoin(userId, clubId)).willReturn(join);

        // when & then
        assertThrows(InvalidRequestException.class, () -> clubJoinService.cancelApplication(userId, clubId));
    }

    @Test
    @DisplayName("클럽 탈퇴 성공 - 일반 멤버")
    void leaveClub_Success() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).role(ClubRole.MEMBER).build();

        given(clubAuthService.findClubJoin(userId, clubId)).willReturn(join);

        // when
        clubJoinService.leaveClub(userId, clubId);

        // then
        assertThat(join.getStatus()).isEqualTo(ClubJoinStatus.LEFT);
    }

    @Test
    @DisplayName("클럽 탈퇴 성공 - 관리자")
    void leaveClub_Success_Manager() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).role(ClubRole.MANAGER).build();
        given(clubAuthService.findClubJoin(userId, clubId)).willReturn(join);

        // when
        clubJoinService.leaveClub(userId, clubId);

        // then
        assertThat(join.getStatus()).isEqualTo(ClubJoinStatus.LEFT);
    }

    @Test
    @DisplayName("클럽 탈퇴 실패 - 리더는 탈퇴 불가")
    void leaveClub_Fail_Leader() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).role(ClubRole.LEADER).build();

        given(clubAuthService.findClubJoin(userId, clubId)).willReturn(join);

        // when & then
        assertThrows(InvalidRequestException.class, () -> clubJoinService.leaveClub(userId, clubId));
    }

    @Test
    @DisplayName("가입 승인 성공")
    void approveApplication_Success() {
        // given
        Long managerId = 1L;
        Long clubId = 10L;
        Long applicantId = 2L;
        ClubJoin applicantJoin = ClubJoin.builder().status(ClubJoinStatus.PENDING).build();

        doNothing().when(clubAuthService).validateClubAuthority(managerId, clubId);
        given(clubAuthService.findClubJoin(applicantId, clubId)).willReturn(applicantJoin);

        // when
        clubJoinService.approveApplication(managerId, clubId, applicantId);

        // then
        assertThat(applicantJoin.getStatus()).isEqualTo(ClubJoinStatus.ACTIVE);
    }

    @Test
    @DisplayName("가입 거절 성공")
    void rejectApplication_Success() {
        // given
        Long managerId = 1L;
        Long clubId = 10L;
        Long applicantId = 2L;
        ClubJoin applicantJoin = ClubJoin.builder().status(ClubJoinStatus.PENDING).build();

        doNothing().when(clubAuthService).validateClubAuthority(managerId, clubId);
        given(clubAuthService.findClubJoin(applicantId, clubId)).willReturn(applicantJoin);

        // when
        clubJoinService.rejectApplication(managerId, clubId, applicantId);

        // then
        assertThat(applicantJoin.getStatus()).isEqualTo(ClubJoinStatus.REJECTED);
    }

    @Test
    @DisplayName("가입 거절 실패 - 대기 상태가 아님")
    void rejectApplication_Fail_NotPending() {
        // given
        Long managerId = 1L;
        Long clubId = 10L;
        Long applicantId = 2L;
        ClubJoin applicantJoin = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).build();

        doNothing().when(clubAuthService).validateClubAuthority(managerId, clubId);
        given(clubAuthService.findClubJoin(applicantId, clubId)).willReturn(applicantJoin);

        // when & then
        assertThrows(InvalidRequestException.class, () ->
                clubJoinService.rejectApplication(managerId, clubId, applicantId));
    }

    @Test
    @DisplayName("멤버 추방 성공")
    void kickMember_Success() {
        // given
        Long managerId = 1L;
        Long clubId = 10L;
        Long targetId = 2L;
        ClubJoin targetJoin = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).role(ClubRole.MEMBER).build();

        doNothing().when(clubAuthService).validateClubAuthority(managerId, clubId);
        given(clubAuthService.findClubJoin(targetId, clubId)).willReturn(targetJoin);

        // when
        clubJoinService.kickMember(managerId, clubId, targetId);

        // then
        assertThat(targetJoin.getStatus()).isEqualTo(ClubJoinStatus.LEFT);
    }

    @Test
    @DisplayName("멤버 추방 실패 - 자기 자신 추방 불가")
    void kickMember_Fail_Self() {
        // given
        Long managerId = 1L;
        Long clubId = 10L;
        Long targetId = 1L;

        doNothing().when(clubAuthService).validateClubAuthority(managerId, clubId);
        ClubJoin selfJoin = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).role(ClubRole.MANAGER).build();
        given(clubAuthService.findClubJoin(targetId, clubId)).willReturn(selfJoin);

        // when & then
        assertThrows(InvalidRequestException.class, () ->
                clubJoinService.kickMember(managerId, clubId, targetId));
    }

    @Test
    @DisplayName("권한 변경 성공 - 리더만 가능")
    void changeMemberRole_Success() {
        // given
        Long leaderId = 1L;
        Long clubId = 10L;
        Long targetId = 2L;
        ClubJoin targetJoin = ClubJoin.builder().status(ClubJoinStatus.ACTIVE).role(ClubRole.MEMBER).build();

        doNothing().when(clubAuthService).validateLeaderAuthority(leaderId, clubId);
        given(clubAuthService.findClubJoin(targetId, clubId)).willReturn(targetJoin);

        // when
        clubJoinService.changeMemberRole(leaderId, clubId, targetId, ClubRole.MANAGER);

        // then
        assertThat(targetJoin.getRole()).isEqualTo(ClubRole.MANAGER);
    }

    @Test
    @DisplayName("내 클럽 목록 조회 성공")
    void getMyClubs_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().userId(userId).build();
        Club club = Club.builder().clubId(10L).clubName("Dance Team").build();
        ClubJoin join = ClubJoin.builder().user(user).club(club).role(ClubRole.LEADER).status(ClubJoinStatus.ACTIVE).build();

        given(clubJoinRepository.findMyClubJoins(userId, List.of(ClubJoinStatus.ACTIVE)))
                .willReturn(List.of(join));

        // when
        var result = clubJoinService.getMyClubs(userId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().clubName()).isEqualTo("Dance Team");
    }

    @Test
    @DisplayName("내 전체 클럽 이력 조회 성공 (활동중 + 대기중)")
    void getMyAllClubs_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().userId(userId).build();
        Club club = Club.builder().clubId(10L).clubName("Dance Team").build();
        ClubJoin join = ClubJoin.builder().user(user).club(club).role(ClubRole.LEADER).status(ClubJoinStatus.PENDING).build();

        given(clubJoinRepository.findMyClubJoins(userId, List.of(ClubJoinStatus.ACTIVE, ClubJoinStatus.PENDING)))
                .willReturn(List.of(join));

        // when
        List<ClubJoinResponse> result = clubJoinService.getMyAllClubs(userId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(ClubJoinStatus.PENDING.name());
    }

    @Test
    @DisplayName("가입 상태 단건 조회 성공")
    void getJoinStatus_Success() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        User user = User.builder().userId(userId).build();
        Club club = Club.builder().clubId(10L).clubName("Dance Team").build();
        ClubJoin join = ClubJoin.builder().user(user).club(club).role(ClubRole.LEADER).status(ClubJoinStatus.ACTIVE).build();

        given(clubAuthService.findClubJoin(userId, clubId)).willReturn(join);

        // when
        ClubJoinResponse result = clubJoinService.getJoinStatus(userId, clubId);

        // then
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("활동 중인 멤버 목록 조회 성공")
    void getActiveMembers_Success() {
        // given
        Long clubId = 10L;
        ClubJoin memberJoin = ClubJoin.builder()
                .user(User.builder().nickname("User1").build())
                .club(Club.builder().clubId(clubId).build())
                .role(ClubRole.MEMBER)
                .status(ClubJoinStatus.ACTIVE) // 상태 필수
                .build();

        given(clubJoinRepository.findClubMembers(clubId, ClubJoinStatus.ACTIVE))
                .willReturn(List.of(memberJoin));

        // when
        List<ClubJoinResponse> result = clubJoinService.getActiveMembers(clubId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().nickname()).isEqualTo("User1");
    }

    @Test
    @DisplayName("가입 신청자 목록 조회 성공 - 관리자 권한 확인")
    void getPendingApplications_Success() {
        // given
        Long managerId = 1L;
        Long clubId = 10L;
        doNothing().when(clubAuthService).validateClubAuthority(managerId, clubId);

        ClubJoin applicant = ClubJoin.builder()
                .user(User.builder().nickname("Applicant").build())
                .club(Club.builder().clubId(clubId).build())
                .role(ClubRole.LEADER)
                .status(ClubJoinStatus.PENDING)
                .build();

        given(clubJoinRepository.findClubMembers(clubId, ClubJoinStatus.PENDING))
                .willReturn(List.of(applicant));

        // when
        List<ClubJoinResponse> result = clubJoinService.getPendingApplications(managerId, clubId);

        // then
        assertThat(result).hasSize(1);
        verify(clubAuthService).validateClubAuthority(managerId, clubId);
    }

    @Test
    @DisplayName("가입 신청자 목록 조회 실패 - 권한 없음")
    void getPendingApplications_Fail_NoAuth() {
        // given
        Long userId = 1L;
        Long clubId = 10L;

        doThrow(new AccessDeniedException("권한 없음"))
                .when(clubAuthService).validateClubAuthority(userId, clubId);

        // when & then
        assertThrows(AccessDeniedException.class, () ->
                clubJoinService.getPendingApplications(userId, clubId)
        );
    }

    @Test
    @DisplayName("유저 탈퇴 시 관련 클럽 가입 정보 Soft Delete")
    void softDeleteByUserId() {
        // given
        Long userId = 1L;

        // when
        clubJoinService.softDeleteByUserId(userId);

        // then
        verify(clubJoinRepository).softDeleteByUserId(userId, ClubJoinStatus.LEFT);
    }

    @Test
    @DisplayName("클럽 삭제 시 관련 가입 정보 Soft Delete")
    void softDeleteByClubId() {
        // given
        Long clubId = 10L;

        // when
        clubJoinService.softDeleteByClubId(clubId);

        // then
        verify(clubJoinRepository).softDeleteByClubId(clubId, ClubJoinStatus.CANCELED);
    }
}