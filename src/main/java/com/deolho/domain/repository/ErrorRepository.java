package com.deolho.domain.repository;

import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.enums.ErrorStatus;
import com.deolho.domain.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ErrorRepository extends JpaRepository<ErrorRecord, Long> {

    Optional<ErrorRecord> findByHash(String hash);

    boolean existsByHash(String hash);

    Page<ErrorRecord> findByApplicationId(Long applicationId, Pageable pageable);

    Page<ErrorRecord> findByStatus(ErrorStatus status, Pageable pageable);

    Page<ErrorRecord> findBySeverity(Severity severity, Pageable pageable);

    Page<ErrorRecord> findByApplicationIdAndStatus(Long applicationId, ErrorStatus status, Pageable pageable);

    /** Search errors by message or exception type (case-insensitive) */
    @Query("SELECT e FROM ErrorRecord e WHERE " +
            "LOWER(e.message) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.exception) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<ErrorRecord> searchByMessageOrException(@Param("query") String query, Pageable pageable);

    /** Top N most frequent errors */
    @Query("SELECT e FROM ErrorRecord e ORDER BY e.occurrences DESC")
    List<ErrorRecord> findTopByOccurrences(Pageable pageable);

    /** Count errors within a time range */
    @Query("SELECT COUNT(e) FROM ErrorRecord e WHERE e.lastSeen BETWEEN :start AND :end")
    long countByLastSeenBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Count errors by application */
    @Query("SELECT COUNT(e) FROM ErrorRecord e WHERE e.application.id = :appId")
    long countByApplicationId(@Param("appId") Long applicationId);

    /** Count errors grouped by status */
    @Query("SELECT e.status, COUNT(e) FROM ErrorRecord e GROUP BY e.status")
    List<Object[]> countGroupedByStatus();

    /** Count errors grouped by severity */
    @Query("SELECT e.severity, COUNT(e) FROM ErrorRecord e GROUP BY e.severity")
    List<Object[]> countGroupedBySeverity();

    /** Errors in the last N hours */
    @Query("SELECT e FROM ErrorRecord e WHERE e.lastSeen >= :since ORDER BY e.lastSeen DESC")
    List<ErrorRecord> findRecentErrors(@Param("since") LocalDateTime since);

    /** Find errors with the same exception type (for similarity matching) */
    List<ErrorRecord> findByException(String exception);
}
