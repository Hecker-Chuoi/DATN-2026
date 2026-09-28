package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ConflictException;
import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.BoardColumnConfigRequest;
import com.datn.workservice.dto.request.BoardSwimlaneConfigRequest;
import com.datn.workservice.dto.request.CreateBoardRequest;
import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.request.MoveCardRequest;
import com.datn.workservice.dto.request.UpdateBoardColumnsRequest;
import com.datn.workservice.dto.request.UpdateBoardRequest;
import com.datn.workservice.dto.request.UpdateBoardSwimlanesRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.dto.response.BoardColumnResponse;
import com.datn.workservice.dto.response.BoardDataResponse;
import com.datn.workservice.dto.response.BoardResponse;
import com.datn.workservice.dto.response.BoardSwimlaneResponse;
import com.datn.workservice.dto.response.MoveCardResponse;
import com.datn.workservice.dto.response.StatusResponse;
import com.datn.workservice.entity.Board;
import com.datn.workservice.entity.BoardColumn;
import com.datn.workservice.entity.BoardColumnStatus;
import com.datn.workservice.entity.BoardSwimlane;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.Workflow;
import com.datn.workservice.entity.WorkflowTransition;
import com.datn.workservice.enums.BoardType;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.SwimlaneStrategy;
import com.datn.workservice.repository.BoardColumnRepository;
import com.datn.workservice.repository.BoardColumnStatusRepository;
import com.datn.workservice.repository.BoardRepository;
import com.datn.workservice.repository.BoardSwimlaneRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.repository.WorkflowTransitionRepository;
import com.datn.workservice.service.BoardService;
import com.datn.workservice.service.LexoRankService;
import com.datn.workservice.service.SprintService;
import com.datn.workservice.service.WorkflowEngineService;
import com.datn.workservice.service.board.SwimlaneGrouper;
import com.datn.workservice.service.board.WIPLimitGuard;
import com.datn.workservice.service.mapper.CardMappingContext;
import com.datn.workservice.service.mapper.WorkItemCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final BoardSwimlaneRepository boardSwimlaneRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final StatusRepository statusRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowEngineService workflowEngineService;
    private final LexoRankService lexoRankService;
    private final WorkItemCardMapper cardMapper;
    private final WIPLimitGuard wipLimitGuard;
    private final SwimlaneGrouper swimlaneGrouper;
    private final SprintService sprintService;

    // ───────────────────────────── CRUD ─────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<BoardResponse> listByProject(UUID projectId) {
        requireProject(projectId);
        return boardRepository.findByProjectId(projectId).stream()
                .map(this::toBoardResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BoardResponse getById(UUID boardId) {
        return toBoardResponse(requireBoard(boardId));
    }

    @Override
    @Transactional
    public BoardResponse create(UUID projectId, CreateBoardRequest request, UUID userId) {
        Project project = requireProject(projectId);

        BoardType type = request.getType() != null
                ? request.getType()
                : (project.getProjectType() == ProjectType.SOFTWARE_KANBAN ? BoardType.KANBAN : BoardType.SCRUM);

        Board board = boardRepository.save(Board.builder()
                .projectId(projectId)
                .name(request.getName().trim())
                .type(type)
                .filterJql(request.getFilterJql())
                .build());

        // Bang khong co cot thi khong hien thi hay keo tha duoc, nen mac dinh sinh 3 cot.
        if (!Boolean.FALSE.equals(request.getSeedDefaultColumns())) {
            seedDefaultColumns(board);
        }
        log.info("Tao bang '{}' ({}) cho du an {}", board.getName(), type, projectId);
        return toBoardResponse(board);
    }

    @Override
    @Transactional
    public BoardResponse update(UUID boardId, UpdateBoardRequest request, UUID userId) {
        Board board = requireBoard(boardId);
        if (request.getName() != null && !request.getName().isBlank()) {
            board.setName(request.getName().trim());
        }
        if (request.getType() != null) {
            board.setType(request.getType());
        }
        if (request.getFilterJql() != null) {
            board.setFilterJql(request.getFilterJql());
        }
        return toBoardResponse(boardRepository.save(board));
    }

    @Override
    @Transactional
    public void delete(UUID boardId, UUID userId) {
        Board board = requireBoard(boardId);
        if (boardRepository.findByProjectId(board.getProjectId()).size() <= 1) {
            throw new BadRequestException("Không thể xoá bảng cuối cùng của dự án");
        }
        boardRepository.delete(board);
        log.info("Xoa bang '{}'", board.getName());
    }

    // ────────────────────────── Du lieu bang ──────────────────────────

    @Override
    @Transactional(readOnly = true)
    public BoardDataResponse getBoardData(UUID boardId, UUID sprintId) {
        Board board = requireBoard(boardId);

        List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByColumnOrderAsc(boardId);
        Map<UUID, List<UUID>> statusIdsByColumn = loadColumnStatusMap(columns);
        Map<UUID, UUID> columnByStatus = invert(statusIdsByColumn);

        // Pham vi hien thi: SCRUM theo Sprint, KANBAN theo toan du an.
        UUID scopeSprintId = null;
        Sprint activeSprint = null;
        if (board.getType() == BoardType.SCRUM) {
            if (sprintId != null) {
                activeSprint = sprintRepository.findById(sprintId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Sprint: " + sprintId));
            } else {
                activeSprint = sprintRepository
                        .findFirstByProjectIdAndState(board.getProjectId(), SprintState.ACTIVE)
                        .orElse(null);
            }
            scopeSprintId = activeSprint == null ? null : activeSprint.getId();
        }

        List<WorkItem> items;
        if (board.getType() == BoardType.SCRUM) {
            items = scopeSprintId == null
                    ? List.of()
                    : workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(scopeSprintId);
        } else {
            Set<UUID> mapped = statusIdsByColumn.values().stream()
                    .flatMap(List::stream).collect(Collectors.toSet());
            items = mapped.isEmpty()
                    ? List.of()
                    : workItemRepository.findActiveByProjectIdAndStatusIdIn(board.getProjectId(), mapped);
        }

        CardMappingContext ctx = cardMapper.buildContext(items, WorkItemCardMapper.Options.board());
        List<BoardCardResponse> allCards = cardMapper.mapAll(items, ctx);

        // Gan cot; the co trang thai chua duoc anh xa thi tach rieng de canh bao.
        List<BoardCardResponse> cards = new ArrayList<>();
        Set<UUID> unmappedStatusIds = new LinkedHashSet<>();
        for (BoardCardResponse c : allCards) {
            UUID columnId = columnByStatus.get(c.getStatusId());
            if (columnId == null) {
                unmappedStatusIds.add(c.getStatusId());
                continue;
            }
            c.setColumnId(columnId);
            cards.add(c);
        }

        List<BoardSwimlane> laneCfg = boardSwimlaneRepository.findByBoardIdOrderBySwimlaneOrderAsc(boardId);
        List<BoardSwimlaneResponse> swimlanes = swimlaneGrouper.group(laneCfg, cards, ctx);
        SwimlaneStrategy strategy = laneCfg.isEmpty() ? null : laneCfg.get(0).getStrategy();

        // Sap the theo (thu tu cot, rank) de FE khong phai sap lai.
        Map<UUID, Integer> orderByColumn = columns.stream()
                .collect(Collectors.toMap(BoardColumn::getId, BoardColumn::getColumnOrder, (a, b) -> a));
        cards.sort(Comparator
                .<BoardCardResponse, Integer>comparing(c -> orderByColumn.getOrDefault(c.getColumnId(), 99))
                .thenComparing(c -> c.getRank() == null ? "" : c.getRank()));

        Map<UUID, Status> statusById = ctx.getStatusById();
        UUID finalScope = scopeSprintId;
        List<BoardColumnResponse> columnResponses = columns.stream()
                .map(col -> toColumnResponse(board, col, statusIdsByColumn.getOrDefault(col.getId(), List.of()),
                        cards, statusById, finalScope))
                .toList();

        BigDecimal totalPoints = cards.stream()
                .map(BoardCardResponse::getStoryPoints)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BoardDataResponse.builder()
                .boardId(board.getId())
                .projectId(board.getProjectId())
                .boardName(board.getName())
                .boardType(board.getType())
                .activeSprint(activeSprint == null ? null : sprintService.getById(activeSprint.getId()))
                .columns(columnResponses)
                .swimlaneStrategy(strategy)
                .swimlanes(swimlanes)
                .cards(cards)
                .totalCards(cards.size())
                .totalStoryPoints(totalPoints)
                .unmappedStatuses(unmappedStatusIds.stream()
                        .map(statusById::get).filter(Objects::nonNull).map(this::toStatusResponse).toList())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────── Keo tha the ───────────────────────────

    @Override
    @Transactional
    public MoveCardResponse moveCard(UUID boardId, UUID itemId, MoveCardRequest request, UUID userId) {
        Board board = requireBoard(boardId);
        WorkItem item = workItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc: " + itemId));

        if (item.getDeletedAt() != null) {
            throw new BadRequestException("Công việc đã bị xoá");
        }
        if (!Objects.equals(item.getProjectId(), board.getProjectId())) {
            throw new BadRequestException("Công việc không thuộc bảng này");
        }

        // 1. Kiem tra xung dot phien ban TRUOC moi thay doi: cho thong bao chinh xac
        //    va khong chay may trang thai cho mot thao tac chac chan that bai.
        if (request.getClientVersion() != null
                && !request.getClientVersion().equals(item.getVersion())) {
            throw new ConflictException(String.format(
                    "Thẻ này vừa được người khác cập nhật (phiên bản %s, bạn đang giữ %s). Vui lòng tải lại bảng.",
                    item.getVersion(), request.getClientVersion()));
        }

        // 2. Cot dich -> tap trang thai
        BoardColumn targetColumn = boardColumnRepository.findById(request.getTargetColumnId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cột: " + request.getTargetColumnId()));
        if (!Objects.equals(targetColumn.getBoardId(), boardId)) {
            throw new BadRequestException("Cột không thuộc bảng này");
        }
        List<UUID> targetStatusIds = boardColumnStatusRepository.findByColumnId(targetColumn.getId())
                .stream().map(BoardColumnStatus::getStatusId).toList();
        if (targetStatusIds.isEmpty()) {
            throw new BadRequestException(
                    "Cột '" + targetColumn.getName() + "' chưa được ánh xạ với trạng thái nào");
        }

        boolean sameColumn = targetStatusIds.contains(item.getStatusId());
        UUID scopeSprintId = board.getType() == BoardType.SCRUM ? item.getSprintId() : null;

        // 3. Rank moi
        String newRank = resolveRank(request, board, targetStatusIds, scopeSprintId);

        // 4a. Sap xep trong cung cot: khong dung toi workflow, khong kiem WIP.
        if (sameColumn) {
            item.setRank(newRank);
            WorkItem saved = workItemRepository.saveAndFlush(item);
            Status status = statusRepository.findById(saved.getStatusId()).orElse(null);
            return MoveCardResponse.builder()
                    .itemId(saved.getId()).code(saved.getCode()).newRank(saved.getRank())
                    .statusId(saved.getStatusId())
                    .statusName(status == null ? null : status.getName())
                    .statusCategory(status == null ? null : status.getCategory())
                    .columnId(targetColumn.getId())
                    .version(saved.getVersion())
                    .statusChanged(false)
                    .build();
        }

        // 4b. Doi cot = doi trang thai
        wipLimitGuard.checkWipLimit(board, targetColumn, targetStatusIds, scopeSprintId,
                Boolean.TRUE.equals(request.getIgnoreWipLimit()));

        AvailableTransitionResponse chosen = resolveTransition(item, targetStatusIds, targetColumn, request, userId);

        ExecuteTransitionRequest execReq = ExecuteTransitionRequest.builder()
                .transitionId(chosen.getTransitionId())
                .resolveAllSubtasks(request.getResolveAllSubtasks())
                .comment(request.getComment())
                .resolution(request.getResolution())
                .build();

        // executeTransition nap lai entity theo id; lay dung instance no tra ve roi moi dat rank.
        WorkItem moved = workflowEngineService.executeTransition(itemId, chosen.getTransitionId(), execReq, userId);
        moved.setRank(newRank);

        // saveAndFlush chu khong save: Hibernate chi tang @Version luc flush. Neu khong flush,
        // response se tra version CU va lan keo ke tiep cua FE se that bai o buoc 1.
        WorkItem saved = workItemRepository.saveAndFlush(moved);
        Status status = statusRepository.findById(saved.getStatusId()).orElse(null);

        log.info("Chuyen the {} sang cot '{}' ({})", saved.getCode(), targetColumn.getName(), chosen.getName());

        return MoveCardResponse.builder()
                .itemId(saved.getId()).code(saved.getCode()).newRank(saved.getRank())
                .statusId(saved.getStatusId())
                .statusName(status == null ? null : status.getName())
                .statusCategory(status == null ? null : status.getCategory())
                .columnId(targetColumn.getId())
                .version(saved.getVersion())
                .statusChanged(true)
                .appliedTransitionId(chosen.getTransitionId())
                .appliedTransitionName(chosen.getName())
                .build();
    }

    /**
     * Tim buoc chuyen hop le toi cot dich.
     *
     * KHONG BAO GIO ghi thang status_id khi khong tim thay: lam vay se bo qua guard viec
     * tien de, guard Sub-task chua xong, post-function (resolvedAt, changelog STATUS ma
     * Burndown dua vao) va cascade trang thai cha.
     */
    private AvailableTransitionResponse resolveTransition(WorkItem item, List<UUID> targetStatusIds,
                                                          BoardColumn targetColumn,
                                                          MoveCardRequest request, UUID userId) {
        List<AvailableTransitionResponse> available =
                workflowEngineService.getAvailableTransitions(item.getId(), userId);

        List<AvailableTransitionResponse> candidates = available.stream()
                .filter(t -> targetStatusIds.contains(t.getToStatusId()))
                .filter(t -> request.getTargetStatusId() == null
                        || request.getTargetStatusId().equals(t.getToStatusId()))
                .sorted(Comparator
                        .<AvailableTransitionResponse, Integer>comparing(
                                t -> t.getToStatusCategory() == null ? 99 : t.getToStatusCategory().ordinal())
                        .thenComparing(t -> t.getToStatusName() == null ? "" : t.getToStatusName()))
                .toList();

        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }

        if (request.getTargetStatusId() != null && !targetStatusIds.contains(request.getTargetStatusId())) {
            throw new BadRequestException("Trạng thái đích không thuộc cột này");
        }

        // Phan biet "quy trinh khong co duong di" (400) voi "nguoi dung khong du dieu kien" (403).
        Workflow workflow = workflowEngineService.resolveWorkflow(item.getProjectId(), item.getTypeId());
        boolean existsOnPaper = workflow != null && workflowTransitionRepository
                .findByWorkflowId(workflow.getId()).stream()
                .anyMatch(t -> targetStatusIds.contains(t.getToStatusId())
                        && (t.getFromStatusId() == null || t.getFromStatusId().equals(item.getStatusId())));

        String currentStatusName = statusRepository.findById(item.getStatusId())
                .map(Status::getName).orElse("hiện tại");

        if (existsOnPaper) {
            throw new ForbiddenException(String.format(
                    "Bạn không đủ điều kiện để chuyển '%s' sang cột '%s'",
                    item.getCode(), targetColumn.getName()));
        }
        throw new BadRequestException(String.format(
                "Không có bước chuyển hợp lệ từ '%s' sang cột '%s'",
                currentStatusName, targetColumn.getName()));
    }

    private String resolveRank(MoveCardRequest request, Board board,
                               List<UUID> targetStatusIds, UUID scopeSprintId) {
        String prev = request.getPrevItemRank();
        String next = request.getNextItemRank();

        if (prev == null && next == null) {
            String last = lastRankInColumn(board, targetStatusIds, scopeSprintId);
            return last == null ? lexoRankService.calculateInitialRank() : lexoRankService.calculateAfter(last);
        }
        if (prev == null) return lexoRankService.calculateBefore(next);
        if (next == null) return lexoRankService.calculateAfter(prev);
        try {
            return lexoRankService.calculateBetween(prev, next);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Thứ tự thẻ không hợp lệ (dữ liệu bảng đã cũ). Vui lòng tải lại bảng.");
        }
    }

    private String lastRankInColumn(Board board, List<UUID> statusIds, UUID scopeSprintId) {
        List<WorkItem> inColumn = scopeSprintId != null
                ? workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(scopeSprintId).stream()
                    .filter(w -> statusIds.contains(w.getStatusId())).toList()
                : workItemRepository.findActiveByProjectIdAndStatusIdIn(board.getProjectId(), statusIds);
        return inColumn.isEmpty() ? null : inColumn.get(inColumn.size() - 1).getRank();
    }

    // ──────────────────────── Cau hinh cot / luong ────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<BoardColumnResponse> getColumns(UUID boardId) {
        Board board = requireBoard(boardId);
        List<BoardColumn> columns = boardColumnRepository.findByBoardIdOrderByColumnOrderAsc(boardId);
        Map<UUID, List<UUID>> statusIdsByColumn = loadColumnStatusMap(columns);
        Map<UUID, Status> statusById = statusRepository.findAll().stream()
                .collect(Collectors.toMap(Status::getId, Function.identity(), (a, b) -> a));

        // Bang SCRUM dem theo Sprint dang chay, giong het getBoardData - neu de null thi
        // so dem se tinh ca du an va lech voi con so nguoi dung thay tren bang.
        UUID scopeSprintId = board.getType() == BoardType.SCRUM
                ? sprintRepository.findFirstByProjectIdAndState(board.getProjectId(), SprintState.ACTIVE)
                    .map(Sprint::getId).orElse(null)
                : null;

        return columns.stream()
                .map(col -> toColumnResponse(board, col, statusIdsByColumn.getOrDefault(col.getId(), List.of()),
                        List.of(), statusById, scopeSprintId))
                .toList();
    }

    @Override
    @Transactional
    public List<BoardColumnResponse> updateColumns(UUID boardId, UpdateBoardColumnsRequest request, UUID userId) {
        Board board = requireBoard(boardId);
        List<BoardColumnConfigRequest> configs = request.getColumns();

        // Mot trang thai nam o hai cot se lam anh xa trang thai -> cot nhap nhang.
        Map<UUID, String> seen = new HashMap<>();
        for (BoardColumnConfigRequest c : configs) {
            for (UUID sid : c.getStatusIds()) {
                String other = seen.putIfAbsent(sid, c.getName());
                if (other != null) {
                    throw new BadRequestException(String.format(
                            "Trạng thái đang được gán cho cả hai cột '%s' và '%s'. Mỗi trạng thái chỉ thuộc một cột.",
                            other, c.getName()));
                }
            }
        }
        Set<UUID> allStatusIds = seen.keySet();
        Set<UUID> existing = statusRepository.findAllById(allStatusIds).stream()
                .map(Status::getId).collect(Collectors.toSet());
        if (!existing.containsAll(allStatusIds)) {
            throw new BadRequestException("Có trạng thái không tồn tại trong cấu hình cột");
        }

        List<BoardColumn> current = boardColumnRepository.findByBoardIdOrderByColumnOrderAsc(boardId);
        Map<UUID, BoardColumn> currentById = current.stream()
                .collect(Collectors.toMap(BoardColumn::getId, Function.identity()));
        Set<UUID> keptIds = configs.stream().map(BoardColumnConfigRequest::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        // Xoa cot khong con trong cau hinh
        for (BoardColumn col : current) {
            if (keptIds.contains(col.getId())) continue;
            List<UUID> statusIds = boardColumnStatusRepository.findByColumnId(col.getId())
                    .stream().map(BoardColumnStatus::getStatusId).toList();
            if (!statusIds.isEmpty() && !Boolean.TRUE.equals(request.getForce())) {
                long count = workItemRepository.countActiveByProjectIdAndStatusIdIn(board.getProjectId(), statusIds);
                if (count > 0) {
                    throw new BadRequestException(String.format(
                            "Cột '%s' còn %d công việc. Hãy chuyển chúng sang cột khác trước khi xoá.",
                            col.getName(), count));
                }
            }
            boardColumnStatusRepository.deleteByColumnId(col.getId());
            boardColumnRepository.delete(col);
        }

        // Them / cap nhat
        for (BoardColumnConfigRequest cfg : configs) {
            BoardColumn col = cfg.getId() == null ? new BoardColumn() : currentById.get(cfg.getId());
            if (col == null) {
                throw new ResourceNotFoundException("Không tìm thấy cột: " + cfg.getId());
            }
            col.setBoardId(boardId);
            col.setName(cfg.getName().trim());
            col.setColumnOrder(cfg.getColumnOrder());
            col.setMinWipLimit(cfg.getMinWipLimit());
            col.setMaxWipLimit(cfg.getMaxWipLimit());
            BoardColumn saved = boardColumnRepository.save(col);

            // board_column_statuses khong co khoa thay the va khong co thu tu -> ghi de ca cum.
            boardColumnStatusRepository.deleteByColumnId(saved.getId());
            boardColumnStatusRepository.saveAll(cfg.getStatusIds().stream()
                    .map(sid -> BoardColumnStatus.builder().columnId(saved.getId()).statusId(sid).build())
                    .toList());
        }

        log.info("Cap nhat {} cot cho bang '{}'", configs.size(), board.getName());
        return getColumns(boardId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoardSwimlaneResponse> getSwimlanes(UUID boardId) {
        requireBoard(boardId);
        return boardSwimlaneRepository.findByBoardIdOrderBySwimlaneOrderAsc(boardId).stream()
                .map(s -> BoardSwimlaneResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .strategy(s.getStrategy())
                        .swimlaneOrder(s.getSwimlaneOrder())
                        .customJql(s.getCustomJql())
                        .unsupported(s.getStrategy() == SwimlaneStrategy.CUSTOM_JQL)
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public List<BoardSwimlaneResponse> updateSwimlanes(UUID boardId, UpdateBoardSwimlanesRequest request, UUID userId) {
        requireBoard(boardId);
        List<BoardSwimlaneConfigRequest> configs =
                request.getSwimlanes() == null ? List.of() : request.getSwimlanes();

        boardSwimlaneRepository.deleteAll(boardSwimlaneRepository.findByBoardId(boardId));
        for (BoardSwimlaneConfigRequest cfg : configs) {
            boardSwimlaneRepository.save(BoardSwimlane.builder()
                    .boardId(boardId)
                    .name(cfg.getName().trim())
                    .strategy(cfg.getStrategy())
                    .customJql(cfg.getCustomJql())
                    .swimlaneOrder(cfg.getSwimlaneOrder())
                    .build());
        }
        return getSwimlanes(boardId);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private void seedDefaultColumns(Board board) {
        // Tu V3, status thuoc ve tung du an. Phai loc theo projectId, neu khong cot cua bang
        // nay se anh xa nham sang status cua du an khac (project_id NULL la status cu dung chung).
        List<Status> statuses = statusRepository.findByProjectIdOrProjectIdIsNull(board.getProjectId());
        record Seed(String name, StatusCategory category) {}
        List<Seed> seeds = List.of(
                new Seed("Cần làm", StatusCategory.TODO),
                new Seed("Đang làm", StatusCategory.IN_PROGRESS),
                new Seed("Hoàn thành", StatusCategory.DONE));

        int order = 0;
        for (Seed seed : seeds) {
            List<UUID> ids = statuses.stream()
                    .filter(s -> s.getCategory() == seed.category())
                    .map(Status::getId).toList();
            if (ids.isEmpty()) continue;

            BoardColumn col = boardColumnRepository.save(BoardColumn.builder()
                    .boardId(board.getId()).name(seed.name()).columnOrder(order++).build());
            boardColumnStatusRepository.saveAll(ids.stream()
                    .map(sid -> BoardColumnStatus.builder().columnId(col.getId()).statusId(sid).build())
                    .toList());
        }
    }

    private Map<UUID, List<UUID>> loadColumnStatusMap(List<BoardColumn> columns) {
        if (columns.isEmpty()) return Map.of();
        List<UUID> ids = columns.stream().map(BoardColumn::getId).toList();
        return boardColumnStatusRepository.findByColumnIdIn(ids).stream()
                .collect(Collectors.groupingBy(BoardColumnStatus::getColumnId,
                        Collectors.mapping(BoardColumnStatus::getStatusId, Collectors.toList())));
    }

    private Map<UUID, UUID> invert(Map<UUID, List<UUID>> statusIdsByColumn) {
        Map<UUID, UUID> out = new HashMap<>();
        statusIdsByColumn.forEach((colId, statusIds) -> statusIds.forEach(sid -> out.put(sid, colId)));
        return out;
    }

    private BoardColumnResponse toColumnResponse(Board board, BoardColumn col, List<UUID> statusIds,
                                                 List<BoardCardResponse> cards,
                                                 Map<UUID, Status> statusById, UUID scopeSprintId) {
        List<BoardCardResponse> inColumn = cards.stream()
                .filter(c -> col.getId().equals(c.getColumnId())).toList();

        int count = cards.isEmpty()
                ? wipLimitGuard.evaluate(col, statusIds, board.getProjectId(), scopeSprintId).currentCount()
                : inColumn.size();

        Integer max = col.getMaxWipLimit();
        Integer min = col.getMinWipLimit();

        return BoardColumnResponse.builder()
                .id(col.getId())
                .boardId(col.getBoardId())
                .name(col.getName())
                .columnOrder(col.getColumnOrder())
                .minWipLimit(min)
                .maxWipLimit(max)
                .statuses(statusIds.stream().map(statusById::get).filter(Objects::nonNull)
                        .map(this::toStatusResponse).toList())
                .currentCount(count)
                .isOverMax(max != null && count > max)
                .isBelowMin(min != null && count < min)
                .storyPointsSum(inColumn.stream().map(BoardCardResponse::getStoryPoints)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    private StatusResponse toStatusResponse(Status s) {
        return StatusResponse.builder()
                .id(s.getId()).name(s.getName()).category(s.getCategory())
                .description(s.getDescription()).colorHex(s.getColorHex())
                .createdAt(s.getCreatedAt()).build();
    }

    private BoardResponse toBoardResponse(Board board) {
        Optional<Sprint> active = board.getType() == BoardType.SCRUM
                ? sprintRepository.findFirstByProjectIdAndState(board.getProjectId(), SprintState.ACTIVE)
                : Optional.empty();

        return BoardResponse.builder()
                .id(board.getId())
                .projectId(board.getProjectId())
                .name(board.getName())
                .type(board.getType())
                .filterJql(board.getFilterJql())
                .columnCount(boardColumnRepository.findByBoardId(board.getId()).size())
                .swimlaneCount(boardSwimlaneRepository.findByBoardId(board.getId()).size())
                .activeSprintId(active.map(Sprint::getId).orElse(null))
                .activeSprintName(active.map(Sprint::getName).orElse(null))
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .build();
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));
    }

    private Board requireBoard(UUID boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng công việc: " + boardId));
    }
}
