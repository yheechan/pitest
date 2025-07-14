/*
 * Detailed test result for mutation testing
 * Captures pass/fail status and exception details for precise baseline comparison
 */
package org.pitest.mutationtest.execute;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents detailed results of a test execution against a mutant,
 * including exception information for precise comparison with baseline.
 */
public class DetailedMutationTestResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String testName;
    private final boolean passed;
    private final String exceptionType;
    private final String exceptionMessage;
    private final String stackTrace;
    private final long executionTimeMs;
    
    public DetailedMutationTestResult(String testName, boolean passed, 
                                    String exceptionType, String exceptionMessage, 
                                    String stackTrace, long executionTimeMs) {
        this.testName = testName;
        this.passed = passed;
        this.exceptionType = exceptionType != null ? exceptionType : "None";
        this.exceptionMessage = exceptionMessage != null ? exceptionMessage : "None";
        this.stackTrace = stackTrace != null ? stackTrace : "None";
        this.executionTimeMs = executionTimeMs;
    }
    
    /**
     * Convenience constructor for passed tests
     */
    public static DetailedMutationTestResult passed(String testName, long executionTimeMs) {
        return new DetailedMutationTestResult(testName, true, "None", "None", "None", executionTimeMs);
    }
    
    /**
     * Convenience constructor for failed tests
     */
    public static DetailedMutationTestResult failed(String testName, Throwable error, long executionTimeMs) {
        String exceptionType = error != null ? error.getClass().getSimpleName() : "Unknown";
        String message = error != null ? error.getMessage() : "Unknown error";
        String stackTrace = error != null ? getStackTraceString(error) : "No stack trace";
        
        return new DetailedMutationTestResult(testName, false, exceptionType, message, stackTrace, executionTimeMs);
    }
    
    /**
     * Convenience constructor for skipped tests (treated as passed)
     */
    public static DetailedMutationTestResult skipped(String testName, long executionTimeMs) {
        return new DetailedMutationTestResult(testName, true, "Skipped", "Test was skipped", "No stack trace", executionTimeMs);
    }
    
    private static String getStackTraceString(Throwable error) {
        if (error == null) {
            return "No stack trace";
        }
        
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        error.printStackTrace(pw);
        pw.close();
        
        // Return full stack trace without truncation, matching baseline test behavior
        return sw.toString().trim();
    }
    
    // Getters
    public String getTestName() { 
        return testName; 
    }
    
    public boolean isPassed() { 
        return passed; 
    }
    
    public String getExceptionType() { 
        return exceptionType; 
    }
    
    public String getExceptionMessage() { 
        return exceptionMessage; 
    }
    
    public String getStackTrace() { 
        return extractRelevantStackTrace(stackTrace);
    }
    
    /**
     * Get the original full stack trace (including infrastructure).
     * This is mainly for debugging purposes.
     */
    public String getFullStackTrace() {
        return stackTrace; 
    }
    
    public long getExecutionTimeMs() { 
        return executionTimeMs; 
    }
    
    /**
     * Compare this result with baseline test case metadata to determine transition type
     */
    public String getTransitionType(TestCaseMetadata baseline) {
        if (baseline == null) {
            return passed ? "NEW_PASS" : "NEW_FAIL";
        }
        
        boolean baselinePassed = baseline.isBaselinePassed();
        
        if (baselinePassed && passed) {
            return "P2P"; // Pass to Pass
        } else if (baselinePassed && !passed) {
            return "P2F"; // Pass to Fail
        } else if (!baselinePassed && passed) {
            return "F2P"; // Fail to Pass
        } else {
            return "F2F"; // Fail to Fail
        }
    }
     /**
     * Check if exception type changed from baseline
     */
    public boolean hasExceptionTypeChanged(TestCaseMetadata baseline) {
        if (baseline == null) {
            return !exceptionType.equals("None");
        }
        return !exceptionType.equals(baseline.getExceptionType());
    }

    /**
     * Check if exception message changed from baseline
     */
    public boolean hasExceptionMessageChanged(TestCaseMetadata baseline) {
        if (baseline == null) {
            return !exceptionMessage.equals("None");
        }
        return !exceptionMessage.equals(baseline.getExceptionMessage());
    }

    /**
     * Check if stack trace changed significantly from baseline.
     * This method compares the filtered stack traces between baseline and mutation execution,
     * focusing on meaningful differences in the actual test execution path.
     * Exception type and message lines are excluded from comparison - use hasExceptionTypeChanged()
     * and hasExceptionMessageChanged() to compare those separately.
     */
    public boolean hasStackTraceChanged(TestCaseMetadata baseline) {
        if (baseline == null) {
            return !stackTrace.equals("None");
        }
        
        // Both getStackTrace() methods now return filtered stack traces
        String baselineStackTrace = baseline.getStackTrace();
        String thisRelevantTrace = this.getStackTrace(); // Already filtered
        
        return !thisRelevantTrace.equals(baselineStackTrace);
    }
    
    /**
     * Extract the relevant part of a stack trace, filtering out PIT infrastructure differences.
     * This focuses on the actual test execution path and user code, ignoring the differences
     * between coverage execution and mutation testing execution infrastructure.
     * Also filters out exception type and message lines to focus only on the call stack.
     */
    private String extractRelevantStackTrace(String stackTrace) {
        return extractRelevantStackTrace(stackTrace, false);
    }
    
    /**
     * Extract the relevant part of a stack trace, filtering out PIT infrastructure differences.
     * This focuses on the actual test execution path and user code, ignoring the differences
     * between coverage execution and mutation testing execution infrastructure.
     * Also filters out exception type and message lines to focus only on the call stack.
     * 
     * @param stackTrace the full stack trace string
     * @param publicAccess if true, this is a public utility method
     * @return filtered stack trace containing only relevant execution information
     */
    public static String extractRelevantStackTrace(String stackTrace, boolean publicAccess) {
        if (stackTrace == null || stackTrace.equals("None") || stackTrace.equals("No stack trace")) {
            return "None";
        }
        
        String[] lines = stackTrace.split("\n");
        StringBuilder relevantTrace = new StringBuilder();
        boolean foundFirstStackTraceLine = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // Skip exception type and message lines (usually first 1-2 lines without "at " prefix)
            if (!foundFirstStackTraceLine && !trimmedLine.startsWith("at ")) {
                continue;
            }
            foundFirstStackTraceLine = true;
            
            // Skip PIT infrastructure lines that differ between coverage and mutation execution
            if (trimmedLine.contains("org.pitest.coverage.execute.")
                || trimmedLine.contains("org.pitest.mutationtest.execute.") 
                || trimmedLine.contains("org.pitest.testapi.execute.")
                || trimmedLine.contains("java.util.concurrent.")
                || trimmedLine.contains("java.lang.Thread.run")
                || trimmedLine.contains("java.util.concurrent.FutureTask")
                || trimmedLine.contains("java.util.concurrent.Executors")) {
                continue;
            }
            
            // Include user code and test framework lines
            if (relevantTrace.length() > 0) {
                relevantTrace.append("\n");
            }
            relevantTrace.append(trimmedLine);
        }
        
        return relevantTrace.toString();
    }
    
    /**
     * Public utility method to extract relevant stack trace from any stack trace string.
     * This can be used by other classes to filter stack traces consistently.
     */
    public static String getRelevantStackTrace(String stackTrace) {
        return extractRelevantStackTrace(stackTrace, true);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(testName, passed, exceptionType, exceptionMessage);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        DetailedMutationTestResult other = (DetailedMutationTestResult) obj;
        return passed == other.passed 
               && Objects.equals(testName, other.testName) 
               && Objects.equals(exceptionType, other.exceptionType) 
               && Objects.equals(exceptionMessage, other.exceptionMessage);
    }
    
    @Override
    public String toString() {
        return String.format("DetailedMutationTestResult{testName='%s', passed=%s, exceptionType='%s', exceptionMessage='%s'}", 
                           testName, passed, exceptionType, exceptionMessage);
    }
}
