package com.eatcloud.storeservice.domain.reviews.util;

import org.springframework.data.domain.*;
import java.util.Set;

public final class SortWhitelist {
    private SortWhitelist() {}
    public static Pageable enforce(Pageable pageable, Set<String> allowed) {
        if (pageable.getSort().isEmpty()) return pageable;
        Sort.Order o = pageable.getSort().stream().findFirst().get();
        if (!allowed.contains(o.getProperty())) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Order.desc("createdAt")));
        }
        return pageable;
    }
}
