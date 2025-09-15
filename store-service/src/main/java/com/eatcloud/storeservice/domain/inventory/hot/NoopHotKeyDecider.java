package com.eatcloud.storeservice.domain.inventory.hot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnMissingBean(HotKeyDecider.class)
public class NoopHotKeyDecider implements HotKeyDecider {
    @Override public boolean isHot(UUID menuId) { return false; }
}
