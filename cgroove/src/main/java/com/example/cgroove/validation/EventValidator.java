package com.example.cgroove.validation;

import com.example.cgroove.dto.event.EventCreateRequest;
import com.example.cgroove.enums.EventType;
import com.example.cgroove.enums.Scope;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventValidator implements ConstraintValidator<ValidScopeTypeEvent, EventCreateRequest> {

    @Override
    public boolean isValid(EventCreateRequest eventCreateRequest, ConstraintValidatorContext context) {
        // 미입력은 @NotBlank가 검증
        if (eventCreateRequest.getScope() == null || eventCreateRequest.getType() == null) {
            return true;
        }

        try {
            Scope.valueOf(eventCreateRequest.getScope().toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        try {
            EventType.valueOf(eventCreateRequest.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        if ("CLUB".equalsIgnoreCase(eventCreateRequest.getScope()) && eventCreateRequest.getClubId() == null) {
            return false;
        }

        return true;
    }
}