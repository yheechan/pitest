/*
 * Custom listener for capturing full mutation-test matrix data
 * For fault localization research on Defects4J
 */
package org.pitest.mutationtest.execute;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.testapi.Description;
import org.pitest.testapi.TestListener;
import org.pitest.testapi.TestResult;

/**
 * Enhanced test listener that captures detailed per-test results
 * for full mutation-test matrix generation.
 */
public class FullMutationMatrixListener implements TestListener {

    private final List<TestExecutionResult> testResults = new ArrayList<>();
    private int testsRun = 0;
    private boolean hasAnyFailure = false;

    /**
     * Represents the result of executing a single test against a mutant
     */
    public static class TestExecutionResult {
        private final String testName;
        private final boolean killed; // true if test failed (killed mutant), false if passed
        private final long executionTimeMs;
        
        public TestExecutionResult(String testName, boolean killed, long executionTimeMs) {
            this.testName = testName;
            this.killed = killed;
            this.executionTimeMs = executionTimeMs;
        }
        
        public String getTestName() {
            return testName;
        }
        public boolean isKilled() {
            return killed;
        }
        public long getExecutionTimeMs() {
            return executionTimeMs;
        }
    }

    @Override
    public void onTestStart(Description d) {
        testsRun++;
    }

    @Override
    public void onTestFailure(TestResult tr) {
        hasAnyFailure = true;
        testResults.add(new TestExecutionResult(
            tr.getDescription().getQualifiedName(), 
            true, // test failed = mutant killed
            0 // TODO: capture actual execution time if needed
        ));
    }

    @Override
    public void onTestSuccess(TestResult tr) {
        testResults.add(new TestExecutionResult(
            tr.getDescription().getQualifiedName(), 
            false, // test passed = mutant survived
            0 // TODO: capture actual execution time if needed
        ));
    }

    @Override
    public void onTestSkipped(TestResult tr) {
        // Count as not killing the mutant
        testResults.add(new TestExecutionResult(
            tr.getDescription().getQualifiedName(), 
            false, 
            0
        ));
    }

    @Override
    public void onRunStart() {
        // Reset for new mutation
        testResults.clear();
        testsRun = 0;
        hasAnyFailure = false;
    }

    @Override
    public void onRunEnd() {
        // Nothing to do
    }

    /**
     * Get the overall status for this mutation
     */
    public DetectionStatus getOverallStatus() {
        return hasAnyFailure ? DetectionStatus.KILLED : DetectionStatus.SURVIVED;
    }

    /**
     * Get detailed results for each test
     */
    public List<TestExecutionResult> getTestResults() {
        return new ArrayList<>(testResults);
    }

    /**
     * Get list of tests that killed the mutant
     */
    public List<String> getKillingTests() {
        return testResults.stream()
            .filter(TestExecutionResult::isKilled)
            .map(TestExecutionResult::getTestName)
            .collect(Collectors.toList());
    }

    /**
     * Get list of tests that didn't kill the mutant
     */
    public List<String> getSurvivingTests() {
        return testResults.stream()
            .filter(result -> !result.isKilled())
            .map(TestExecutionResult::getTestName)
            .collect(Collectors.toList());
    }

    public int getNumberOfTestsRun() {
        return testsRun;
    }
}
