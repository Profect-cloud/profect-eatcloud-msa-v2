package com.eatcloud.adminservice.ports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagerUpsertCommand {
    private UUID managerId;     // null 가능
    private String email;
    private String name;
    private String phone;
}