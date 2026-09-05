package com.datn.workservice.repository;

import com.datn.workservice.entity.BoardSwimlane;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BoardSwimlaneRepository extends JpaRepository<BoardSwimlane, UUID> {
    List<BoardSwimlane> findByBoardId(UUID boardId);
    List<BoardSwimlane> findByBoardIdOrderBySwimlaneOrderAsc(UUID boardId);
}
