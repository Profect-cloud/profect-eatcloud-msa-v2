package com.eatcloud.storeservice.domain.inventory.hot;

import java.util.UUID;

public interface HotKeyDecider {
    boolean isHot(UUID menuId);

    // 운영 토글 API에서 쓸 수 있게 기본 no-op 제공
    default void markHot(UUID menuId) {}
    default void unmarkHot(UUID menuId) {}
}
