// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring.builder;

import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.admin.monitoring.VespaMetricSet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A data object for predefined metric sets.
 *
 * @author gjoranv
 */
public class PredefinedMetricSets {

    public static final Map<String, MetricSet> predefinedMetricSets = toMapById(
            new VespaMetricSet()
    );

    private static Map<String, MetricSet> toMapById(MetricSet... metricSets) {
        Map<String, MetricSet> availableMetricSets = new LinkedHashMap<>();
        for (MetricSet metricSet : metricSets)
            availableMetricSets.put(metricSet.getId(), metricSet);
        return Collections.unmodifiableMap(availableMetricSets);
    }

}