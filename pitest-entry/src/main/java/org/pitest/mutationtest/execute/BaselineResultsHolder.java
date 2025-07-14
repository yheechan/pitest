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
    private static volatile Map<String, Integer> testCaseIdMapping = new HashMap<>();
    
    // Complete test case metadata for research mode
    private static volatile Map<String, TestCaseMetadata> testCaseMetadata = new HashMap<>();

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
     * Set test case ID mapping (test name -> tcID)
     */
    public static synchronized void setTestCaseIdMapping(Map<String, Integer> mapping) {
        testCaseIdMapping.clear();
        if (mapping != null) {
            testCaseIdMapping.putAll(mapping);
        }
        System.out.println("DEBUG: BaselineResultsHolder.setTestCaseIdMapping called with " 
            + (mapping != null ? mapping.size() : 0) + " test mappings");
    }

    /**
     * Get test case ID mapping
     */
    public static synchronized Map<String, Integer> getTestCaseIdMapping() {
        System.out.println("DEBUG: BaselineResultsHolder.getTestCaseIdMapping called, returning " 
            + testCaseIdMapping.size() + " test mappings");
        return new HashMap<>(testCaseIdMapping);
    }

    /**
     * Get test case ID for a specific test name
     */
    public static synchronized Integer getTestCaseId(String testName) {
        return testCaseIdMapping.get(testName);
    }

    /**
     * Clear all data
     */
    public static synchronized void clearAll() {
        baselineResults.clear();
        failingTestLines.clear();
        failingTestLinesByClass.clear();
        testCaseIdMapping.clear();
        testCaseMetadata.clear();
    }

    /**
     * Set complete test case metadata for research mode
     */
    public static synchronized void setTestCaseMetadata(Map<String, TestCaseMetadata> metadata) {
        testCaseMetadata.clear();
        if (metadata != null) {
            testCaseMetadata.putAll(metadata);
            System.out.println("DEBUG: BaselineResultsHolder.setTestCaseMetadata - stored " + metadata.size() + " test case metadata entries");
        }
    }

    /**
     * Get complete test case metadata for research mode
     */
    public static synchronized Map<String, TestCaseMetadata> getTestCaseMetadata() {
        System.out.println("DEBUG: BaselineResultsHolder.getTestCaseMetadata - returning " + testCaseMetadata.size() + " test case metadata entries");
        return new HashMap<>(testCaseMetadata);
    }

    /**
     * Check if test case metadata is available
     */
    public static synchronized boolean hasTestCaseMetadata() {
        return !testCaseMetadata.isEmpty();
    }
}
