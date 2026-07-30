package com.huanjing.geo.module.customer.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BrandRequestValidationTest {

    private static final Set<String> PROFILE_DESCRIPTION_FIELDS = Set.of(
            "description",
            "businessIntro",
            "brandQualificationDescription",
            "brandCaseDescription"
    );
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequestAllowsProfileDescriptionsUpToOneThousandCharacters() {
        BrandCreateRequest request = new BrandCreateRequest();
        applyProfileDescriptions(request, "字".repeat(1000));

        assertThat(profileDescriptionViolations(request)).isEmpty();
    }

    @Test
    void createRequestRejectsProfileDescriptionsOverOneThousandCharacters() {
        BrandCreateRequest request = new BrandCreateRequest();
        applyProfileDescriptions(request, "字".repeat(1001));

        assertThat(profileDescriptionViolations(request)).containsExactlyInAnyOrderElementsOf(PROFILE_DESCRIPTION_FIELDS);
    }

    @Test
    void updateRequestAllowsProfileDescriptionsUpToOneThousandCharacters() {
        BrandUpdateRequest request = new BrandUpdateRequest();
        applyProfileDescriptions(request, "字".repeat(1000));

        assertThat(profileDescriptionViolations(request)).isEmpty();
    }

    @Test
    void updateRequestRejectsProfileDescriptionsOverOneThousandCharacters() {
        BrandUpdateRequest request = new BrandUpdateRequest();
        applyProfileDescriptions(request, "字".repeat(1001));

        assertThat(profileDescriptionViolations(request)).containsExactlyInAnyOrderElementsOf(PROFILE_DESCRIPTION_FIELDS);
    }

    private void applyProfileDescriptions(BrandCreateRequest request, String value) {
        request.setDescription(value);
        request.setBusinessIntro(value);
        request.setBrandQualificationDescription(value);
        request.setBrandCaseDescription(value);
    }

    private void applyProfileDescriptions(BrandUpdateRequest request, String value) {
        request.setDescription(value);
        request.setBusinessIntro(value);
        request.setBrandQualificationDescription(value);
        request.setBrandCaseDescription(value);
    }

    private Set<String> profileDescriptionViolations(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .filter(PROFILE_DESCRIPTION_FIELDS::contains)
                .collect(Collectors.toSet());
    }
}
