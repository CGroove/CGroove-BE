package com.example.cgroove.service;

import com.example.cgroove.dto.eventJoin.EventJoinResponse;
import com.example.cgroove.entity.Event;
import com.example.cgroove.entity.EventJoin;
import com.example.cgroove.entity.User;
import com.example.cgroove.enums.EventJoinStatus;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.exception.ConflictException;
import com.example.cgroove.exception.InvalidRequestException;
import com.example.cgroove.exception.NotFoundException;
import com.example.cgroove.repository.EventJoinRepository;
import com.example.cgroove.repository.EventRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventJoinServiceTest {

    @InjectMocks
    private EventJoinService eventJoinService;

    @Mock
    private EventJoinRepository eventJoinRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("행사 신청 성공 - 신규 신청")
    void applyEvent_Success_New() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        User user = User.builder().userId(userId).build();
        Event event = Event.builder().eventId(eventId).capacity(50L).build();

        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId))
                .willReturn(Optional.empty());
        given(eventRepository.findWithLockByEventId(eventId))
                .willReturn(Optional.of(event));
        given(eventJoinRepository.countByEvent_EventIdAndStatus(eventId, EventJoinStatus.CONFIRMED))
                .willReturn(10L);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        EventJoin savedJoin = EventJoin.builder().participant(user).event(event).status(EventJoinStatus.CONFIRMED).build();
        given(eventJoinRepository.save(any(EventJoin.class))).willReturn(savedJoin);

        // when
        EventJoinResponse response = eventJoinService.applyEvent(userId, eventId);

        // then
        assertThat(response.status()).isEqualTo(EventJoinStatus.CONFIRMED.name());
        verify(eventJoinRepository, times(1)).save(any(EventJoin.class));
    }

    @Test
    @DisplayName("행사 신청 성공 - 취소했던 유저 재신청")
    void applyEvent_Success_Canceled() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        User user = User.builder().userId(userId).build();
        Event event = Event.builder().eventId(eventId).capacity(50L).build();

        EventJoin existingJoin = EventJoin.builder().status(EventJoinStatus.CANCELED).event(event).participant(user).build();

        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId))
                .willReturn(Optional.of(existingJoin));
        given(eventRepository.findWithLockByEventId(eventId))
                .willReturn(Optional.of(event));
        given(eventJoinRepository.countByEvent_EventIdAndStatus(eventId, EventJoinStatus.CONFIRMED))
                .willReturn(10L);

        // when
        EventJoinResponse response = eventJoinService.applyEvent(userId, eventId);

        // then
        assertThat(response.status()).isEqualTo(EventJoinStatus.CONFIRMED.name());
        assertThat(existingJoin.getStatus()).isEqualTo(EventJoinStatus.CONFIRMED);
        verify(eventJoinRepository, times(0)).save(any(EventJoin.class));
    }

    @Test
    @DisplayName("행사 신청 성공 - 거절 당했던 유저 재신청")
    void applyEvent_Success_Rejected() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        User user = User.builder().userId(userId).build();
        Event event = Event.builder().eventId(eventId).capacity(50L).build();

        EventJoin existingJoin = EventJoin.builder().status(EventJoinStatus.REJECTED).event(event).participant(user).build();

        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId))
                .willReturn(Optional.of(existingJoin));
        given(eventRepository.findWithLockByEventId(eventId))
                .willReturn(Optional.of(event));
        given(eventJoinRepository.countByEvent_EventIdAndStatus(eventId, EventJoinStatus.CONFIRMED))
                .willReturn(10L);

        // when
        EventJoinResponse response = eventJoinService.applyEvent(userId, eventId);

        // then
        assertThat(response.status()).isEqualTo(EventJoinStatus.CONFIRMED.name());
        assertThat(existingJoin.getStatus()).isEqualTo(EventJoinStatus.CONFIRMED);
        verify(eventJoinRepository, times(0)).save(any(EventJoin.class));
    }

    @Test
    @DisplayName("행사 신청 실패 - 이미 신청됨")
    void applyEvent_Fail_AlreadyJoined() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        EventJoin existingJoin = EventJoin.builder().status(EventJoinStatus.CONFIRMED).build();

        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId))
                .willReturn(Optional.of(existingJoin));

        // when & then
        assertThrows(ConflictException.class, () -> eventJoinService.applyEvent(userId, eventId));
    }

    @Test
    @DisplayName("행사 신청 실패 - 정원 초과")
    void applyEvent_Fail_FullCapacity() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        Event event = Event.builder().eventId(eventId).capacity(50L).build();

        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId))
                .willReturn(Optional.empty());
        given(eventRepository.findWithLockByEventId(eventId))
                .willReturn(Optional.of(event));
        given(eventJoinRepository.countByEvent_EventIdAndStatus(eventId, EventJoinStatus.CONFIRMED))
                .willReturn(50L);

        // when & then
        assertThrows(ConflictException.class, () -> eventJoinService.applyEvent(userId, eventId));
    }

    @Test
    @DisplayName("신청 취소 성공")
    void cancelEventJoin_Success() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        EventJoin join = EventJoin.builder().status(EventJoinStatus.CONFIRMED).build();

        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId))
                .willReturn(Optional.of(join));

        // when
        eventJoinService.cancelEventJoin(userId, eventId);

        // then
        assertThat(join.getStatus()).isEqualTo(EventJoinStatus.CANCELED);
    }

    @Test
    @DisplayName("신청 취소 실패 - 이미 취소됨")
    void cancelEventJoin_Fail_AlreadyCanceled() {
        // given
        EventJoin join = EventJoin.builder().status(EventJoinStatus.CANCELED).build();
        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(1L, 100L))
                .willReturn(Optional.of(join));

        // when & then
        assertThrows(InvalidRequestException.class, () -> eventJoinService.cancelEventJoin(1L, 100L));
    }

    @Test
    @DisplayName("신청 취소 실패 - 이미 거절됨")
    void cancelEventJoin_Fail_AlreadyRejected() {
        // given
        EventJoin join = EventJoin.builder().status(EventJoinStatus.REJECTED).build();
        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(1L, 100L))
                .willReturn(Optional.of(join));

        // when & then
        assertThrows(InvalidRequestException.class, () -> eventJoinService.cancelEventJoin(1L, 100L));
    }

    @Test
    @DisplayName("참여 거절 성공")
    void rejectParticipation_Success() {
        // given
        Long hostId = 10L;
        Long eventId = 100L;
        Long participantId = 20L;

        User host = User.builder().userId(hostId).build();
        Event event = Event.builder().eventId(eventId).host(host).build();
        EventJoin targetJoin = EventJoin.builder().status(EventJoinStatus.CONFIRMED).build();

        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(participantId, eventId))
                .willReturn(Optional.of(targetJoin));

        // when
        eventJoinService.rejectParticipation(hostId, eventId, participantId);

        // then
        assertThat(targetJoin.getStatus()).isEqualTo(EventJoinStatus.REJECTED);
    }

    @Test
    @DisplayName("참여 거절 실패 - 주최자가 아님")
    void rejectParticipation_Fail_NotHost() {
        // given
        Long hostId = 10L;
        Long otherUserId = 99L;
        Event event = Event.builder().host(User.builder().userId(hostId).build()).build();

        given(eventRepository.findById(100L)).willReturn(Optional.of(event));

        // when & then
        assertThrows(AccessDeniedException.class, () ->
                eventJoinService.rejectParticipation(otherUserId, 100L, 20L));
    }

    @Test
    @DisplayName("내 신청 목록 조회 - N+1 해결 검증용 Mocking")
    void getUserEvents_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().userId(userId).build();

        Event event = Event.builder().eventId(100L).host(User.builder().nickname("Host").build()).build();
        EventJoin join = EventJoin.builder().event(event).participant(user).status(EventJoinStatus.CONFIRMED).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(eventJoinRepository.findMyJoinedEvents(userId, EventJoinStatus.CONFIRMED))
                .willReturn(List.of(join));

        // when
        List<EventJoinResponse> responses = eventJoinService.getUserEvents(userId);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().eventId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("행사 참여자 목록 조회 성공")
    void getEventUsers_Success() {
        // given
        Long eventId = 100L;
        given(eventRepository.existsById(eventId)).willReturn(true);

        User participant = User.builder().userId(5L).nickname("Dancer").build();
        EventJoin join = EventJoin.builder()
                .event(Event.builder().eventId(eventId).build())
                .participant(participant)
                .status(EventJoinStatus.CONFIRMED)
                .build();

        given(eventJoinRepository.findParticipantsWithUser(eventId, EventJoinStatus.CONFIRMED))
                .willReturn(List.of(join));

        // when
        List<EventJoinResponse> result = eventJoinService.getEventUsers(eventId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().nickname()).isEqualTo("Dancer");
    }

    @Test
    @DisplayName("신청 상태 조회 성공")
    void getJoinStatus_Success() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        User user = User.builder().userId(userId).build();
        Event event = Event.builder().eventId(eventId).build();

        EventJoin join = EventJoin.builder()
                .participant(user)
                .event(event)
                .status(EventJoinStatus.CONFIRMED)
                .build();

        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId))
                .willReturn(Optional.of(join));

        // when
        EventJoinResponse response = eventJoinService.getJoinStatus(userId, eventId);

        // then
        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("신청 상태 조회 실패 - 이력 없음")
    void getJoinStatus_Fail_NotFound() {
        // given
        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(anyLong(), anyLong()))
                .willReturn(Optional.empty());

        // when & then
        assertThrows(NotFoundException.class, () ->
                eventJoinService.getJoinStatus(1L, 100L)
        );
    }

    @Test
    @DisplayName("참여 거절 실패 - 신청 상태가 CONFIRMED가 아님")
    void rejectParticipation_Fail_NotConfirmed() {
        // given
        Long hostId = 10L;
        Long eventId = 100L;
        Long participantId = 20L;

        User host = User.builder().userId(hostId).build();
        Event event = Event.builder().eventId(eventId).host(host).build();

        EventJoin targetJoin = EventJoin.builder().status(EventJoinStatus.CANCELED).build();

        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
        given(eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(participantId, eventId))
                .willReturn(Optional.of(targetJoin));

        // when & then
        assertThrows(InvalidRequestException.class, () ->
                eventJoinService.rejectParticipation(hostId, eventId, participantId)
        );
    }

    @Test
    @DisplayName("행사 참여자 목록 조회 실패 - 행사 없음")
    void getEventUsers_Fail_NotFound() {
        // given
        given(eventRepository.existsById(anyLong())).willReturn(false);

        // when & then
        assertThrows(NotFoundException.class, () ->
                eventJoinService.getEventUsers(999L)
        );
    }

    @Test
    @DisplayName("유저 탈퇴 시 신청 내역 취소 처리")
    void softDeleteByUserId() {
        // given
        Long userId = 1L;

        // when
        eventJoinService.softDeleteByUserId(userId);

        // then
        verify(eventJoinRepository).softDeleteByUserId(userId, EventJoinStatus.CANCELED);
    }

    @Test
    @DisplayName("클럽 삭제 시 관련 행사 신청 내역 취소 처리")
    void softDeleteByClubId() {
        // given
        Long clubId = 10L;

        // when
        eventJoinService.softDeleteByClubId(clubId);

        // then
        verify(eventJoinRepository).softDeleteByClubId(clubId, EventJoinStatus.CANCELED);
    }
}