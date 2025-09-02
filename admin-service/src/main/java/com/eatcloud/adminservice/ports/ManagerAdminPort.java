package com.eatcloud.adminservice.ports;

import java.util.UUID;

public interface ManagerAdminPort {
    UUID upsert(ManagerUpsertCommand cmd);
}