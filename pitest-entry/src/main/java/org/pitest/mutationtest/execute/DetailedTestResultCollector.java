/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.execute;

import org.pitest.testapi.Description;
import org.pitest.testapi.TestListener;
import org.pitest.testapi.TestResult;
import org.pitest.testapi.TestUnitExecutionListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Test listener that captures detailed test result information including exception details.
 * This allows us to preserve real exception information from test execution for later use
 * in CSV output. Implements both TestListener and TestUnitExecutionListener to capture
 * detailed results from both regular test execution and test discovery execution.
 */
public class DetailedTestResultCollector implements TestListener, TestUnitExecutionListener {
    
    // Thread-safe storage for detailed test results
    private static final Map<String, DetailedTestResult> TEST_RESULTS = new ConcurrentHashMap<>();
    
    // Thread-safe storage for test start times (for execution time calculation)
    private static final Map<String, Long> TEST_START_TIMES = new ConcurrentHashMap<>();
    
    /**
     * Detailed test result with exception information
     */
    public static class DetailedTestResult {
        private final String testName;
        private final boolean passed;
        private final long executionTimeMs;
        private final String exceptionType;
        private final String exceptionMessage;
        private final String stackTrace;
        
        public DetailedTestResult(String testName, boolean passed, long executionTimeMs,
                                 String exceptionType, String exceptionMessage, String stackTrace) {
            this.testName = testName;
            this.passed = passed;
            this.executionTimeMs = executionTimeMs;
            this.exceptionType = exceptionType != null ? exceptionType : "";
            this.exceptionMessage = exceptionMessage != null ? exceptionMessage : "";
            this.stackTrace = stackTrace != null ? stackTrace : "";
        }
        
        public String getTestName() {
            return testName;
        }
        
        public boolean isPassed() {
            return passed;
        }
        
        public long getExecutionTimeMs() {
            return executionTimeMs;
        }
        
        public String getExceptionType() {
            return exceptionType;
        }
        
        public String getExceptionMessage() {
            return exceptionMessage;
        }
        
        public String getStackTrace() {
            return stackTrace;
        }
    }
    
    // TestListener interface implementation
    @Override
    public void onRunStart() {
        // Clear any previous results at the start of a new run
        TEST_RESULTS.clear();
        TEST_START_TIMES.clear();
    }
    
    @Override
    public void onTestStart(Description d) {
        TEST_START_TIMES.put(d.getQualifiedName(), System.currentTimeMillis());
    }
    
    @Override
    public void onTestFailure(TestResult tr) {
        String testName = tr.getDescription().getQualifiedName();
        long executionTime = calculateExecutionTime(testName);
        
        String exceptionType = "";
        String exceptionMessage = "";
        String stackTrace = "";
        
        Throwable throwable = tr.getThrowable();
        if (throwable != null) {
            exceptionType = throwable.getClass().getSimpleName();
            exceptionMessage = throwable.getMessage() != null ? throwable.getMessage() : "";
            stackTrace = getStackTraceString(throwable);
        }
        
        DetailedTestResult result = new DetailedTestResult(
            testName, false, executionTime, exceptionType, exceptionMessage, stackTrace);
        TEST_RESULTS.put(testName, result);
        
        TEST_START_TIMES.remove(testName);
    }
    
    @Override
    public void onTestSuccess(TestResult tr) {
        String testName = tr.getDescription().getQualifiedName();
        long executionTime = calculateExecutionTime(testName);
        
        DetailedTestResult result = new DetailedTestResult(
            testName, true, executionTime, "", "", "");
        TEST_RESULTS.put(testName, result);
        
        TEST_START_TIMES.remove(testName);
    }
    
    @Override
    public void onTestSkipped(TestResult tr) {
        String testName = tr.getDescription().getQualifiedName();
        long executionTime = calculateExecutionTime(testName);
        
        DetailedTestResult result = new DetailedTestResult(
            testName, true, executionTime, "", "", ""); // Treat skipped as passed
        TEST_RESULTS.put(testName, result);
        
        TEST_START_TIMES.remove(testName);
    }
    
    @Override
    public void onRunEnd() {
        // Results are preserved in static storage for access by other components
    }
    
    // TestUnitExecutionListener interface implementation
    @Override
    public void executionStarted(Description description) {
        TEST_START_TIMES.put(description.getQualifiedName(), System.currentTimeMillis());
    }
    
    @Override
    public void executionFinished(Description description, boolean passed, Throwable error) {
        String testName = description.getQualifiedName();
        long executionTime = calculateExecutionTime(testName);
        
        String exceptionType = "";
        String exceptionMessage = "";
        String stackTrace = "";
        
        if (error != null) {
            exceptionType = error.getClass().getSimpleName();
            exceptionMessage = error.getMessage() != null ? error.getMessage() : "";
            stackTrace = getStackTraceString(error);
        }
        
        DetailedTestResult result = new DetailedTestResult(
            testName, passed, executionTime, exceptionType, exceptionMessage, stackTrace);
        TEST_RESULTS.put(testName, result);
        
        TEST_START_TIMES.remove(testName);
    }
    
    private long calculateExecutionTime(String testName) {
        Long startTime = TEST_START_TIMES.get(testName);
        if (startTime != null) {
            return System.currentTimeMillis() - startTime;
        }
        return 0; // Default if timing info not available
    }
    
    private String getStackTraceString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.close();
        
        // Return full stack trace without truncation
        return sw.toString();
    }
    
    /**
     * Get all captured test results
     */
    public static Map<String, DetailedTestResult> getAllTestResults() {
        return new ConcurrentHashMap<>(TEST_RESULTS);
    }
    
    /**
     * Get detailed result for a specific test
     */
    public static DetailedTestResult getTestResult(String testName) {
        return TEST_RESULTS.get(testName);
    }
    
    /**
     * Clear all captured results
     */
    public static void clearResults() {
        TEST_RESULTS.clear();
    }
    
    /**
     * Check if we have any captured results
     */
    public static boolean hasResults() {
        return !TEST_RESULTS.isEmpty();
    }
    
    /**
     * Store a test result directly (for use by other listeners)
     */
    public static void storeTestResult(String testName, DetailedTestResult result) {
        TEST_RESULTS.put(testName, result);
    }
}
