package com.egov.tendering.user.event;


import com.egov.tendering.user.dal.model.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class UserStatusChangedEvent extends UserEvent {
    private UserStatus oldStatus;
    private UserStatus newStatus;
}