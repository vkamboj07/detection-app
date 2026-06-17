package com.footfallanalytics.sdk;

import com.footfallanalytics.sdk.model.FootfallMetrics;
import com.footfallanalytics.sdk.model.Observation;

public interface FootfallListener {
    void onObservationDetected(Observation observation);

    void onMetricsUpdated(FootfallMetrics metrics);

    void onScanningStarted();

    void onScanningStopped();

    void onError(String message, Throwable cause);
}
