package com.egov.tendering.notification.dal.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationType type;

  @Column(nullable = false)
  private String entityId;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false, length = 2000)
  private String message;

  @ElementCollection
  private List<String> recipients;

  @Column(nullable = false)
  private String status;

  @Column
  private String errorMessage;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column
  private LocalDateTime deliveredAt;

  @Column(nullable = false)
  private boolean read;

  @Column
  private Integer retryCount;

  @Column
  private LocalDateTime lastRetryAt;


  @UpdateTimestamp
  private LocalDateTime updatedAt;

  @Column
  private LocalDateTime scheduledAt;

  @Column
  private LocalDateTime sentAt;
}

