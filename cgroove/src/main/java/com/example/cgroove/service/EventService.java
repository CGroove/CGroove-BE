package com.example.cgroove.service;

import com.example.cgroove.dto.event.EventCreateRequest;
import com.example.cgroove.dto.event.EventResponse;
import com.example.cgroove.dto.event.EventUpdateRequest;
import com.example.cgroove.entity.Club;
import com.example.cgroove.entity.Event;
import com.example.cgroove.entity.User;
import com.example.cgroove.enums.EventJoinStatus;
import com.example.cgroove.enums.EventType;
import com.example.cgroove.enums.Scope;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.exception.InvalidRequestException;
import com.example.cgroove.exception.NotFoundException;
import com.example.cgroove.repository.EventJoinRepository;
import com.example.cgroove.repository.EventLikeRepository;
import com.example.cgroove.repository.EventRepository;
import com.example.cgroove.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventJoinRepository eventJoinRepository;
    private final EventLikeRepository eventLikeRepository;
    private final ClubAuthService clubAuthService;
    private final FileStorageService fileStorageService;
    private final EntityManager em;

    @Transactional
    public EventResponse createEvent(Long userId, EventCreateRequest request) {
        User host = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        Scope scope;
        try {
            scope = Scope.valueOf(request.getScope().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException("잘못된 공개 범위입니다. (GLOBAL, CLUB 중 선택)");
        }

        Club club = null;
        if (scope == Scope.CLUB) {
            Long clubId = request.getClubId();
            if (clubId == null) {
                throw new InvalidRequestException("공개 범위가 CLUB일 경우 clubId가 필요합니다.");
            }
            club = clubAuthService.findByClubId(clubId);
        }

        Event event = Event.builder()
                .host(host)
                .scope(scope)
                .club(club)
                .type(EventType.valueOf(request.getType().toUpperCase()))
                .title(request.getTitle())
                .content(request.getContent())
                .tags(request.getTags())
                .images(request.getImages())
                .locationName(request.getLocationName())
                .locationAddress(request.getLocationAddress())
                .locationLink(request.getLocationLink())
                .capacity(request.getCapacity())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .build();

        return EventResponse.from(eventRepository.save(event), false);
    }

    @Transactional
    public EventResponse getEvent(Long eventId, Long userId) {
        Event event = getActiveEvent(eventId);
        eventRepository.updateViewCount(eventId);

        boolean isLiked = userId != null && eventLikeRepository.existsByEventEventIdAndUserUserId(eventId, userId);

        return EventResponse.from(event, isLiked);
    }
    public List<EventResponse> getEvents(Long userId) {
        List<Long> myClubIds = clubAuthService.findUserClubIds(userId);
        List<Event> events = eventRepository.findAllEvents(myClubIds);
        return convertToResponses(events, userId);
    }
    public List<EventResponse> getUpcomingEvents(Long userId) {
        List<Long> myClubIds = clubAuthService.findUserClubIds(userId);
        Pageable pageable = PageRequest.of(0, 10);

        List<Event> events = eventRepository.findUpcomingEvents(myClubIds, pageable);
        return convertToResponses(events, userId);
    }
    private List<EventResponse> convertToResponses(List<Event> events, Long userId) {
        if (events.isEmpty()) {
            return List.of();
        }

        Set<Long> likedEventIds = new HashSet<>();
        if (userId != null) {
            List<Long> eventIds = events.stream().map(Event::getEventId).toList();
            likedEventIds = eventLikeRepository.findLikedEventIds(eventIds, userId);
        }

        Set<Long> finalLikedEventIds = likedEventIds;

        return events.stream()
                .map(event -> EventResponse.from(event, finalLikedEventIds.contains(event.getEventId())))
                .toList();
    }

    @Transactional
    public EventResponse updateEvent(Long userId, Long eventId, EventUpdateRequest request) {
        Event event = getActiveEvent(eventId);
        checkHost(userId, event);

        event.updateEvent(
                request.getTitle(), request.getContent(), request.getTags(),
                request.getLocationName(), request.getLocationAddress(),
                request.getLocationLink(), request.getCapacity(),
                request.getStartsAt(), request.getEndsAt()
        );

        fileStorageService.processImageUpdate(event, request.getNewImagePaths(), request.getKeepImages());
        boolean isLiked = eventLikeRepository.existsByEventEventIdAndUserUserId(eventId, userId);

        return EventResponse.from(event, isLiked);
    }

    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        Event event = getActiveEvent(eventId);
        checkHost(userId, event);

        if (event.getImages() != null && !event.getImages().isEmpty()) {
            for (String imagePath : event.getImages()) {
                fileStorageService.deleteFile(imagePath);
            }
        }

        eventJoinRepository.softDeleteByEventId(eventId, EventJoinStatus.CANCELED);
        event.delete();

        em.flush();
        em.clear();
    }

    public Event getActiveEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("행사 조회 실패"));
    }
    private void checkHost(Long userId, Event event) {
        if (!event.getHost().getUserId().equals(userId)) {
            throw new AccessDeniedException("권한이 없습니다");
        }
    }

    public void softDeleteByUserId(Long userId) {
        eventRepository.softDeleteByUserId(userId);
    }
    public void softDeleteByClubId(Long clubId) { eventRepository.softDeleteByClubId(clubId); }
}