// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

/**
 * @author valerijf
 */
public interface MetricValue {
    Number getValue();
}