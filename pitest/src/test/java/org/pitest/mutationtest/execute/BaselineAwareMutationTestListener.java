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
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package org.pitest.mutationtest.execute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.pitest.mutationtest.DetectionStatus;
import org.pitest.testapi.Description;
import org.pitest.testapi.TestListener;
import org.pitest.testapi.TestResult;

/**
 * A mutation test listener that correctly determines mutation kill/survive
 * status by comparing test results against baseline (original) results.
 * 
 * A mutation is KILLED when:
 * - A test that originally passed now fails, OR
 * - A test that originally failed now passes
 * 
 * A mutation SURVIVES when all test results remain the same as baseline.
 */
public class BaselineAwareMutationTestListener implements TestListener {

    /**
     * Represents the result of a single test execution.
     */
    public static class TestExecutionResult {
        private final String testName;
        private final boolean passed;
        private final String errorMessage;
        private final long executionTimeMs;

        /**
         * Construct test execution result.
         * @param testName name of the test
         * @param passed whether test passed
         * @param errorMessage error message if failed
         * @param executionTimeMs execution time in milliseconds
         */
        public TestExecutionResult(final String testName, final boolean passed,
                                   final String errorMessage, 
                                   final long executionTimeMs) {
            this.testName = testName;
            this.passed = passed;
            this.errorMessage = errorMessage;
            this.executionTimeMs = executionTimeMs;
        }

        /**
         * Get test name.
         * @return test name
         */
        public String getTestName() {
            return testName;
        }

        /**
         * Check if test passed.
         * @return true if test passed
         */
        public boolean isPassed() {
            return passed;
        }

        /**
         * Get error message.
         * @return error message if failed, null otherwise
         */
        public String getErrorMessage() {
            return errorMessage;
        }

        /**
         * Get execution time.
         * @return execution time in milliseconds
         */
        public long getExecutionTimeMs() {
            return executionTimeMs;
        }
    }

    // Baseline test results (on original code)
    private final Map<String, Boolean> baselineResults;
    
    // Current test results (on mutated code)
    private final Map<String, Boolean> currentResults = new HashMap<>();
    private final List<TestExecutionResult> detailedResults = 
        new ArrayList<>();
    
    private int testsRun = 0;

    /**
     * Create a listener with baseline test results.
     * @param baselineResults Map of test name to passed status from original
     */
    public BaselineAwareMutationTestListener(
        final Map<String, Boolean> baselineResults) {
        this.baselineResults = new HashMap<>(baselineResults);
    }

    @Override
    public void onTestStart(final Description d) {
        testsRun++;
    }

    @Override
    public void onTestFailure(final TestResult tr) {
        final String testName = tr.getDescription().getQualifiedName();
        currentResults.put(testName, false);
        
        final String errorMessage = tr.getThrowable() != null 
            ? tr.getThrowable().getMessage() : "Unknown error";
        detailedResults.add(new TestExecutionResult(testName, false, 
                                                    errorMessage, 0));
    }

    @Override
    public void onTestSuccess(final TestResult tr) {
        final String testName = tr.getDescription().getQualifiedName();
        currentResults.put(testName, true);
        
        detailedResults.add(new TestExecutionResult(testName, true, null, 0));
    }

    @Override
    public void onTestSkipped(final TestResult tr) {
        final String testName = tr.getDescription().getQualifiedName();
        // Treat skipped as not affecting mutation status - use baseline result
        final Boolean baselineResult = baselineResults.get(testName);
        if (baselineResult != null) {
            currentResults.put(testName, baselineResult);
        }
        
        detailedResults.add(new TestExecutionResult(testName, 
            baselineResult != null ? baselineResult : false, "SKIPPED", 0));
    }

    @Override
    public void onRunStart() {
        currentResults.clear();
        detailedResults.clear();
        testsRun = 0;
    }

    @Override
    public void onRunEnd() {
        // Nothing to do
    }

    /**
     * Determine if the mutation was killed by comparing current results 
     * with baseline.
     * @return detection status
     */
    public DetectionStatus getOverallStatus() {
        for (final Map.Entry<String, Boolean> entry 
             : currentResults.entrySet()) {
            final String testName = entry.getKey();
            final boolean currentPassed = entry.getValue();
            
            final Boolean baselinePassed = baselineResults.get(testName);
            if (baselinePassed == null) {
                // Test not in baseline - treat as new test, 
                // if it fails the mutation is killed
                if (!currentPassed) {
                    return DetectionStatus.KILLED;
                }
                continue;
            }
            
            // Check if result changed from baseline
            if (baselinePassed != currentPassed) {
                return DetectionStatus.KILLED;
            }
        }
        
        return DetectionStatus.SURVIVED;
    }

    /**
     * Get tests that killed the mutant (changed result from baseline).
     * @return list of killing test names
     */
    public List<String> getKillingTests() {
        return currentResults.entrySet().stream()
                .filter(entry -> {
                    final String testName = entry.getKey();
                    final boolean currentPassed = entry.getValue();
                    final Boolean baselinePassed = 
                        baselineResults.get(testName);
                    
                    // Test killed mutation if result changed from baseline
                    return baselinePassed != null 
                        && baselinePassed != currentPassed;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get tests that did not kill the mutant (same result as baseline).
     * @return list of surviving test names
     */
    public List<String> getSurvivingTests() {
        return currentResults.entrySet().stream()
                .filter(entry -> {
                    final String testName = entry.getKey();
                    final boolean currentPassed = entry.getValue();
                    final Boolean baselinePassed = 
                        baselineResults.get(testName);
                    
                    // Test did not kill mutation if result same as baseline
                    return baselinePassed != null 
                        && baselinePassed.equals(currentPassed);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get detailed results for each test.
     * @return list of test execution results
     */
    public List<TestExecutionResult> getTestResults() {
        return new ArrayList<>(detailedResults);
    }

    /**
     * Get number of tests run.
     * @return number of tests run
     */
    public int getNumberOfTestsRun() {
        return testsRun;
    }

    /**
     * Get baseline results for debugging.
     * @return copy of baseline results
     */
    public Map<String, Boolean> getBaselineResults() {
        return new HashMap<>(baselineResults);
    }

    /**
     * Get current results for debugging.
     * @return copy of current results
     */
    public Map<String, Boolean> getCurrentResults() {
        return new HashMap<>(currentResults);
    }

    /**
     * Get the baseline transition type for a specific test.
     * @param testName the test name
     * @return the transition type (P2F, F2P, P2P, F2F)
     */
    public String getTestTransitionType(final String testName) {
        final Boolean baselinePassed = baselineResults.get(testName);
        final Boolean currentPassed = currentResults.get(testName);
        
        if (baselinePassed == null || currentPassed == null) {
            return "UNKNOWN";
        }
        
        if (baselinePassed && !currentPassed) {
            return "P2F"; // Passing to Failing - mutation killed this test
        } else if (!baselinePassed && currentPassed) {
            return "F2P"; // Failing to Passing - mutation fixed this test
        } else if (baselinePassed && currentPassed) {
            return "P2P"; // Passing to Passing - no change
        } else {
            return "F2F"; // Failing to Failing - no change
        }
    }

    /**
     * Get transition types for all tests.
     * @return Map of test name to transition type
     */
    public Map<String, String> getAllTestTransitionTypes() {
        final Map<String, String> transitions = new HashMap<>();
        for (final String testName : currentResults.keySet()) {
            transitions.put(testName, getTestTransitionType(testName));
        }
        return transitions;
    }
}