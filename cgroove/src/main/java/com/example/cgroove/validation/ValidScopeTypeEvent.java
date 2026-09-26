package com.example.cgroove.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventValidator.class)
@Documented
public @interface ValidScopeTypeEvent {
    String message() default "행사 범위 및 종류 입력 오류";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}