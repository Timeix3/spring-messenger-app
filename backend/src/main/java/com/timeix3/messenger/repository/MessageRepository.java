package com.timeix3.messenger.repository;

import com.timeix3.messenger.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversationIdOrderByIdDesc(
            Long conversationId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversationIdOrderByIdAsc(
            Long conversationId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversationIdAndIdLessThanOrderByIdDesc(
            Long conversationId,
            Long id,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender"})
    List<Message> findByConversationIdAndIdGreaterThanOrderByIdAsc(
            Long conversationId,
            Long id
    );

    @EntityGraph(attributePaths = {"sender"})
    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    Optional<Message> findByIdAndConversationId(Long id, Long conversationId);

    boolean existsByIdAndConversationId(Long id, Long conversationId);

    long countByConversationId(Long conversationId);
}
