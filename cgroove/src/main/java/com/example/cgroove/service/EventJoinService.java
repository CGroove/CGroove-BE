package com.example.cgroove.service;

import com.example.cgroove.dto.eventJoin.EventJoinResponse;
import com.example.cgroove.entity.*;
import com.example.cgroove.enums.EventJoinStatus;
import com.example.cgroove.exception.AccessDeniedException;
import com.example.cgroove.exception.ConflictException;
import com.example.cgroove.exception.InvalidRequestException;
import com.example.cgroove.exception.NotFoundException;
import com.example.cgroove.repository.EventJoinRepository;

import com.example.cgroove.repository.EventRepository;
import com.example.cgroove.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventJoinService {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventJoinRepository eventJoinRepository;

    @Transactional
    public EventJoinResponse applyEvent(Long userId, Long eventId) {
        EventJoin existingJoin = eventJoinRepository
                .findByParticipant_UserIdAndEvent_EventId(userId, eventId)
                .orElse(null);

        if (existingJoin != null) {
            if (existingJoin.getStatus() == EventJoinStatus.CONFIRMED) {
                throw new ConflictException("이미 신청이 완료된 행사입니다.");
            }
        }

        Event event = eventRepository.findWithLockByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("행사를 찾을 수 없습니다"));

        long currentCount = eventJoinRepository.countByEvent_EventIdAndStatus(eventId, EventJoinStatus.CONFIRMED);
        if (currentCount >= event.getCapacity()) {
            throw new ConflictException("선착순 마감되었습니다.");
        }

        if (existingJoin != null) {
            existingJoin.changeStatus(EventJoinStatus.CONFIRMED);
            return EventJoinResponse.from(existingJoin);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        EventJoin newJoin = EventJoin.builder()
                .participant(user)
                .event(event)
                .status(EventJoinStatus.CONFIRMED)
                .build();

        return EventJoinResponse.from(eventJoinRepository.save(newJoin));
    }

    @Transactional
    public void cancelEventJoin(Long userId, Long eventId) {
        EventJoin join = eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("신청 내역이 없습니다"));

        if (join.getStatus() != EventJoinStatus.CONFIRMED) {
            throw new InvalidRequestException("취소할 수 없는 상태입니다");
        }

        join.changeStatus(EventJoinStatus.CANCELED);
    }

    @Transactional
    public void rejectParticipation(Long hostId, Long eventId, Long participantId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("행사를 찾을 수 없습니다"));

        if (!event.getHost().getUserId().equals(hostId)) {
            throw new AccessDeniedException("행사 주최자만 거절할 수 있습니다");
        }

        EventJoin targetJoin = eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(participantId, eventId)
                .orElseThrow(() -> new NotFoundException("해당 유저의 신청 내역이 없습니다"));

        if (targetJoin.getStatus() != EventJoinStatus.CONFIRMED) {
            throw new InvalidRequestException("확정된 신청자만 거절할 수 있습니다");
        }

        targetJoin.changeStatus(EventJoinStatus.REJECTED);
    }

    public EventJoinResponse getJoinStatus(Long userId, Long eventId) {
        return eventJoinRepository.findByParticipant_UserIdAndEvent_EventId(userId, eventId)
                .map(EventJoinResponse::from)
                .orElseThrow(() -> new NotFoundException("신청 이력이 없습니다"));
    }

    public List<EventJoinResponse> getUserEvents(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        return eventJoinRepository.findMyJoinedEvents(userId, EventJoinStatus.CONFIRMED)
                .stream()
                .map(EventJoinResponse::from)
                .toList();
    }

    public List<EventJoinResponse> getEventUsers(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("행사를 찾을 수 없습니다");
        }

        return eventJoinRepository.findParticipantsWithUser(eventId, EventJoinStatus.CONFIRMED)
                .stream().map(EventJoinResponse::from).toList();
    }

    public void softDeleteByUserId(Long userId) {
        eventJoinRepository.softDeleteByUserId(userId, EventJoinStatus.CANCELED);
    }

    public void softDeleteByClubId(Long clubId) {
        eventJoinRepository.softDeleteByClubId(clubId, EventJoinStatus.CANCELED);
    }
}
