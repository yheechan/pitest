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
     * Check if stack trace changed significantly from baseline
     */
    public boolean hasStackTraceChanged(TestCaseMetadata baseline) {
        if (baseline == null) {
            return !stackTrace.equals("None");
        }
        
        String baselineStackTrace = baseline.getStackTrace();
        
        // For stack trace comparison, we'll check if the main exception line differs
        // This is more robust than full string comparison due to line number changes
        String thisMainLine = extractMainExceptionLine(this.stackTrace);
        String baselineMainLine = extractMainExceptionLine(baselineStackTrace);
        
        return !thisMainLine.equals(baselineMainLine);
    }
    
    private String extractMainExceptionLine(String stackTrace) {
        if (stackTrace == null || stackTrace.equals("None") || stackTrace.equals("No stack trace")) {
            return "None";
        }
        
        String[] lines = stackTrace.split("\n");
        if (lines.length > 0) {
            // Return the first line which usually contains the exception type and message
            return lines[0].trim();
        }
        return "None";
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
