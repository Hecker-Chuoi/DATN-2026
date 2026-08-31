package com.datn.workservice.dto;

import com.datn.workservice.dto.request.CreateWorkItemRequest;
import com.datn.workservice.dto.request.UpdateWorkItemRequest;
import com.datn.workservice.dto.request.WorkItemFilterRequest;
import com.datn.workservice.enums.WorkItemPriority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateWorkItemRequest: Valid payload with JSONB customFields passes validation")
    void testCreateWorkItemRequest_Valid() {
        CreateWorkItemRequest request = CreateWorkItemRequest.builder()
                .projectId(UUID.randomUUID())
                .typeId(UUID.randomUUID())
                .summary("Implement OAuth2 JWT Auth Flow")
                .description("Detailed description of OAuth2 implementation")
                .priority(WorkItemPriority.HIGH)
                .storyPoints(new BigDecimal("5.0"))
                .originalEstimateSeconds(28800L)
                .startDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(7))
                .customFields(Map.of("environment", "Staging", "severity", "MAJOR"))
                .build();

        Set<ConstraintViolation<CreateWorkItemRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreateWorkItemRequest: Fails on missing required fields (projectId, typeId, summary)")
    void testCreateWorkItemRequest_MissingRequired() {
        CreateWorkItemRequest request = CreateWorkItemRequest.builder()
                .summary("   ")
                .build();

        Set<ConstraintViolation<CreateWorkItemRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(3);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("projectId"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("typeId"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("summary"));
    }

    @Test
    @DisplayName("CreateWorkItemRequest: Fails on negative story points or negative estimate seconds")
    void testCreateWorkItemRequest_NegativeEstimates() {
        CreateWorkItemRequest request = CreateWorkItemRequest.builder()
                .projectId(UUID.randomUUID())
                .typeId(UUID.randomUUID())
                .summary("Valid Summary")
                .storyPoints(new BigDecimal("-1.0"))
                .originalEstimateSeconds(-3600L)
                .remainingEstimateSeconds(-1800L)
                .build();

        Set<ConstraintViolation<CreateWorkItemRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(3);
    }

    @Test
    @DisplayName("UpdateWorkItemRequest: Valid update payload passes validation")
    void testUpdateWorkItemRequest_Valid() {
        UpdateWorkItemRequest request = UpdateWorkItemRequest.builder()
                .summary("Updated summary")
                .storyPoints(new BigDecimal("8.0"))
                .timeSpentSeconds(7200L)
                .customFields(Map.of("qaVerified", true))
                .build();

        Set<ConstraintViolation<UpdateWorkItemRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("UpdateWorkItemRequest: Fails on story points exceeding 999.99")
    void testUpdateWorkItemRequest_ExceedingStoryPoints() {
        UpdateWorkItemRequest request = UpdateWorkItemRequest.builder()
                .storyPoints(new BigDecimal("1000.00"))
                .build();

        Set<ConstraintViolation<UpdateWorkItemRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("tối đa 999.99");
    }

    @Test
    @DisplayName("WorkItemFilterRequest: Can construct with multi-criteria parameters")
    void testWorkItemFilterRequest_Construct() {
        WorkItemFilterRequest filter = WorkItemFilterRequest.builder()
                .projectId(UUID.randomUUID())
                .keyword("auth")
                .isBacklog(false)
                .isParentNull(true)
                .build();

        assertThat(filter.getProjectId()).isNotNull();
        assertThat(filter.getKeyword()).isEqualTo("auth");
        assertThat(filter.getIsBacklog()).isFalse();
        assertThat(filter.getIsParentNull()).isTrue();
    }
}
