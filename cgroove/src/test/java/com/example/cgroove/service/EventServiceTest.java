package com.example.cgroove.service;

import com.example.cgroove.dto.event.EventCreateRequest;
import com.example.cgroove.dto.event.EventResponse;
import com.example.cgroove.dto.event.EventUpdateRequest;
import com.example.cgroove.entity.Event;
import com.example.cgroove.entity.User;
import com.example.cgroove.enums.EventJoinStatus;
import com.example.cgroove.enums.EventType;
import com.example.cgroove.enums.Scope;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.exception.InvalidRequestException;
import com.example.cgroove.repository.EventJoinRepository;
import com.example.cgroove.repository.EventLikeRepository;
import com.example.cgroove.repository.EventRepository;
import com.example.cgroove.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks
    private EventService eventService;

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EventLikeRepository eventLikeRepository;
    @Mock
    private ClubAuthService clubAuthService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private EventJoinRepository eventJoinRepository;
    @Mock
    private EntityManager entityManager;

    @Test
    @DisplayName("행사 생성 성공 - GLOBAL 범위")
    void createEvent_Success_Global() {
        // given
        Long userId = 1L;
        User host = User.builder().userId(userId).build();

        EventCreateRequest request = new EventCreateRequest(
                "GLOBAL", null, "WORKSHOP", "Title", "Content",
                List.of("tag"), null, "Loc", "Addr", "Link",
                50L, LocalDateTime.now(), LocalDateTime.now().plusHours(2)
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(host));

        Event savedEvent = Event.builder()
                .host(host)
                .scope(Scope.GLOBAL)
                .type(EventType.WORKSHOP)
                .title("Title")
                .build();
        given(eventRepository.save(any(Event.class))).willReturn(savedEvent);

        // when
        EventResponse response = eventService.createEvent(userId, request);

        // then
        assertThat(response.scope()).isEqualTo("GLOBAL");
        assertThat(response.type()).isEqualTo("WORKSHOP");
        verify(clubAuthService, never()).findByClubId(any());
    }

    @Test
    @DisplayName("행사 생성 실패 - 잘못된 Scope")
    void createEvent_Fail_InvalidScope() {
        // given
        Long userId = 1L;
        User host = User.builder().userId(userId).build();
        EventCreateRequest request = new EventCreateRequest(
                "INVALID", null, "WORKSHOP", "Title", "Content", null, null, null, null, null, 10L, null, null
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(host));

        // when & then
        assertThrows(InvalidRequestException.class, () -> eventService.createEvent(userId, request));
    }

    @Test
    @DisplayName("행사 생성 실패 - CLUB인데 clubId 누락")
    void createEvent_Fail_NoClubId() {
        // given
        Long userId = 1L;
        User host = User.builder().userId(userId).build();
        EventCreateRequest request = new EventCreateRequest(
                "CLUB", null, "WORKSHOP", "Title", "Content", null, null, null, null, null, 10L, null, null
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(host));

        // when & then
        assertThrows(InvalidRequestException.class, () -> eventService.createEvent(userId, request));
    }

    @Test
    @DisplayName("행사 상세 조회 성공 - 조회수 증가 확인")
    void getEvent_Success() {
        // given
        Long eventId = 100L;
        Long userId = 1L;
        User host = User.builder().userId(userId).build();
        Event event = Event.builder().eventId(eventId).scope(Scope.GLOBAL).type(EventType.BATTLE).host(host).build();

        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
        given(eventLikeRepository.existsByEventEventIdAndUserUserId(eventId, userId)).willReturn(true);

        // when
        EventResponse response = eventService.getEvent(eventId, userId);

        // then
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.isLiked()).isTrue();
        verify(eventRepository, times(1)).updateViewCount(eventId);
    }

    @Test
    @DisplayName("다가오는 행사 목록 조회 성공 - 좋아요 매핑 확인")
    void getUpcomingEvents_Success() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        User host = User.builder().userId(2L).build();
        Event event = Event.builder().eventId(eventId).scope(Scope.GLOBAL).type(EventType.BATTLE).host(host).build();

        List<Long> myClubIds = List.of(10L);
        given(clubAuthService.findUserClubIds(userId)).willReturn(myClubIds);
        given(eventRepository.findUpcomingEvents(eq(myClubIds), any()))
                .willReturn(List.of(event));
        given(eventLikeRepository.findLikedEventIds(any(), eq(userId)))
                .willReturn(Set.of(eventId));

        // when
        List<EventResponse> responses = eventService.getUpcomingEvents(userId);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().isLiked()).isTrue();
    }

    @Test
    @DisplayName("전체 행사 조회 성공")
    void getEvents_Success() {
        // given
        Long userId = 1L;
        Event event1 = Event.builder().eventId(10L).host(User.builder().userId(2L).build())
                .title("").content("").scope(Scope.GLOBAL).type(EventType.BATTLE).capacity(20L)
                .startsAt(LocalDateTime.now()).endsAt(LocalDateTime.now().plusHours(1)).build();
        Event event2 = Event.builder().eventId(20L).host(User.builder().userId(2L).build())
                .title("").content("").scope(Scope.GLOBAL).type(EventType.BATTLE).capacity(20L)
                .startsAt(LocalDateTime.now()).endsAt(LocalDateTime.now().plusHours(1)).build();

        List<Long> myClubIds = List.of(100L);
        given(clubAuthService.findUserClubIds(userId)).willReturn(myClubIds);
        given(eventRepository.findAllEvents(myClubIds)).willReturn(List.of(event1, event2));
        given(eventLikeRepository.findLikedEventIds(any(), eq(userId))).willReturn(Set.of(10L));

        // when
        List<EventResponse> responses = eventService.getEvents(userId);

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).eventId()).isEqualTo(10L);
        assertThat(responses.get(0).isLiked()).isTrue();
        assertThat(responses.get(1).isLiked()).isFalse();
    }

    @Test
    @DisplayName("행사 수정 성공")
    void updateEvent_Success() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        User host = User.builder().userId(userId).build();
        Event realEvent = Event.builder()
                .eventId(eventId)
                .host(host)
                .scope(Scope.GLOBAL)
                .type(EventType.WORKSHOP)
                .title("Old Title")
                .content("Old Content")
                .build();

        Event event = spy(realEvent);

        given(event.getHost()).willReturn(host);
        given(event.getEventId()).willReturn(eventId);

        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        EventUpdateRequest request = new EventUpdateRequest(
                "New Title", "New Content", null, null, null, null, null, null, 100L, null, null
        );

        // when
        eventService.updateEvent(userId, eventId, request);

        // then
        verify(event).updateEvent(
                eq("New Title"), eq("New Content"), any(), any(), any(), any(), eq(100L), any(), any()
        );
        verify(fileStorageService).processImageUpdate(eq(event), any(), any());
    }

    @Test
    @DisplayName("행사 수정 실패 - 주최자가 아님")
    void updateEvent_Fail_NotHost() {
        // given
        Long userId = 1L;
        Long hostId = 2L;
        Long eventId = 100L;

        User host = User.builder().userId(hostId).build();
        Event event = Event.builder().eventId(eventId).host(host).build();

        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        EventUpdateRequest request = new EventUpdateRequest(
                "Title", "Content", null, null, null, null, null, null, 100L, null, null
        );

        // when & then
        assertThrows(AccessDeniedException.class, () ->
                eventService.updateEvent(userId, eventId, request)
        );
    }

    @Test
    @DisplayName("행사 삭제 성공 - Soft Delete")
    void deleteEvent_Success() {
        // given
        Long userId = 1L;
        Long eventId = 100L;
        User host = User.builder().userId(userId).build();
        Event event = spy(Event.builder().eventId(eventId).host(host).build());

        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        // when
        eventService.deleteEvent(userId, eventId);

        // then
        verify(event).delete();
        verify(eventJoinRepository).softDeleteByEventId(eventId, EventJoinStatus.CANCELED);
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("행사 삭제 실패 - 주최자가 아님")
    void deleteEvent_Fail_NotHost() {
        // given
        Long userId = 1L;
        Long hostId = 2L;
        Long eventId = 100L;

        User host = User.builder().userId(hostId).build();
        Event event = Event.builder().eventId(eventId).host(host).build();

        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        // when & then
        assertThrows(AccessDeniedException.class, () ->
                eventService.deleteEvent(userId, eventId)
        );
    }

    @Test
    @DisplayName("유저 탈퇴 시 주최한 행사 Soft Delete")
    void softDeleteByUserId() {
        // given
        Long userId = 1L;

        // when
        eventService.softDeleteByUserId(userId);

        // then
        verify(eventRepository).softDeleteByUserId(userId);
    }

    @Test
    @DisplayName("클럽 삭제 시 연관된 행사 Soft Delete")
    void softDeleteByClubId() {
        // given
        Long clubId = 10L;

        // when
        eventService.softDeleteByClubId(clubId);

        // then
        verify(eventRepository).softDeleteByClubId(clubId);
    }
}