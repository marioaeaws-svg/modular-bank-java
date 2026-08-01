package com.modularbank.transfers.adapter.out.persistence;

import com.modularbank.transfers.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @Query("SELECT t FROM Transfer t WHERE t.userId = :userId "
        + "AND (t.sourceAccountId = :accountId OR t.targetAccountId = :accountId) "
        + "ORDER BY t.createdAt DESC")
    List<Transfer> findHistory(@Param("userId") UUID userId, @Param("accountId") UUID accountId);
}
