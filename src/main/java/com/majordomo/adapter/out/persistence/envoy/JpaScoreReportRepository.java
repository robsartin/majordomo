package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link ScoreReportEntity}. */
public interface JpaScoreReportRepository extends JpaRepository<ScoreReportEntity, UUID> {

    /**
     * Finds a report by id within an organization.
     *
     * @param id             report id
     * @param organizationId owning org
     * @return matching report, or empty
     */
    Optional<ScoreReportEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Cursor-paginated query. Returns {@code limit + 1} rows when more pages
     * exist so the caller can construct a {@code Page} with {@code hasMore}.
     *
     * @param organizationId required org scope
     * @param minFinalScore  optional min score (null = no lower bound)
     * @param recommendation optional recommendation filter (null = any)
     * @param cursor         optional cursor — returns rows with id &gt; cursor
     * @param sort           result sort order (typically by id ascending)
     * @param limit          row cap (limit+1 to detect hasMore)
     * @return matching rows
     */
    @Query("""
            SELECT r FROM ScoreReportEntity r
             WHERE r.organizationId = :organizationId
               AND (:minFinalScore IS NULL OR r.finalScore >= :minFinalScore)
               AND (:recommendation IS NULL OR r.recommendation = :recommendation)
               AND (:cursor IS NULL OR r.id > :cursor)
            """)
    List<ScoreReportEntity> query(
            @Param("organizationId") UUID organizationId,
            @Param("minFinalScore") Integer minFinalScore,
            @Param("recommendation") String recommendation,
            @Param("cursor") UUID cursor,
            Sort sort,
            Limit limit);
}
