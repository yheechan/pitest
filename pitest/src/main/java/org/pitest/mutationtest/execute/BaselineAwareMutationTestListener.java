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

    // Full test case metadata for detailed analysis
    private final Map<String, TestCaseMetadata> testCaseMetadata;
    // Current test results (on mutated code)
    private final Map<String, Boolean> currentResults = new HashMap<>();
    private final List<DetailedMutationTestResult> detailedResults = 
        new ArrayList<>();
    
    private int testsRun = 0;

    /**
     * Create a listener with baseline test results.
     * @param baselineResults Map of test name to passed status from original
     * @deprecated Use fromTestCaseMetadata instead for full functionality
     */
    @Deprecated
    public BaselineAwareMutationTestListener(
        final Map<String, Boolean> baselineResults) {
        this.testCaseMetadata = new HashMap<>();
        // Convert baseline results to minimal metadata
        for (Map.Entry<String, Boolean> entry : baselineResults.entrySet()) {
            TestCaseMetadata metadata = new TestCaseMetadata(
                -1, // No tcID available
                entry.getKey(),
                entry.getValue(),
                "None", // No exception info available
                "None",
                "None",
                0.0 // No execution time available
            );
            this.testCaseMetadata.put(entry.getKey(), metadata);
        }
    }

    /**
     * Create a listener with full test case metadata (preferred method).
     * @param testCaseMetadata Map of test name to full test case metadata
     * @return BaselineAwareMutationTestListener instance
     */
    public static BaselineAwareMutationTestListener fromTestCaseMetadata(
        final Map<String, TestCaseMetadata> testCaseMetadata) {
        BaselineAwareMutationTestListener listener = new BaselineAwareMutationTestListener();
        listener.testCaseMetadata.putAll(testCaseMetadata);
        return listener;
    }

    /**
     * Private constructor for fromTestCaseMetadata factory method.
     */
    private BaselineAwareMutationTestListener() {
        this.testCaseMetadata = new HashMap<>();
    }

    /**
     * Helper method to get baseline result from metadata.
     * @param testName the test name
     * @return baseline result, or null if not found
     */
    private Boolean getBaselineResult(String testName) {
        TestCaseMetadata metadata = this.testCaseMetadata.get(testName);
        return metadata != null ? metadata.isBaselinePassed() : null;
    }

    @Override
    public void onTestStart(final Description d) {
        testsRun++;
    }

    @Override
    public void onTestFailure(final TestResult tr) {
        final String testName = tr.getDescription().getQualifiedName();
        currentResults.put(testName, false);
        
        // Create detailed result with exception information
        DetailedMutationTestResult detailedResult = DetailedMutationTestResult.failed(
            testName, tr.getThrowable(), 0); // TODO: capture actual execution time
        detailedResults.add(detailedResult);
        
        // Analyze exception changes if we have metadata
        if (this.testCaseMetadata.containsKey(testName) && tr.getThrowable() != null) {
            TestCaseMetadata baseline = this.testCaseMetadata.get(testName);
            boolean exceptionTypeChanged = detailedResult.hasExceptionTypeChanged(baseline);
            boolean exceptionMessageChanged = detailedResult.hasExceptionMessageChanged(baseline);
            boolean stackTraceChanged = detailedResult.hasStackTraceChanged(baseline);
            
            if (exceptionTypeChanged || exceptionMessageChanged || stackTraceChanged) {
                System.out.println("DEBUG: Exception details changed for test " + testName 
                    + " - Type: " + exceptionTypeChanged 
                    + ", Message: " + exceptionMessageChanged 
                    + ", StackTrace: " + stackTraceChanged);
            }
        }
    }

    @Override
    public void onTestSuccess(final TestResult tr) {
        final String testName = tr.getDescription().getQualifiedName();
        currentResults.put(testName, true);
        
        DetailedMutationTestResult detailedResult = DetailedMutationTestResult.passed(
            testName, 0); // TODO: capture actual execution time
        detailedResults.add(detailedResult);
    }

    @Override
    public void onTestSkipped(final TestResult tr) {
        final String testName = tr.getDescription().getQualifiedName();
        // Treat skipped as not affecting mutation status - use baseline result
        final Boolean baselineResult = getBaselineResult(testName);
        if (baselineResult != null) {
            currentResults.put(testName, baselineResult);
        }
        
        DetailedMutationTestResult detailedResult = DetailedMutationTestResult.skipped(
            testName, 0); // TODO: capture actual execution time
        detailedResults.add(detailedResult);
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
            
            final Boolean baselinePassed = getBaselineResult(testName);
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
                        getBaselineResult(testName);
                    
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
                        getBaselineResult(testName);
                    
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
    public List<DetailedMutationTestResult> getTestResults() {
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
        Map<String, Boolean> baselineResults = new HashMap<>();
        for (Map.Entry<String, TestCaseMetadata> entry : testCaseMetadata.entrySet()) {
            baselineResults.put(entry.getKey(), entry.getValue().isBaselinePassed());
        }
        return baselineResults;
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
        final Boolean baselinePassed = getBaselineResult(testName);
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

    /**
     * Get test case metadata for a specific test if available.
     * @param testName the test name
     * @return test case metadata or null if not available
     */
    public TestCaseMetadata getTestCaseMetadata(String testName) {
        return this.testCaseMetadata.get(testName);
    }

    /**
     * Analyze whether exception type changed between baseline and mutation.
     * @param testName the test name
     * @param currentException the current exception (can be null)
     * @return true if exception type changed
     */
    public boolean hasExceptionTypeChanged(String testName, Throwable currentException) {
        TestCaseMetadata metadata = this.testCaseMetadata.get(testName);
        if (metadata == null) {
            return false; // No metadata to compare
        }

        String baselineExceptionType = metadata.getExceptionType();
        String currentExceptionType = currentException != null 
            ? currentException.getClass().getName() : "None";

        return !baselineExceptionType.equals(currentExceptionType);
    }

    /**
     * Analyze whether exception message changed between baseline and mutation.
     * @param testName the test name
     * @param currentException the current exception (can be null)
     * @return true if exception message changed
     */
    public boolean hasExceptionMessageChanged(String testName, Throwable currentException) {
        TestCaseMetadata metadata = this.testCaseMetadata.get(testName);
        if (metadata == null) {
            return false; // No metadata to compare
        }

        String baselineExceptionMessage = metadata.getExceptionMessage();
        String currentExceptionMessage = currentException != null 
            ? (currentException.getMessage() != null ? currentException.getMessage() : "None") : "None";

        return !baselineExceptionMessage.equals(currentExceptionMessage);
    }

    /**
     * Analyze whether stack trace changed between baseline and mutation.
     * @param testName the test name
     * @param currentException the current exception (can be null)
     * @return true if stack trace changed
     */
    public boolean hasStackTraceChanged(String testName, Throwable currentException) {
        TestCaseMetadata metadata = this.testCaseMetadata.get(testName);
        if (metadata == null) {
            return false; // No metadata to compare
        }

        String baselineStackTrace = metadata.getStackTrace();
        String currentStackTrace = "None";
        
        if (currentException != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            currentException.printStackTrace(pw);
            currentStackTrace = sw.toString();
        }

        return !baselineStackTrace.equals(currentStackTrace);
    }

    /**
     * Get detailed transition analysis for a test including exception changes.
     * @param testName the test name
     * @param currentException the current exception (can be null)
     * @return detailed transition info
     */
    public String getDetailedTransition(String testName, Throwable currentException) {
        TestCaseMetadata metadata = this.testCaseMetadata.get(testName);
        if (metadata == null) {
            // Fallback to simple transition analysis
            Boolean baselinePassed = getBaselineResult(testName);
            Boolean currentPassed = this.currentResults.get(testName);
            
            if (baselinePassed == null || currentPassed == null) {
                return "UNKNOWN";
            }
            
            if (baselinePassed && !currentPassed) {
                return "P2F";
            }
            if (!baselinePassed && currentPassed) {
                return "F2P";
            }
            if (baselinePassed && currentPassed) {
                return "P2P";
            }
            if (!baselinePassed && !currentPassed) {
                return "F2F";
            }
            return "UNKNOWN";
        }

        boolean baselinePassed = metadata.isBaselinePassed();
        Boolean currentPassed = this.currentResults.get(testName);
        
        if (currentPassed == null) {
            return "UNKNOWN";
        }

        // Basic transition
        String transition;
        if (baselinePassed && !currentPassed) {
            transition = "P2F";
        } else if (!baselinePassed && currentPassed) {
            transition = "F2P";
        } else if (baselinePassed && currentPassed) {
            transition = "P2P";
        } else {
            transition = "F2F";
        }

        // Add exception analysis details
        boolean exceptionTypeChanged = hasExceptionTypeChanged(testName, currentException);
        boolean exceptionMessageChanged = hasExceptionMessageChanged(testName, currentException);
        boolean stackTraceChanged = hasStackTraceChanged(testName, currentException);

        if (exceptionTypeChanged || exceptionMessageChanged || stackTraceChanged) {
            transition += "_EX"; // Mark that exception details changed
        }

        return transition;
    }
}
