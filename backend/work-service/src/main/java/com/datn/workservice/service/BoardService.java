package com.datn.workservice.service;

import com.datn.workservice.dto.request.CreateBoardRequest;
import com.datn.workservice.dto.request.MoveCardRequest;
import com.datn.workservice.dto.request.UpdateBoardColumnsRequest;
import com.datn.workservice.dto.request.UpdateBoardRequest;
import com.datn.workservice.dto.request.UpdateBoardSwimlanesRequest;
import com.datn.workservice.dto.response.BoardColumnResponse;
import com.datn.workservice.dto.response.BoardDataResponse;
import com.datn.workservice.dto.response.BoardResponse;
import com.datn.workservice.dto.response.BoardSwimlaneResponse;
import com.datn.workservice.dto.response.MoveCardResponse;

import java.util.List;
import java.util.UUID;

public interface BoardService {

    List<BoardResponse> listByProject(UUID projectId);

    BoardResponse getById(UUID boardId);

    BoardResponse create(UUID projectId, CreateBoardRequest request, UUID userId);

    BoardResponse update(UUID boardId, UpdateBoardRequest request, UUID userId);

    void delete(UUID boardId, UUID userId);

    /**
     * Toan bo du lieu de ve bang.
     *
     * @param sprintId chi dinh Sprint can xem (bang SCRUM); bo trong thi lay Sprint dang chay.
     */
    BoardDataResponse getBoardData(UUID boardId, UUID sprintId);

    /** Keo tha mot the: doi thu tu, va neu doi cot thi chay qua may trang thai workflow. */
    MoveCardResponse moveCard(UUID boardId, UUID itemId, MoveCardRequest request, UUID userId);

    List<BoardColumnResponse> getColumns(UUID boardId);

    List<BoardColumnResponse> updateColumns(UUID boardId, UpdateBoardColumnsRequest request, UUID userId);

    List<BoardSwimlaneResponse> getSwimlanes(UUID boardId);

    List<BoardSwimlaneResponse> updateSwimlanes(UUID boardId, UpdateBoardSwimlanesRequest request, UUID userId);
}
