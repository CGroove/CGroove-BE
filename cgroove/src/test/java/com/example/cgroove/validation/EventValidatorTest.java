package com.example.cgroove.validation;

import com.example.cgroove.dto.event.EventCreateRequest;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EventValidatorTest {

    private final EventValidator validator = new EventValidator();

    @Mock
    private ConstraintValidatorContext context;

    @Test
    @DisplayName("유효성 검사 성공 - GLOBAL 범위, 정상 Type")
    void isValid_Success_Global() {
        // given
        EventCreateRequest request = EventCreateRequest.builder()
                .scope("GLOBAL")
                .type("WORKSHOP")
                .clubId(null)
                .build();

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("유효성 검사 성공 - CLUB 범위, ClubId 포함")
    void isValid_Success_Club() {
        // given
        EventCreateRequest request = EventCreateRequest.builder()
                .scope("CLUB")
                .type("BATTLE")
                .clubId(10L)
                .build();

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("유효성 검사 실패 - 잘못된 Scope 값")
    void isValid_Fail_InvalidScope() {
        // given
        EventCreateRequest request = EventCreateRequest.builder()
                .scope("INVALID_SCOPE")
                .type("WORKSHOP")
                .build();

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("유효성 검사 실패 - 잘못된 Type 값")
    void isValid_Fail_InvalidType() {
        // given
        EventCreateRequest request = EventCreateRequest.builder()
                .scope("GLOBAL")
                .type("UNKNOWN_TYPE")
                .build();

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("유효성 검사 실패 - CLUB인데 ClubId 누락")
    void isValid_Fail_ClubIdMissing() {
        // given
        EventCreateRequest request = EventCreateRequest.builder()
                .scope("CLUB")
                .type("JAM")
                .clubId(null)
                .build();

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Scope · Type 미입력은 통과 - 미입력 검증은 @NotBlank가 담당")
    void isValid_NullScopeOrType_DelegatesToNotBlank() {
        // given
        EventCreateRequest noScope = EventCreateRequest.builder().type("WORKSHOP").build();
        EventCreateRequest noType = EventCreateRequest.builder().scope("GLOBAL").build();

        // when & then
        assertThat(validator.isValid(noScope, context)).isTrue();
        assertThat(validator.isValid(noType, context)).isTrue();
    }
}
