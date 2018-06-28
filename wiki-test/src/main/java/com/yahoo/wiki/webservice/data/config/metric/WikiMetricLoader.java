// Copyright 2016 Yahoo Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.wiki.webservice.data.config.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.bard.webservice.data.config.metric.MetricInstance;
import com.yahoo.bard.webservice.data.config.metric.MetricLoader;
import com.yahoo.bard.webservice.data.config.metric.makers.MetricMaker;
import com.yahoo.bard.webservice.data.dimension.DimensionDictionary;
import com.yahoo.bard.webservice.data.metric.LogicalMetricInfo;
import com.yahoo.bard.webservice.data.metric.MetricDictionary;
import com.yahoo.bard.webservice.util.Utils;
import com.yahoo.wiki.webservice.data.config.ExternalConfigLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Load the Wiki-specific metrics. Currently only loads primitive makers that are considered built-in to Fili,
 * such as the LongSumMaker for performing the longSum aggregation, and the divisionMaker, which performs division
 * of two other metrics.
 */
public class WikiMetricLoader implements MetricLoader {

    private static final Logger LOG = LoggerFactory.getLogger(WikiMetricLoader.class);

    public static final int BYTES_PER_KILOBYTE = 1024;
    public static final int DEFAULT_KILOBYTES_PER_SKETCH = 16;
    public static final int DEFAULT_SKETCH_SIZE_IN_BYTES = DEFAULT_KILOBYTES_PER_SKETCH * BYTES_PER_KILOBYTE;

    private static MetricMakerDictionary metricMakerDictionary;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a WikiMetricLoader.
     */
    public WikiMetricLoader() {
        this(new ObjectMapper());
    }

    /**
     * Constructs a WikiMetricLoader using the default sketch size and default dimensionDictionary.
     *
     * @param objectMapper Object mapper for json parse
     */
    public WikiMetricLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * (Re)Initialize the Metric Makers dictionary.
     *
     * @param metricMakers        The configuration of metric makers
     * @param metricDictionary    The dictionary that will be loaded with metrics
     * @param dimensionDictionary The dimension dictionary containing loaded dimensions
     */
    protected void buildMetricMakersDictionary(LinkedHashSet<WikiMetricMakerTemplate> metricMakers,
                                               MetricDictionary metricDictionary,
                                               DimensionDictionary dimensionDictionary) {
        metricMakerDictionary = new MetricMakerDictionary(metricMakers, metricDictionary, dimensionDictionary);
    }

    /**
     * Select and return a Metric Maker by it's name.
     *
     * @param metricMakerName Metric Maker's name
     * @return a specific Metric Maker Instance
     */
    protected MetricMaker selectMetricMakersByName(String metricMakerName) {
        return metricMakerDictionary.findByName(metricMakerName);
    }

    @Override
    public void loadMetricDictionary(MetricDictionary metricDictionary, DimensionDictionary dimensionDictionary) {

        ExternalConfigLoader metricConfigLoader = new ExternalConfigLoader(objectMapper);
        WikiMetricConfigTemplate wikiMetricConfig =
                metricConfigLoader.parseExternalFile("MetricConfigTemplateSample.json",
                        WikiMetricConfigTemplate.class
                );

        buildMetricMakersDictionary(wikiMetricConfig.getMakers(), metricDictionary, dimensionDictionary);

        List<MetricInstance> metrics = wikiMetricConfig.getMetrics().stream().map(
                metric -> new MetricInstance(
                        new LogicalMetricInfo(metric.asName(), metric.getLongName(), metric.getDescription()),
                        selectMetricMakersByName(metric.getMakerName()),
                        metric.getDependencyMetricNames()
                )
        ).collect(Collectors.toList());

        Utils.addToMetricDictionary(metricDictionary, metrics);
        LOG.debug("About to load direct aggregation metrics. Metric dictionary keys: {}", metricDictionary.keySet());
    }
}