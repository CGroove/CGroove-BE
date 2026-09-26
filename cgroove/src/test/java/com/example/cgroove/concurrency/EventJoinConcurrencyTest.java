package com.example.cgroove.concurrency;

import com.example.cgroove.entity.Event;
import com.example.cgroove.entity.User;
import com.example.cgroove.enums.EventJoinStatus;
import com.example.cgroove.enums.EventType;
import com.example.cgroove.enums.Scope;
import com.example.cgroove.exception.ConflictException;
import com.example.cgroove.repository.EventJoinRepository;
import com.example.cgroove.repository.EventRepository;
import com.example.cgroove.repository.UserRepository;
import com.example.cgroove.service.EventJoinService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정원이 있는 행사에 여러 요청이 동시에 들어와도 정원만큼만 확정되는지 검증한다.
 * 스레드마다 별도 트랜잭션이 필요하므로 @Transactional 없이 실행하고, 전용 H2 DB를 쓴다.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class EventJoinConcurrencyTest {

    private static final int CAPACITY = 10;
    private static final int APPLICANTS = 50;

    @Autowired
    private EventJoinService eventJoinService;

    @Autowired
    private EventJoinRepository eventJoinRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        eventJoinRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("정원 10명 행사에 50명이 동시에 신청하면 정확히 10명만 확정된다")
    void applyEvent_Concurrently_ConfirmsOnlyCapacity() throws InterruptedException {
        // given
        User host = userRepository.save(user("host"));
        Long eventId = eventRepository.save(Event.builder()
                .host(host).scope(Scope.GLOBAL).type(EventType.WORKSHOP)
                .title("선착순 워크숍").content("정원 테스트")
                .capacity((long) CAPACITY)
                .startsAt(LocalDateTime.now().plusDays(1)).endsAt(LocalDateTime.now().plusDays(1).plusHours(2))
                .likeCount(0L).viewCount(0L)
                .build()).getEventId();
        List<Long> applicantIds = IntStream.range(0, APPLICANTS)
                .mapToObj(i -> userRepository.save(user("applicant" + i)).getUserId())
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(APPLICANTS);
        CountDownLatch ready = new CountDownLatch(APPLICANTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(APPLICANTS);
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        // when: 모든 스레드가 준비된 뒤 한 번에 신청
        for (Long applicantId : applicantIds) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    eventJoinService.applyEvent(applicantId, eventId);
                    confirmed.incrementAndGet();
                } catch (ConflictException e) {
                    soldOut.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        // then
        assertThat(finished).isTrue();
        assertThat(unexpected).isEmpty();
        assertThat(confirmed.get()).isEqualTo(CAPACITY);
        assertThat(soldOut.get()).isEqualTo(APPLICANTS - CAPACITY);
        assertThat(eventJoinRepository.countByEvent_EventIdAndStatus(eventId, EventJoinStatus.CONFIRMED))
                .isEqualTo(CAPACITY);
    }

    private User user(String name) {
        return User.builder()
                .email(name + "@test.com")
                .password("password")
                .nickname(name)
                .build();
    }
}
