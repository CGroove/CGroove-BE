package com.example.cgroove.validation;

import com.example.cgroove.dto.post.PostCreateRequest;
import com.example.cgroove.enums.Scope;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PostValidator implements ConstraintValidator<ValidScopePost, PostCreateRequest> {

    @Override
    public boolean isValid(PostCreateRequest postCreateRequest, ConstraintValidatorContext context) {
        // 미입력은 @NotBlank가 검증
        if (postCreateRequest.getScope() == null) {
            return true;
        }

        try {
            Scope.valueOf(postCreateRequest.getScope().toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        if ("CLUB".equalsIgnoreCase(postCreateRequest.getScope()) && postCreateRequest.getClubId() == null) {
            return false;
        }

        return true;
    }
}
