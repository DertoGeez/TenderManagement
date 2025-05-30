package com.egov.tendering.notification.event;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class EmailSentEvent extends NotificationEvent {
    private String recipient;
    private String subject;
    private String content;
    private String status;
}