package com.deolho.domain.repository;

import com.deolho.domain.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    Optional<AiAnalysis> findByErrorRecordId(Long errorId);

    boolean existsByErrorRecordId(Long errorId);
}
