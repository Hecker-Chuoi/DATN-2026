package com.datn.workservice.dto;

import com.datn.workservice.dto.model.WorkflowConditionConfig;
import com.datn.workservice.dto.model.WorkflowPostFunctionConfig;
import com.datn.workservice.dto.model.WorkflowValidatorConfig;
import com.datn.workservice.dto.request.*;
import com.datn.workservice.enums.StatusCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateStatusRequest: Valid status passes validation")
    void testCreateStatusRequest_Valid() {
        CreateStatusRequest request = CreateStatusRequest.builder()
                .name("In Code Review")
                .category(StatusCategory.IN_PROGRESS)
                .description("Task is undergoing peer code review")
                .colorHex("#3B82F6")
                .build();

        Set<ConstraintViolation<CreateStatusRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreateStatusRequest: Fails on blank name and null category")
    void testCreateStatusRequest_BlankNameAndNullCategory() {
        CreateStatusRequest request = CreateStatusRequest.builder()
                .name("  ")
                .category(null)
                .colorHex("invalid-hex")
                .build();

        Set<ConstraintViolation<CreateStatusRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(3);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("colorHex"));
    }

    @Test
    @DisplayName("CreateStatusRequest: Accepts 3-digit and 6-digit hex color format")
    void testCreateStatusRequest_HexColors() {
        CreateStatusRequest request3Hex = CreateStatusRequest.builder()
                .name("Done")
                .category(StatusCategory.DONE)
                .colorHex("#FFF")
                .build();
        assertThat(validator.validate(request3Hex)).isEmpty();

        CreateStatusRequest request6Hex = CreateStatusRequest.builder()
                .name("To Do")
                .category(StatusCategory.TODO)
                .colorHex("#94A3B8")
                .build();
        assertThat(validator.validate(request6Hex)).isEmpty();
    }

    @Test
    @DisplayName("CreateWorkflowRequest: Valid workflow passes validation")
    void testCreateWorkflowRequest_Valid() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .projectId(UUID.randomUUID())
                .name("Custom Bug Lifecycle Workflow")
                .description("Dedicated workflow for bugs and incidents")
                .isDefault(false)
                .build();

        Set<ConstraintViolation<CreateWorkflowRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreateWorkflowRequest: Fails on blank name")
    void testCreateWorkflowRequest_BlankName() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .name("")
                .build();

        Set<ConstraintViolation<CreateWorkflowRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Tên quy trình không được để trống");
    }

    @Test
    @DisplayName("CreateWorkflowTransitionRequest: Valid transition with JSONB configs passes")
    void testCreateWorkflowTransitionRequest_Valid() {
        WorkflowConditionConfig condition = WorkflowConditionConfig.builder()
                .type("PERMISSION_REQUIRED")
                .params(Map.of("permissionKey", "ISSUE_TRANSITION"))
                .build();

        WorkflowValidatorConfig wfValidator = WorkflowValidatorConfig.builder()
                .type("FIELD_REQUIRED")
                .params(Map.of("fields", List.of("resolution")))
                .build();

        WorkflowPostFunctionConfig postFunction = WorkflowPostFunctionConfig.builder()
                .type("SET_RESOLVED_TIME")
                .params(Map.of())
                .build();

        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .workflowId(UUID.randomUUID())
                .fromStatusId(UUID.randomUUID())
                .toStatusId(UUID.randomUUID())
                .name("Resolve Issue")
                .conditionsConfig(List.of(Map.of("type", condition.getType())))
                .validatorsConfig(List.of(Map.of("type", wfValidator.getType(), "params", wfValidator.getParams())))
                .postFunctionsConfig(List.of(Map.of("type", postFunction.getType(), "params", postFunction.getParams())))
                .build();

        Set<ConstraintViolation<CreateWorkflowTransitionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreateWorkflowTransitionRequest: Fails on null toStatusId or blank name")
    void testCreateWorkflowTransitionRequest_MissingFields() {
        CreateWorkflowTransitionRequest request = CreateWorkflowTransitionRequest.builder()
                .toStatusId(null)
                .name("  ")
                .build();

        Set<ConstraintViolation<CreateWorkflowTransitionRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(2);
    }

    @Test
    @DisplayName("ExecuteTransitionRequest: Valid request passes, null transitionId fails")
    void testExecuteTransitionRequest_Validation() {
        ExecuteTransitionRequest validReq = ExecuteTransitionRequest.builder()
                .transitionId(UUID.randomUUID())
                .resolution("FIXED")
                .comment("Fixed in commit abc1234")
                .fields(Map.of("fixVersionId", UUID.randomUUID().toString()))
                .build();
        assertThat(validator.validate(validReq)).isEmpty();

        ExecuteTransitionRequest invalidReq = ExecuteTransitionRequest.builder()
                .transitionId(null)
                .build();
        Set<ConstraintViolation<ExecuteTransitionRequest>> violations = validator.validate(invalidReq);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("ID bước chuyển");
    }

    @Test
    @DisplayName("CreateWorkflowSchemeRequest: Valid scheme with type mappings passes")
    void testCreateWorkflowSchemeRequest_Valid() {
        CreateWorkflowSchemeRequest.WorkflowSchemeMappingItem mapping =
                CreateWorkflowSchemeRequest.WorkflowSchemeMappingItem.builder()
                        .workItemTypeId(UUID.randomUUID())
                        .workflowId(UUID.randomUUID())
                        .build();

        CreateWorkflowSchemeRequest request = CreateWorkflowSchemeRequest.builder()
                .projectId(UUID.randomUUID())
                .name("E-Commerce Project Scheme")
                .description("Main scheme mapping Story/Task/Bug to respective workflows")
                .mappings(List.of(mapping))
                .build();

        Set<ConstraintViolation<CreateWorkflowSchemeRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
