package com.example.cgroove.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PostValidator.class)
@Documented
public @interface ValidScopePost {
    String message() default "게시글 범위 입력 오류";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}