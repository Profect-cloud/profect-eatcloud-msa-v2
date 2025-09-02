package com.eatcloud.adminservice.ports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloseStoreCommand {
    private UUID applicationId;     // 폐업 신청 멱등 키
    private String reason;
}