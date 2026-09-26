package com.example.cgroove.performance;

import com.example.cgroove.entity.Club;
import com.example.cgroove.entity.Comment;
import com.example.cgroove.entity.Event;
import com.example.cgroove.entity.Post;
import com.example.cgroove.entity.User;
import com.example.cgroove.enums.ClubJoinStatus;
import com.example.cgroove.enums.ClubRole;
import com.example.cgroove.enums.ClubType;
import com.example.cgroove.enums.EventType;
import com.example.cgroove.enums.Scope;
import com.example.cgroove.repository.ClubRepository;
import com.example.cgroove.repository.CommentRepository;
import com.example.cgroove.repository.EventRepository;
import com.example.cgroove.repository.PostRepository;
import com.example.cgroove.repository.UserRepository;
import com.example.cgroove.security.UserDetail;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 목록 API 한 번에 실행되는 SQL 수를 Hibernate Statistics로 센다.
 * 항목 수가 늘어도 쿼리 수가 늘지 않아야 N+1이 없다고 본다.
 * 응답 직렬화 중의 지연 로딩까지 세기 위해 서비스가 아닌 API(MockMvc)를 호출한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:querycount;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.default_batch_fetch_size=50", // 운영(application.yml)과 같은 값
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=warn"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ListQueryCountTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CommentRepository commentRepository;

    private Statistics statistics;
    private User viewer;
    private Club club;
    private int seq;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        viewer = userRepository.save(newUser("viewer"));
        Club newClub = Club.builder().clubName("Viewer Crew").clubType(ClubType.CREW).intro("intro").build();
        newClub.addMember(viewer, ClubRole.MEMBER, ClubJoinStatus.ACTIVE);
        club = clubRepository.save(newClub);
    }

    @Test
    @DisplayName("게시글 목록: 게시글이 10개에서 20개로 늘어도 쿼리 수가 같다")
    void getPosts_QueryCountDoesNotGrowWithPosts() throws Exception {
        seedPosts(10);
        long queriesFor10 = countQueries("/posts", 10);

        seedPosts(10);
        long queriesFor20 = countQueries("/posts", 20);

        System.out.printf("[QueryCount] GET /posts : 10개 → %d쿼리, 20개 → %d쿼리%n", queriesFor10, queriesFor20);
        assertThat(queriesFor20).isEqualTo(queriesFor10);
    }

    @Test
    @DisplayName("행사 목록: 행사가 10개에서 20개로 늘어도 쿼리 수가 같다")
    void getEvents_QueryCountDoesNotGrowWithEvents() throws Exception {
        seedEvents(10);
        long queriesFor10 = countQueries("/events", 10);

        seedEvents(10);
        long queriesFor20 = countQueries("/events", 20);

        System.out.printf("[QueryCount] GET /events : 10개 → %d쿼리, 20개 → %d쿼리%n", queriesFor10, queriesFor20);
        assertThat(queriesFor20).isEqualTo(queriesFor10);
    }

    private long countQueries(String url, int expectedSize) throws Exception {
        UserDetail principal = new UserDetail(
                viewer.getUserId(), viewer.getEmail(), viewer.getNickname(), null, viewer.getPassword());

        statistics.clear();
        mockMvc.perform(get(url).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(expectedSize));
        return statistics.getPrepareStatementCount();
    }

    // 작성자가 모두 다르고, 절반은 동아리 전용 · 태그 · 이미지 · 댓글이 있는 게시글
    private void seedPosts(int count) {
        for (int i = 0; i < count; i++) {
            User author = userRepository.save(newUser("author"));
            boolean clubOnly = i % 2 == 0;
            Post post = postRepository.save(Post.builder()
                    .author(author)
                    .scope(clubOnly ? Scope.CLUB : Scope.GLOBAL)
                    .club(clubOnly ? club : null)
                    .title("post").content("content")
                    .tags(List.of("hiphop", "locking"))
                    .images(List.of("post.jpg"))
                    .likeCount(0L).viewCount(0L)
                    .build());
            commentRepository.save(Comment.builder().user(author).post(post).content("comment").build());
        }
    }

    private void seedEvents(int count) {
        for (int i = 0; i < count; i++) {
            User host = userRepository.save(newUser("host"));
            boolean clubOnly = i % 2 == 0;
            Event event = eventRepository.save(Event.builder()
                    .host(host)
                    .scope(clubOnly ? Scope.CLUB : Scope.GLOBAL)
                    .club(clubOnly ? club : null)
                    .type(EventType.WORKSHOP)
                    .title("event").content("content")
                    .tags(List.of("workshop"))
                    .images(List.of("event.jpg"))
                    .capacity(10L)
                    .startsAt(LocalDateTime.now().plusDays(1)).endsAt(LocalDateTime.now().plusDays(1).plusHours(2))
                    .likeCount(0L).viewCount(0L)
                    .build());
            commentRepository.save(Comment.builder().user(host).event(event).content("comment").build());
        }
    }

    private User newUser(String prefix) {
        String name = prefix + (seq++);
        return User.builder().email(name + "@test.com").password("password").nickname(name).build();
    }
}
