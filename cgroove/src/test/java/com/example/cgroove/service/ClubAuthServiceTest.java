package com.example.cgroove.service;

import com.example.cgroove.entity.Club;
import com.example.cgroove.entity.ClubJoin;
import com.example.cgroove.enums.ClubJoinStatus;
import com.example.cgroove.enums.ClubRole;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.exception.NotFoundException;
import com.example.cgroove.repository.ClubJoinRepository;
import com.example.cgroove.repository.ClubRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ClubAuthServiceTest {

    @InjectMocks
    private ClubAuthService clubAuthService;

    @Mock
    private ClubJoinRepository clubJoinRepository;

    @Mock
    private ClubRepository clubRepository;

    @Test
    @DisplayName("관리자 권한 확인 성공 - 리더")
    void validateClubAuthority_Success_Leader() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().role(ClubRole.LEADER).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(join));

        // when & then
        assertDoesNotThrow(() -> clubAuthService.validateClubAuthority(userId, clubId));
    }

    @Test
    @DisplayName("관리자 권한 확인 성공 - 매니저")
    void validateClubAuthority_Success_Manager() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().role(ClubRole.MANAGER).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(join));

        // when & then
        assertDoesNotThrow(() -> clubAuthService.validateClubAuthority(userId, clubId));
    }

    @Test
    @DisplayName("관리자 권한 확인 실패 - 일반 멤버")
    void validateClubAuthority_Fail_Member() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().role(ClubRole.MEMBER).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(join));

        // when & then
        assertThrows(AccessDeniedException.class, () -> clubAuthService.validateClubAuthority(userId, clubId));
    }

    @Test
    @DisplayName("리더 권한 확인 성공 - 리더")
    void validateLeaderAuthority_Success_Leader() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().role(ClubRole.LEADER).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(join));

        // when & then
        assertDoesNotThrow(() -> clubAuthService.validateLeaderAuthority(userId, clubId));
    }

    @Test
    @DisplayName("리더 권한 확인 실패 - 매니저")
    void validateLeaderAuthority_Fail_Manager() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().role(ClubRole.MANAGER).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(join));

        // when & then
        assertThrows(AccessDeniedException.class, () -> clubAuthService.validateLeaderAuthority(userId, clubId));
    }

    @Test
    @DisplayName("리더 권한 확인 실패 - 일반 멤버")
    void validateLeaderAuthority_Fail_Member() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        ClubJoin join = ClubJoin.builder().role(ClubRole.MEMBER).build();

        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.of(join));

        // when & then
        assertThrows(AccessDeniedException.class, () -> clubAuthService.validateLeaderAuthority(userId, clubId));
    }

    @Test
    @DisplayName("클럽 조회 성공")
    void findByClubId_Success() {
        // given
        Long clubId = 10L;
        Club club = Club.builder().clubId(clubId).clubName("TestClub").build();

        given(clubRepository.findById(clubId)).willReturn(Optional.of(club));

        // when
        Club result = clubAuthService.findByClubId(clubId);

        // then
        assertThat(result.getClubId()).isEqualTo(clubId);
        assertThat(result.getClubName()).isEqualTo("TestClub");
    }

    @Test
    @DisplayName("클럽 조회 실패 - 존재하지 않는 클럽")
    void findByClubId_Fail_NotFound() {
        // given
        Long clubId = 999L;
        given(clubRepository.findById(clubId)).willReturn(Optional.empty());

        // when & then
        assertThrows(NotFoundException.class, () -> clubAuthService.findByClubId(clubId));
    }

    @Test
    @DisplayName("가입 정보 조회 실패 - 가입 정보 없음")
    void findClubJoin_Fail_NotFound() {
        // given
        Long userId = 1L;
        Long clubId = 10L;
        given(clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId))
                .willReturn(Optional.empty());

        // when & then
        assertThrows(NotFoundException.class, () -> clubAuthService.findClubJoin(userId, clubId));
    }

    @Test
    @DisplayName("사용자 클럽 ID 목록 조회 성공")
    void findUserClubIds_Success() {
        // given
        Long userId = 1L;
        List<Long> clubIds = List.of(10L, 20L, 30L);

        given(clubJoinRepository.findClubIdsByUserIdAndStatus(userId, ClubJoinStatus.ACTIVE))
                .willReturn(clubIds);

        // when
        List<Long> result = clubAuthService.findUserClubIds(userId);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(10L, 20L, 30L);
    }
}