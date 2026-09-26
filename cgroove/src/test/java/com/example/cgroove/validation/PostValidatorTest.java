package com.example.cgroove.validation;

import com.example.cgroove.dto.post.PostCreateRequest;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PostValidatorTest {

    private final PostValidator validator = new PostValidator();

    @Mock
    private ConstraintValidatorContext context;

    @Test
    @DisplayName("유효성 검사 성공 - GLOBAL 범위")
    void isValid_Success_Global() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "GLOBAL", null, "T", "C", null, null
        );

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("유효성 검사 성공 - CLUB 범위, ClubId 포함")
    void isValid_Success_Club() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "CLUB", 100L, "T", "C", null, null
        );

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("유효성 검사 실패 - 잘못된 Scope")
    void isValid_Fail_InvalidScope() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "STRANGE_SCOPE", null, "T", "C", null, null
        );

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("유효성 검사 실패 - CLUB인데 ClubId 누락")
    void isValid_Fail_ClubIdMissing() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                "CLUB", null, "T", "C", null, null
        );

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Scope 미입력은 통과 - 미입력 검증은 @NotBlank가 담당")
    void isValid_NullScope_DelegatesToNotBlank() {
        // given
        PostCreateRequest request = new PostCreateRequest(
                null, null, "T", "C", null, null
        );

        // when
        boolean result = validator.isValid(request, context);

        // then
        assertThat(result).isTrue();
    }
}
