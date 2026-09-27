package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.InfrastructureInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InfrastructureInventoryRepository extends JpaRepository<InfrastructureInventory, Long> {

    List<InfrastructureInventory> findBySource(String source);

    long countBySource(String source);

    List<InfrastructureInventory> findByInstanceKeyIn(Collection<String> instanceKeys);
}
