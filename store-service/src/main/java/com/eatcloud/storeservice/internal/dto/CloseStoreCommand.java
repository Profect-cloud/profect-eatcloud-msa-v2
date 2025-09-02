package com.eatcloud.storeservice.internal.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CloseStoreCommand {
    private String reason;
}
