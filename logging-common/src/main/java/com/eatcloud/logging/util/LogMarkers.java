package com.eatcloud.logging.util;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * 로그 마커 상수들
 */
public final class LogMarkers {
    
    public static final Marker STATEFUL = MarkerFactory.getMarker("STATEFUL");
    public static final Marker STATELESS = MarkerFactory.getMarker("STATELESS");
    public static final Marker RECOMMENDATION = MarkerFactory.getMarker("RECOMMENDATION");
    
    private LogMarkers() {
        // Utility class - prevent instantiation
    }
}
