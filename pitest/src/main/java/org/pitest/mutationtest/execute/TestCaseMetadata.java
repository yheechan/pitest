/*
 * Test case metadata for mutation testing minions
 * Contains baseline test results and exception details
 */
package org.pitest.mutationtest.execute;

import java.io.Serializable;

/**
 * Metadata for a test case sent from main process to mutation minions.
 * Contains baseline execution results and exception details for comparison.
 */
public class TestCaseMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int tcID;
    private final String testName;
    private final boolean baselinePassed;
    private final String exceptionType;
    private final String exceptionMessage;
    private final String stackTrace;
    private final double executionTimeMs;

    public TestCaseMetadata(int tcID, String testName, boolean baselinePassed, 
                           String exceptionType, String exceptionMessage, String stackTrace,
                           double executionTimeMs) {
        this.tcID = tcID;
        this.testName = testName;
        this.baselinePassed = baselinePassed;
        this.exceptionType = exceptionType != null ? exceptionType : "None";
        this.exceptionMessage = exceptionMessage != null ? exceptionMessage : "None";
        this.stackTrace = stackTrace != null ? stackTrace : "None";
        this.executionTimeMs = executionTimeMs;
    }

    public int getTcID() {
        return tcID;
    }

    public String getTestName() {
        return testName;
    }

    public boolean isBaselinePassed() {
        return baselinePassed;
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

    public double getExecutionTimeMs() {
        return executionTimeMs;
    }

    @Override
    public String toString() {
        return "TestCaseMetadata{"
                + "tcID=" + tcID
                + ", testName='" + testName + '\''
                + ", baselinePassed=" + baselinePassed
                + ", exceptionType='" + exceptionType + '\''
                + ", executionTimeMs=" + executionTimeMs
                + '}';
    }
}
