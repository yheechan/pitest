/*
 * Baseline results holder for research mode
 * For fault localization research on Defects4J
 */
package org.pitest.mutationtest.execute;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple holder for baseline test results to be accessed by CSV reporter
 * in research mode. Uses a static variable instead of ThreadLocal for cross-thread access.
 */
public class BaselineResultsHolder {
    private static volatile Map<String, Boolean> baselineResults = new HashMap<>();
    private static volatile Set<String> failingTestLines = new HashSet<>();
    private static volatile Map<String, Set<Integer>> failingTestLinesByClass = new HashMap<>();

    /**
     * Set baseline results 
     */
    public static synchronized void setBaselineResults(Map<String, Boolean> results) {
        baselineResults.clear();
        if (results != null) {
            baselineResults.putAll(results);
        }
    }

    /**
     * Get baseline results 
     */
    public static synchronized Map<String, Boolean> getBaselineResults() {
        return new HashMap<>(baselineResults);
    }

    /**
     * Clear baseline results 
     */
    public static synchronized void clearBaselineResults() {
        baselineResults.clear();
    }

    /**
     * Check if baseline results are available
     */
    public static synchronized boolean hasBaselineResults() {
        return !baselineResults.isEmpty();
    }

    /**
     * Set failing test lines data
     */
    public static synchronized void setFailingTestLines(Set<String> lines) {
        failingTestLines.clear();
        if (lines != null) {
            failingTestLines.addAll(lines);
        }
    }

    /**
     * Get failing test lines as class:line strings
     */
    public static synchronized Set<String> getFailingTestLines() {
        return new HashSet<>(failingTestLines);
    }

    /**
     * Set failing test lines by class data
     */
    public static synchronized void setFailingTestLinesByClass(Map<String, Set<Integer>> linesByClass) {
        failingTestLinesByClass.clear();
        if (linesByClass != null) {
            failingTestLinesByClass.putAll(linesByClass);
        }
    }

    /**
     * Get failing test lines by class
     */
    public static synchronized Map<String, Set<Integer>> getFailingTestLinesByClass() {
        return new HashMap<>(failingTestLinesByClass);
    }

    /**
     * Clear all data
     */
    public static synchronized void clearAll() {
        baselineResults.clear();
        failingTestLines.clear();
        failingTestLinesByClass.clear();
    }
}
