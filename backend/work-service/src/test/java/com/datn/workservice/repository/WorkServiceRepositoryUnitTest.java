package com.datn.workservice.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkServiceRepositoryUnitTest {

    @Test
    @DisplayName("Verify all 33 Repositories are properly annotated with @Repository and extend JpaRepository")
    void testAllRepositoriesContract() {
        List<Class<?>> repositories = List.of(
                ProjectRepository.class,
                TeamRepository.class,
                TeamMemberRepository.class,
                ProjectRoleRepository.class,
                RoleAssignmentRepository.class,
                PermissionSchemeEntryRepository.class,
                WorkItemTypeRepository.class,
                StatusRepository.class,
                WorkflowRepository.class,
                WorkflowTransitionRepository.class,
                WorkflowSchemeRepository.class,
                WorkflowSchemeMappingRepository.class,
                BoardRepository.class,
                BoardColumnRepository.class,
                BoardColumnStatusRepository.class,
                BoardSwimlaneRepository.class,
                SprintRepository.class,
                SprintReportSnapshotRepository.class,
                WorkItemRepository.class,
                WorkItemLinkRepository.class,
                WorkItemCommentRepository.class,
                WorkItemAttachmentRepository.class,
                WorkItemChangelogRepository.class,
                ComponentRepository.class,
                VersionRepository.class,
                LabelRepository.class,
                WorkItemComponentMapRepository.class,
                WorkItemLabelMapRepository.class,
                CustomFieldDefinitionRepository.class,
                SavedFilterRepository.class,
                AutomationRuleRepository.class,
                OutboxEventRepository.class,
                ProjectCloneRepository.class
        );

        assertEquals(33, repositories.size(), "Should have exactly 33 repository interfaces implemented");

        for (Class<?> repo : repositories) {
            assertTrue(repo.isInterface(), repo.getSimpleName() + " must be an interface");
            assertTrue(repo.isAnnotationPresent(Repository.class), repo.getSimpleName() + " must be annotated with @Repository");
            assertTrue(JpaRepository.class.isAssignableFrom(repo), repo.getSimpleName() + " must extend JpaRepository");
        }
    }

    @Test
    @DisplayName("Verify JpaSpecificationExecutor is implemented for dynamic filterable repositories")
    void testSpecificationExecutors() {
        List<Class<?>> specRepos = List.of(
                ProjectRepository.class,
                TeamRepository.class,
                WorkItemRepository.class,
                SprintRepository.class,
                BoardRepository.class,
                WorkflowRepository.class,
                StatusRepository.class,
                ComponentRepository.class,
                VersionRepository.class,
                LabelRepository.class,
                WorkItemTypeRepository.class,
                CustomFieldDefinitionRepository.class,
                SavedFilterRepository.class,
                AutomationRuleRepository.class,
                WorkItemChangelogRepository.class
        );

        for (Class<?> repo : specRepos) {
            assertTrue(JpaSpecificationExecutor.class.isAssignableFrom(repo),
                    repo.getSimpleName() + " must extend JpaSpecificationExecutor for dynamic JPA Specification queries");
        }
    }

    @Test
    @DisplayName("Verify key query methods exist on WorkItemRepository")
    void testWorkItemRepositoryMethods() {
        List<String> methodNames = Arrays.stream(WorkItemRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertTrue(methodNames.contains("findByCode"));
        assertTrue(methodNames.contains("findByProjectIdAndCode"));
        assertTrue(methodNames.contains("findBySprintId"));
        assertTrue(methodNames.contains("findBySprintIdOrderByRankAsc"));
        assertTrue(methodNames.contains("findByParentId"));
        assertTrue(methodNames.contains("findActiveByProjectIdOrderByRankAsc"));
        assertTrue(methodNames.contains("countBySprintId"));
    }

    @Test
    @DisplayName("Verify key query methods exist on ProjectRepository and TeamRepository")
    void testProjectAndTeamRepositoryMethods() {
        List<String> projectMethodNames = Arrays.stream(ProjectRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertTrue(projectMethodNames.contains("findByKey"));
        assertTrue(projectMethodNames.contains("findByKeyIgnoreCase"));
        assertTrue(projectMethodNames.contains("existsByKey"));
        assertTrue(projectMethodNames.contains("findByWorkspaceId"));
        assertTrue(projectMethodNames.contains("findByTeamId"));

        List<String> teamMethodNames = Arrays.stream(TeamRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertTrue(teamMethodNames.contains("findByWorkspaceId"));
        assertTrue(teamMethodNames.contains("findByOwnerId"));
    }

    @Test
    @DisplayName("Verify key query methods exist on Sprint, Board and Workflow repositories")
    void testSprintBoardWorkflowRepositoryMethods() {
        List<String> sprintMethods = Arrays.stream(SprintRepository.class.getMethods())
                .map(Method::getName)
                .toList();
        assertTrue(sprintMethods.contains("findByProjectId"));
        assertTrue(sprintMethods.contains("findByProjectIdAndState"));

        List<String> boardMethods = Arrays.stream(BoardRepository.class.getMethods())
                .map(Method::getName)
                .toList();
        assertTrue(boardMethods.contains("findByProjectId"));
        assertTrue(boardMethods.contains("findByProjectIdAndType"));

        List<String> workflowMethods = Arrays.stream(WorkflowTransitionRepository.class.getMethods())
                .map(Method::getName)
                .toList();
        assertTrue(workflowMethods.contains("findByWorkflowId"));
        assertTrue(workflowMethods.contains("findByWorkflowIdAndFromStatusId"));
    }
}
