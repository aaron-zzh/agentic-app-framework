/**
 * Actor 仓储。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.actor;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActorRepository extends JpaRepository<Actor, Long> {

    Optional<Actor> findByActorId(String actorId);
}
