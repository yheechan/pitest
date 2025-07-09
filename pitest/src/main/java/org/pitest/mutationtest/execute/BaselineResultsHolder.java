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
    private static volatile Set<Integer> failingTestLines = new HashSet<>();
    private static volatile Map<String, Set<Integer>> failingTestLinesByClass = new HashMap<>();

    /**
     * Set baseline results 
     */
    public static synchronized void setBaselineResults(Map<String, Boolean> results) {
        baselineResults.clear();
        if (results != null) {
            baselineResults.putAll(results);
        }
        System.out.println("DEBUG: BaselineResultsHolder.setBaselineResults called with " 
                          + (results != null ? results.size() : 0) + " results");
    }

    /**
     * Set lines covered by failing tests
     */
    public static synchronized void setFailingTestLines(Set<Integer> lines) {
        failingTestLines.clear();
        if (lines != null) {
            failingTestLines.addAll(lines);
        }
        System.out.println("DEBUG: BaselineResultsHolder.setFailingTestLines called with " 
                          + (lines != null ? lines.size() : 0) + " lines");
    }

    /**
     * Set lines covered by failing tests for each class
     */
    public static synchronized void setFailingTestLinesByClass(Map<String, Set<Integer>> linesByClass) {
        failingTestLinesByClass.clear();
        if (linesByClass != null) {
            failingTestLinesByClass.putAll(linesByClass);
        }
        System.out.println("DEBUG: BaselineResultsHolder.setFailingTestLinesByClass called with " 
                          + (linesByClass != null ? linesByClass.size() : 0) + " classes");
    }

    /**
     * Get baseline results 
     */
    public static synchronized Map<String, Boolean> getBaselineResults() {
        System.out.println("DEBUG: BaselineResultsHolder.getBaselineResults called, returning " 
                          + baselineResults.size() + " results");
        return new HashMap<>(baselineResults);
    }

    /**
     * Get lines covered by failing tests
     */
    public static synchronized Set<Integer> getFailingTestLines() {
        return new HashSet<>(failingTestLines);
    }

    /**
     * Get lines covered by failing tests for a specific class
     */
    public static synchronized Set<Integer> getFailingTestLinesForClass(String className) {
        Set<Integer> lines = failingTestLinesByClass.get(className);
        return lines != null ? new HashSet<>(lines) : new HashSet<>();
    }

    /**
     * Get lines covered by failing tests for all classes
     */
    public static synchronized Map<String, Set<Integer>> getFailingTestLinesByClass() {
        Map<String, Set<Integer>> result = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : failingTestLinesByClass.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Clear baseline results 
     */
    public static synchronized void clearBaselineResults() {
        baselineResults.clear();
        failingTestLines.clear();
        failingTestLinesByClass.clear();
    }

    /**
     * Check if baseline results are available
     */
    public static synchronized boolean hasBaselineResults() {
        return !baselineResults.isEmpty();
    }
}
