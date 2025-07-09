/*
 * CSV exporter for detailed mutation-test matrix data
 * For fault localization research on Defects4J
 */
package org.pitest.mutationtest.report.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.execute.BaselineResultsHolder;
import org.pitest.util.ResultOutputStrategy;

/**
 * Enhanced CSV reporter that outputs detailed mutation-test matrix data
 * Format: mutant_id,class,method,line,mutator,test_name,transition,status
 */
public class FullMutationMatrixCSVReportListener implements MutationResultListener {

    private final Writer out;

    public FullMutationMatrixCSVReportListener(final ResultOutputStrategy outputStrategy) throws IOException {
        this.out = outputStrategy.createWriterForFile("full_mutation_matrix.csv");
    }

    @Override
    public void runStart() {
        try {
            // Write CSV header with new transition column
            out.write("mutant_id,class,method,line,mutator,test_name,transition,status,num_tests_run\n");
        } catch (IOException e) {
            throw new RuntimeException("Error writing CSV header", e);
        }
    }

    @Override
    public void handleMutationResult(ClassMutationResults metaData) {
        try {
            for (MutationResult mutation : metaData.getMutations()) {
                String mutantId = mutation.getDetails().getId().toString();
                String className = mutation.getDetails().getClassName().asJavaName();
                String methodName = mutation.getDetails().getMethod();
                int lineNumber = mutation.getDetails().getLineNumber();
                String mutatorName = mutation.getDetails().getMutator();
                String status = mutation.getStatus().toString();
                int numTestsRun = mutation.getNumberOfTestsRun();

                // Get all tests that were run for this mutant
                List<String> killingTests = mutation.getKillingTests();
                List<String> survivingTests = mutation.getSucceedingTests();
                List<String> coveringTests = mutation.getCoveringTests();

                // In research mode, we want all tests that were actually executed
                // This includes both killing and surviving tests
                java.util.Set<String> allExecutedTests = new java.util.HashSet<>();
                allExecutedTests.addAll(killingTests);
                allExecutedTests.addAll(survivingTests);
                
                // If no tests were executed from baseline-aware results, fall back to covering tests
                if (allExecutedTests.isEmpty()) {
                    allExecutedTests.addAll(coveringTests);
                }

                // Output one row per test that was executed on this mutant
                if (!allExecutedTests.isEmpty()) {
                    for (String testName : allExecutedTests) {
                        String transition = getTestTransition(testName, killingTests, survivingTests);
                        writeRow(mutantId, className, methodName, lineNumber, 
                                mutatorName, testName, transition, status, numTestsRun);
                    }
                } else {
                    // No tests executed - output one row with empty test name
                    writeRow(mutantId, className, methodName, lineNumber, 
                            mutatorName, "NO_COVERAGE", "UNKNOWN", status, numTestsRun);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing mutation data", e);
        }
    }

    private String getTestTransition(String testName, List<String> killingTests, List<String> survivingTests) {
        // Get baseline results from the holder
        Map<String, Boolean> baselineResults = BaselineResultsHolder.getBaselineResults();
        
        // If static holder is empty, try file-based holder
        if (baselineResults == null || baselineResults.isEmpty()) {
            baselineResults = org.pitest.mutationtest.execute.BaselineResultsFileHolder.loadBaselineResults();
        }
        
        if (baselineResults == null || baselineResults.isEmpty()) {
            // Fallback to old behavior if no baseline available
            if (killingTests.contains(testName)) {
                return "KILLED";
            } else if (survivingTests.contains(testName)) {
                return "SURVIVED";
            } else {
                return "UNKNOWN";
            }
        }

        Boolean baselinePassed = baselineResults.get(testName);
        if (baselinePassed == null) {
            // Debug: Test not found in baseline results
            System.err.println("DEBUG: Test '" + testName + "' not found in baseline results. Available tests: " + baselineResults.keySet());
            return "UNKNOWN";
        }

        boolean currentPassed = survivingTests.contains(testName);
        boolean currentFailed = killingTests.contains(testName);

        // Check if this is a NON_VIABLE mutation (no tests were executed)
        boolean noTestsRun = !currentPassed && !currentFailed;
        
        if (noTestsRun) {
            // For NON_VIABLE mutations, we assume the test would have the same result as baseline
            // since the mutation couldn't be executed properly
            System.err.println("DEBUG: NON_VIABLE mutation for test '" + testName + "', baseline passed: " + baselinePassed);
            if (baselinePassed) {
                return "P2P"; // Test passed in baseline, would pass on mutation (no change)
            } else {
                return "F2F"; // Test failed in baseline, would fail on mutation (no change)
            }
        }

        if (baselinePassed && currentFailed) {
            // Test passed in baseline but failed on mutation (test result changed)
            return "P2F"; 
        } else if (baselinePassed && currentPassed) {
            // Test passed in baseline and passed on mutation (no change)
            return "P2P"; 
        } else if (!baselinePassed && currentFailed) {
            // Test failed in baseline but "killed" the mutation (test result changed)
            // This means the test failed in baseline but passed on mutation
            return "F2P";
        } else if (!baselinePassed && currentPassed) {
            // Test failed in baseline and survived the mutation (no change)
            // This means the test failed in both baseline and mutation
            return "F2F";
        } else {
            return "UNKNOWN";
        }
    }

    private void writeRow(String mutantId, String className, String methodName, 
                         int lineNumber, String mutatorName, String testName, 
                         String transition, String status, int numTestsRun) throws IOException {
        out.write(String.format("%s,%s,%s,%d,%s,%s,%s,%s,%d\n",
                escapeCsv(mutantId),
                escapeCsv(className),
                escapeCsv(methodName),
                lineNumber,
                escapeCsv(mutatorName),
                escapeCsv(testName),
                transition,
                escapeCsv(status),
                numTestsRun));
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape commas and quotes in CSV
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    public void runEnd() {
        try {
            out.close();
            // Clean up temporary baseline results file
            org.pitest.mutationtest.execute.BaselineResultsFileHolder.cleanup();
        } catch (IOException e) {
            throw new RuntimeException("Error closing CSV file", e);
        }
    }
}
