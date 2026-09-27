package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.TeamAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamAliasRepository extends JpaRepository<TeamAlias, UUID> {

    @Query("select a from TeamAlias a join fetch a.team")
    List<TeamAlias> findAllWithTeam();

    List<TeamAlias> findByTeamIdOrderByAlias(UUID teamId);

    Optional<TeamAlias> findByAlias(String alias);
}
