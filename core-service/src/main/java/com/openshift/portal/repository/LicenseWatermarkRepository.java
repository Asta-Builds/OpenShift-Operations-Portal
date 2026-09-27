package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.LicenseWatermark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseWatermarkRepository extends JpaRepository<LicenseWatermark, Long> {
    Optional<LicenseWatermark> findByWatermarkDate(LocalDate date);
    Optional<LicenseWatermark> findTopByOrderByWatermarkDateDesc();
    List<LicenseWatermark> findTop30ByOrderByWatermarkDateDesc();
}
