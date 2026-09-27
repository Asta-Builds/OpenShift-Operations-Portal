package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.LicenseWatermark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseWatermarkRepository extends JpaRepository<LicenseWatermark, Long> {
    Optional<LicenseWatermark> findByWatermarkDate(LocalDate date);
    Optional<LicenseWatermark> findTopByOrderByWatermarkDateDesc();
    List<LicenseWatermark> findTop30ByOrderByWatermarkDateDesc();

    /** Highest daily peak recorded on or after the given day, or null when none was. */
    @Query("SELECT MAX(w.peakWorkerCores) FROM LicenseWatermark w WHERE w.watermarkDate >= :since")
    Integer findPeakSince(@Param("since") LocalDate since);
}
