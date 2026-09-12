package com.datn.workservice.repository;

import com.datn.workservice.entity.BoardColumnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BoardColumnStatusRepository extends JpaRepository<BoardColumnStatus, BoardColumnStatus.BoardColumnStatusId> {
    List<BoardColumnStatus> findByColumnId(UUID columnId);
    List<BoardColumnStatus> findByColumnIdIn(List<UUID> columnIds);
    List<BoardColumnStatus> findByStatusId(UUID statusId);
    void deleteByColumnId(UUID columnId);
    void deleteByStatusId(UUID statusId);
}
