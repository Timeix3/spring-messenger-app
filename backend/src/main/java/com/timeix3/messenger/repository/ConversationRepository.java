package com.timeix3.messenger.repository;

import com.timeix3.messenger.entity.Conversation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

  @Query(
      """
            select c
            from Conversation c
            where (c.user1.id = :userA and c.user2.id = :userB)
               or (c.user1.id = :userB and c.user2.id = :userA)
            """)
  Optional<Conversation> findBetweenUsers(@Param("userA") Long userA, @Param("userB") Long userB);

  @Query(
      """
            select count(c)
            from Conversation c
            where (c.user1.id = :userA and c.user2.id = :userB)
               or (c.user1.id = :userB and c.user2.id = :userA)
            """)
  long countBetweenUsers(@Param("userA") Long userA, @Param("userB") Long userB);

  @Query(
      value =
          """
                    select c
                    from Conversation c
                    left join fetch c.user1 u1
                    left join fetch c.user2 u2
                    left join fetch c.lastMessage lm
                    where c.user1.id = :userId
                       or c.user2.id = :userId
                    order by coalesce(c.lastMessageAt, c.createdAt) desc
                    """,
      countQuery =
          """
                    select count(c)
                    from Conversation c
                    where c.user1.id = :userId
                       or c.user2.id = :userId
                    """)
  Page<Conversation> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

  @Query(
      """
            select c
            from Conversation c
            left join fetch c.user1 u1
            left join fetch c.user2 u2
            left join fetch c.lastMessage lm
            where c.id = :id
            """)
  Optional<Conversation> findWithDetailsById(@Param("id") Long id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
                    update conversations
                    set last_message_id = :lastMessageId,
                        last_message_at = :lastMessageAt
                    where id = :conversationId
                    """,
      nativeQuery = true)
  int updateLastMessage(
      @Param("conversationId") Long conversationId,
      @Param("lastMessageId") Long lastMessageId,
      @Param("lastMessageAt") Instant lastMessageAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
                    update conversations
                    set user1_last_read_message_id =
                            case
                                when user1_id = :userId then :messageId
                                else user1_last_read_message_id
                            end,
                        user2_last_read_message_id =
                            case
                                when user2_id = :userId then :messageId
                                else user2_last_read_message_id
                            end
                    where id = :conversationId
                      and :userId in (user1_id, user2_id)
                    """,
      nativeQuery = true)
  int markRead(
      @Param("conversationId") Long conversationId,
      @Param("userId") Long userId,
      @Param("messageId") Long messageId);

  @Query(
      value =
          """
                    select count(m.id)
                    from messages m
                    join conversations c on c.id = m.conversation_id
                    where m.conversation_id = :conversationId
                      and m.sender_id <> :userId
                      and :userId in (c.user1_id, c.user2_id)
                      and m.id > coalesce(
                              case
                                  when c.user1_id = :userId then c.user1_last_read_message_id
                                  when c.user2_id = :userId then c.user2_last_read_message_id
                              end,
                              0
                          )
                    """,
      nativeQuery = true)
  long countUnreadMessages(
      @Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
