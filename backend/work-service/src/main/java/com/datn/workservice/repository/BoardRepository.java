package com.datn.workservice.repository;

import com.datn.workservice.entity.Board;
import com.datn.workservice.enums.BoardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BoardRepository extends JpaRepository<Board, UUID>, JpaSpecificationExecutor<Board> {
    List<Board> findByProjectId(UUID projectId);
    List<Board> findByProjectIdAndType(UUID projectId, BoardType type);
}
