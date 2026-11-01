package com.timeix3.messenger.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user1_id", nullable = false)
  private User user1;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user2_id", nullable = false)
  private User user2;

  @Column(name = "user1_last_read_message_id")
  private Long user1LastReadMessageId;

  @Column(name = "user2_last_read_message_id")
  private Long user2LastReadMessageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "last_message_id")
  private Message lastMessage;

  @Column(name = "last_message_at")
  private Instant lastMessageAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
