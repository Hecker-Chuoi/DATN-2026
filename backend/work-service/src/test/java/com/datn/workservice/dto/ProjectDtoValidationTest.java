package com.datn.workservice.dto;

import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.CreateWorkItemTypeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateProjectRequest: Valid request should have no constraint violations")
    void testValidCreateProjectRequest() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .workspaceId(UUID.randomUUID())
                .name("Core Banking Engine")
                .key("CBE")
                .description("Main transaction processing module")
                .build();

        Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreateProjectRequest: Invalid key pattern should fail validation")
    void testInvalidProjectKeyPattern() {
        // Lowercase key
        CreateProjectRequest requestLower = CreateProjectRequest.builder()
                .workspaceId(UUID.randomUUID())
                .name("Invalid Project")
                .key("cbe")
                .build();

        Set<ConstraintViolation<CreateProjectRequest>> violationsLower = validator.validate(requestLower);
        assertThat(violationsLower).isNotEmpty();
        assertThat(violationsLower).anyMatch(v -> v.getPropertyPath().toString().equals("key"));

        // Starts with number
        CreateProjectRequest requestNum = CreateProjectRequest.builder()
                .workspaceId(UUID.randomUUID())
                .name("Invalid Project")
                .key("1KEY")
                .build();

        Set<ConstraintViolation<CreateProjectRequest>> violationsNum = validator.validate(requestNum);
        assertThat(violationsNum).isNotEmpty();
        assertThat(violationsNum).anyMatch(v -> v.getPropertyPath().toString().equals("key"));

        // Too short key (< 2 chars)
        CreateProjectRequest requestShort = CreateProjectRequest.builder()
                .workspaceId(UUID.randomUUID())
                .name("Invalid Project")
                .key("K")
                .build();

        Set<ConstraintViolation<CreateProjectRequest>> violationsShort = validator.validate(requestShort);
        assertThat(violationsShort).isNotEmpty();

        // Too long key (> 10 chars)
        CreateProjectRequest requestLong = CreateProjectRequest.builder()
                .workspaceId(UUID.randomUUID())
                .name("Invalid Project")
                .key("VERYLONGPROJECTKEY")
                .build();

        Set<ConstraintViolation<CreateProjectRequest>> violationsLong = validator.validate(requestLong);
        assertThat(violationsLong).isNotEmpty();
    }

    @Test
    @DisplayName("CreateProjectRequest: Blank name or null workspaceId should fail validation")
    void testMissingRequiredFields() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .key("VALID")
                .build();

        Set<ConstraintViolation<CreateProjectRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(2);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("workspaceId"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    @DisplayName("CreateWorkItemTypeRequest: Validation for hierarchy level & colorHex")
    void testWorkItemTypeValidation() {
        // Valid WorkItemType
        CreateWorkItemTypeRequest validReq = CreateWorkItemTypeRequest.builder()
                .name("Story")
                .code("STORY")
                .hierarchyLevel(0)
                .colorHex("#4BADE8")
                .build();
        assertThat(validator.validate(validReq)).isEmpty();

        // Invalid Hierarchy Level (> 1)
        CreateWorkItemTypeRequest invalidLevel = CreateWorkItemTypeRequest.builder()
                .name("Super Epic")
                .code("SE")
                .hierarchyLevel(5)
                .build();
        Set<ConstraintViolation<CreateWorkItemTypeRequest>> violationsLevel = validator.validate(invalidLevel);
        assertThat(violationsLevel).anyMatch(v -> v.getPropertyPath().toString().equals("hierarchyLevel"));

        // Invalid Color Hex
        CreateWorkItemTypeRequest invalidHex = CreateWorkItemTypeRequest.builder()
                .name("Bug")
                .code("BUG")
                .hierarchyLevel(0)
                .colorHex("not-a-hex-color")
                .build();
        Set<ConstraintViolation<CreateWorkItemTypeRequest>> violationsHex = validator.validate(invalidHex);
        assertThat(violationsHex).anyMatch(v -> v.getPropertyPath().toString().equals("colorHex"));
    }
}
