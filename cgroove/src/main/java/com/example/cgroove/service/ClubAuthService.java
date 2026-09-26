package com.example.cgroove.service;

import com.example.cgroove.entity.Club;
import com.example.cgroove.entity.ClubJoin;
import com.example.cgroove.enums.ClubJoinStatus;
import com.example.cgroove.enums.ClubRole;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.exception.NotFoundException;
import com.example.cgroove.repository.ClubJoinRepository;
import com.example.cgroove.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubAuthService {
    private final ClubRepository clubRepository;
    private final ClubJoinRepository clubJoinRepository;

    public void validateClubAuthority(Long userId, Long clubId) {
        ClubJoin clubJoin = findClubJoin(userId, clubId);

        if (!clubJoin.hasManagementPermission()) {
            throw new AccessDeniedException("클럽 권한이 없습니다");
        }
    }

    public void validateLeaderAuthority(Long userId, Long clubId) {
        ClubJoin clubJoin = findClubJoin(userId, clubId);

        if (clubJoin == null || clubJoin.getRole() != ClubRole.LEADER) {
            throw new AccessDeniedException("클럽 리더만 가능합니다");
        }
    }

    public Club findByClubId(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("클럽을 찾을 수 없습니다"));
    }

    public ClubJoin findClubJoin(Long userId, Long clubId) {
        return clubJoinRepository.findByUser_UserIdAndClub_ClubId(userId, clubId)
                .orElseThrow(() -> new NotFoundException("가입 정보를 찾을 수 없습니다"));
    }

    public List<Long> findUserClubIds(Long userId) {
        return clubJoinRepository.findClubIdsByUserIdAndStatus(userId, ClubJoinStatus.ACTIVE);
    }
}