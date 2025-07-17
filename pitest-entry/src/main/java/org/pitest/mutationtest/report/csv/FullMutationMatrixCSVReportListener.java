/*
 * CSV exporter for detailed mutation-test matrix data
 * For fault localization research on Defects4J
 */
package org.pitest.mutationtest.report.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.execute.DetailedMutationTestResult;
import org.pitest.mutationtest.execute.MutationResultsFileManager;
import org.pitest.mutationtest.execute.MutationResultSummary;
import org.pitest.mutationtest.execute.TestCaseMetadata;
import org.pitest.util.ResultOutputStrategy;

/**
 * Enhanced CSV reporter that outputs detailed mutation-test matrix data
 * Format: one row per mutant with bit sequences for transition results
 * Header: mutant_id,class,method,line,mutator,F2P_transition,P2F_transition,P2P_transition,F2F_transition,exception_type_transition,exception_msg_transition,stacktrace_transition,status,num_tests_run
 */
public class FullMutationMatrixCSVReportListener implements MutationResultListener {

    private final Writer out;
    private final Map<String, TestCaseMetadata> testCaseMetadata;
    private final List<MutationResultSummary> mutationSummaries = new ArrayList<>();
    private List<String> orderedTestNames; // Consistent test order for bit sequences

    public FullMutationMatrixCSVReportListener(final ResultOutputStrategy outputStrategy) throws IOException {
        this(outputStrategy, null, null);
    }

    public FullMutationMatrixCSVReportListener(final ResultOutputStrategy outputStrategy, 
                                              final Map<String, TestCaseMetadata> testCaseMetadata) throws IOException {
        this(outputStrategy, testCaseMetadata, null);
    }

    public FullMutationMatrixCSVReportListener(final ResultOutputStrategy outputStrategy, 
                                              final Map<String, TestCaseMetadata> testCaseMetadata,
                                              final String reportDir) throws IOException {
        this.out = outputStrategy.createWriterForFile("full_mutation_matrix.csv");
        this.testCaseMetadata = testCaseMetadata;
        
        // Initialize ordered test names from metadata for consistent bit sequence order
        this.orderedTestNames = createOrderedTestNames();
        
        // Initialize the mutation results file system
        try {
            String actualReportDir = reportDir != null ? reportDir : System.getProperty("reportDir", "target/pit-reports");
            MutationResultsFileManager.initialize(actualReportDir);
        } catch (IOException e) {
            System.err.println("WARNING: Failed to initialize mutation results file manager: " + e.getMessage());
        }
    }

    /**
     * Create an ordered list of test names based on test case metadata (tcID order).
     * This ensures consistent bit sequence ordering across all mutants.
     */
    private List<String> createOrderedTestNames() {
        if (testCaseMetadata == null || testCaseMetadata.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Sort tests by tcID to ensure consistent ordering
        List<String> orderedNames = new ArrayList<>();
        testCaseMetadata.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e1.getValue().getTcID(), e2.getValue().getTcID()))
            .forEach(entry -> orderedNames.add(entry.getKey()));
        
        return orderedNames;
    }

    @Override
    public void runStart() {
        try {
            // Write CSV header with only result_transition and exception transitions
            out.write("mutant_id,class,method,line,mutator,result_transition,exception_type_transition,exception_msg_transition,stacktrace_transition,status,num_tests_run\n");
        } catch (IOException e) {
            throw new RuntimeException("Error writing CSV header", e);
        }
    }

    @Override
    public void handleMutationResult(ClassMutationResults metaData) {
        try {
            for (MutationResult mutation : metaData.getMutations()) {
                // Use the mutation ID assigned during filtering
                long uniqueMutationId = mutation.getDetails().getMutantId();
                
                String className = mutation.getDetails().getClassName().asJavaName();
                String methodName = mutation.getDetails().getMethod();
                int lineNumber = mutation.getDetails().getLineNumber();
                String mutatorName = mutation.getDetails().getMutator();
                String status = mutation.getStatus().toString();
                int numTestsRun = mutation.getNumberOfTestsRun();
                
                // Create mutation description
                String mutantDescription = String.format("%s.%s:%d [%s]", 
                    className, methodName, lineNumber, mutatorName);

                // Use detailed results if available
                List<DetailedMutationTestResult> detailedResults = mutation.getDetailedResults();
                
                if (detailedResults != null && !detailedResults.isEmpty()) {
                    // Save detailed results to file
                    MutationResultsFileManager.saveMutationTestResults(uniqueMutationId, 
                        mutantDescription, detailedResults);
                    
                    // Write single row with bit sequences for this mutant
                    writeMutantRowWithBitSequences(String.valueOf(uniqueMutationId), className, methodName, lineNumber, 
                            mutatorName, detailedResults, status, numTestsRun);
                    
                    // Add to summary
                    mutationSummaries.add(new MutationResultSummary(uniqueMutationId, 
                        mutantDescription, detailedResults.size(), status));
                } else {
                    // Save empty results to file for legacy mutations
                    MutationResultsFileManager.saveMutationTestResults(uniqueMutationId, 
                        mutantDescription, null);
                    
                    // Fall back to legacy approach using killing/surviving test lists
                    writeMutantRowWithLegacyData(mutation, String.valueOf(uniqueMutationId), className, methodName, 
                                               lineNumber, mutatorName, status, numTestsRun);
                    
                    // Add to summary
                    mutationSummaries.add(new MutationResultSummary(uniqueMutationId, 
                        mutantDescription, numTestsRun, status));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing mutation data", e);
        }
    }

    /**
     * Write a single row for a mutant with bit sequences representing transitions across all tests.
     * Each bit sequence has one bit per test case in the ordered test list.
     */
    private void writeMutantRowWithBitSequences(String mutantId, String className, String methodName, 
                                               int lineNumber, String mutatorName, 
                                               List<DetailedMutationTestResult> detailedResults,
                                               String status, int numTestsRun) throws IOException {
        StringBuilder row = new StringBuilder();
        
        // Basic mutant information
        row.append(escapeCsv(mutantId)).append(",");
        row.append(escapeCsv(className)).append(",");
        row.append(escapeCsv(methodName)).append(",");
        row.append(lineNumber).append(",");
        row.append(escapeCsv(mutatorName)).append(",");

        // Create bit sequences for transitions
        String resultTransitionBitSequence = createResultTransitionBitSequence(detailedResults);
        String exceptionTypeBitSequence = createExceptionTransitionBitSequence(detailedResults, "TYPE");
        String exceptionMsgBitSequence = createExceptionTransitionBitSequence(detailedResults, "MESSAGE");
        String stacktraceBitSequence = createExceptionTransitionBitSequence(detailedResults, "STACKTRACE");

        // Add bit sequences to row
        row.append(resultTransitionBitSequence).append(",");
        row.append(exceptionTypeBitSequence).append(",");
        row.append(exceptionMsgBitSequence).append(",");
        row.append(stacktraceBitSequence).append(",");

        // Status and test count
        row.append(escapeCsv(status)).append(",");
        row.append(numTestsRun);
        row.append("\n");
        out.write(row.toString());
    }

    /**
     * Create a bit sequence where 1 means the test result changed (F2P or P2F), 0 means it did not (P2P or F2F).
     */
    private String createResultTransitionBitSequence(List<DetailedMutationTestResult> detailedResults) {
        if (orderedTestNames.isEmpty()) {
            return "";
        }
        Map<String, DetailedMutationTestResult> resultMap = new HashMap<>();
        for (DetailedMutationTestResult result : detailedResults) {
            resultMap.put(result.getTestName(), result);
        }
        StringBuilder bitSequence = new StringBuilder();
        for (String testName : orderedTestNames) {
            DetailedMutationTestResult testResult = resultMap.get(testName);
            if (testResult == null) {
                bitSequence.append("0");
            } else {
                String transition = computeResultTransition(testResult);
                bitSequence.append((transition.equals("F2P") || transition.equals("P2F")) ? "1" : "0");
            }
        }
        return bitSequence.toString();
    }

    /**
     * Create a bit sequence for exception transitions across all ordered tests.
     * @param detailedResults the test results for this mutant
     * @param exceptionType "TYPE", "MESSAGE", or "STACKTRACE"
     * @return bit sequence string where 1 = exception change occurred, 0 = no change
     */
    private String createExceptionTransitionBitSequence(List<DetailedMutationTestResult> detailedResults, String exceptionType) {
        if (orderedTestNames.isEmpty()) {
            return ""; // No test metadata available
        }
        
        // Create a map of test results for quick lookup
        Map<String, DetailedMutationTestResult> resultMap = new HashMap<>();
        for (DetailedMutationTestResult result : detailedResults) {
            resultMap.put(result.getTestName(), result);
        }
        
        StringBuilder bitSequence = new StringBuilder();
        
        for (String testName : orderedTestNames) {
            DetailedMutationTestResult testResult = resultMap.get(testName);
            
            if (testResult == null) {
                // Test was not executed on this mutant, assume no change
                bitSequence.append("0");
            } else {
                boolean hasChange = false;
                switch (exceptionType) {
                    case "TYPE":
                        hasChange = computeExceptionTypeTransition(testResult) == 1;
                        break;
                    case "MESSAGE":
                        hasChange = computeExceptionMsgTransition(testResult) == 1;
                        break;
                    case "STACKTRACE":
                        hasChange = computeStacktraceTransition(testResult) == 1;
                        break;
                }
                bitSequence.append(hasChange ? "1" : "0");
            }
        }
        
        return bitSequence.toString();
    }

    private String computeResultTransition(DetailedMutationTestResult testResult) {
        if (this.testCaseMetadata == null || this.testCaseMetadata.isEmpty()) {
            // No baseline available, return based on current result
            return testResult.isPassed() ? "P2P" : "F2F";
        }
        
        TestCaseMetadata baseline = this.testCaseMetadata.get(testResult.getTestName());
        if (baseline == null) {
            // No baseline for this test, assume no change
            return testResult.isPassed() ? "P2P" : "F2F";
        }
        
        boolean baselinePassed = baseline.isBaselinePassed();
        boolean currentPassed = testResult.isPassed();
        
        if (baselinePassed && !currentPassed) {
            return "P2F"; // Passed to Failed
        } else if (!baselinePassed && currentPassed) {
            return "F2P"; // Failed to Passed
        } else if (!baselinePassed && !currentPassed) {
            return "F2F"; // Failed to Failed
        } else {
            return "P2P"; // Passed to Passed
        }
    }

    private int computeExceptionTypeTransition(DetailedMutationTestResult testResult) {
        if (this.testCaseMetadata == null || this.testCaseMetadata.isEmpty()) {
            return 0; // No baseline, assume no change
        }
        
        TestCaseMetadata baseline = this.testCaseMetadata.get(testResult.getTestName());
        if (baseline == null) {
            return 0; // No baseline for this test
        }
        
        return testResult.hasExceptionTypeChanged(baseline) ? 1 : 0;
    }

    private int computeExceptionMsgTransition(DetailedMutationTestResult testResult) {
        if (this.testCaseMetadata == null || this.testCaseMetadata.isEmpty()) {
            return 0; // No baseline, assume no change
        }
        
        TestCaseMetadata baseline = this.testCaseMetadata.get(testResult.getTestName());
        if (baseline == null) {
            return 0; // No baseline for this test
        }
        
        return testResult.hasExceptionMessageChanged(baseline) ? 1 : 0;
    }

    private int computeStacktraceTransition(DetailedMutationTestResult testResult) {
        if (this.testCaseMetadata == null || this.testCaseMetadata.isEmpty()) {
            return 0; // No baseline, assume no change
        }
        
        TestCaseMetadata baseline = this.testCaseMetadata.get(testResult.getTestName());
        if (baseline == null) {
            return 0; // No baseline for this test
        }
        
        return testResult.hasStackTraceChanged(baseline) ? 1 : 0;
    }

    /**
     * Write a single row for a mutant using legacy data (killing/surviving test lists).
     * Creates bit sequences based on available legacy information.
     */
    private void writeMutantRowWithLegacyData(MutationResult mutation, String mutantId, String className, 
                                            String methodName, int lineNumber, String mutatorName, 
                                            String status, int numTestsRun) throws IOException {
        StringBuilder row = new StringBuilder();
        
        // Basic mutant information
        row.append(escapeCsv(mutantId)).append(",");
        row.append(escapeCsv(className)).append(",");
        row.append(escapeCsv(methodName)).append(",");
        row.append(lineNumber).append(",");
        row.append(escapeCsv(mutatorName)).append(",");
        
        // Create bit sequences from legacy data
        List<String> killingTests = mutation.getKillingTests();
        List<String> survivingTests = mutation.getSucceedingTests();
        
        String f2pBitSequence = createLegacyTransitionBitSequence(killingTests, survivingTests, "F2P");
        String p2fBitSequence = createLegacyTransitionBitSequence(killingTests, survivingTests, "P2F");
        String p2pBitSequence = createLegacyTransitionBitSequence(killingTests, survivingTests, "P2P");
        String f2fBitSequence = createLegacyTransitionBitSequence(killingTests, survivingTests, "F2F");
        
        // Exception transitions are not available in legacy data, use empty sequences
        String emptyBitSequence = createEmptyBitSequence();
        
        // Add bit sequences to row
        row.append(f2pBitSequence).append(",");
        row.append(p2fBitSequence).append(",");
        row.append(p2pBitSequence).append(",");
        row.append(f2fBitSequence).append(",");
        row.append(emptyBitSequence).append(","); // exception_type_transition
        row.append(emptyBitSequence).append(","); // exception_msg_transition
        row.append(emptyBitSequence).append(","); // stacktrace_transition
        
        // Status and test count
        row.append(escapeCsv(status)).append(",");
        row.append(numTestsRun);
        
        row.append("\n");
        out.write(row.toString());
    }

    /**
     * Create a bit sequence for legacy transitions based on killing/surviving test lists.
     */
    private String createLegacyTransitionBitSequence(List<String> killingTests, List<String> survivingTests, String transitionType) {
        if (orderedTestNames.isEmpty()) {
            return ""; // No test metadata available
        }
        
        StringBuilder bitSequence = new StringBuilder();
        
        for (String testName : orderedTestNames) {
            String legacyTransition = getLegacyResultTransition(testName, killingTests, survivingTests);
            bitSequence.append(legacyTransition.equals(transitionType) ? "1" : "0");
        }
        
        return bitSequence.toString();
    }

    /**
     * Create an empty bit sequence (all zeros) for cases where data is not available.
     */
    private String createEmptyBitSequence() {
        if (orderedTestNames.isEmpty()) {
            return "";
        }
        
        StringBuilder bitSequence = new StringBuilder();
        for (int i = 0; i < orderedTestNames.size(); i++) {
            bitSequence.append("0");
        }
        return bitSequence.toString();
    }

    private String getLegacyResultTransition(String testName, List<String> killingTests, List<String> survivingTests) {
        // Use test case metadata for baseline information if available
        if (this.testCaseMetadata == null || this.testCaseMetadata.isEmpty()) {
            // Fallback to simple killed/survived logic
            if (killingTests.contains(testName)) {
                return "P2F"; // Assume baseline passed, mutation failed
            } else if (survivingTests.contains(testName)) {
                return "P2P"; // Assume baseline passed, mutation passed
            } else {
                return "P2P"; // Default assumption
            }
        }

        TestCaseMetadata metadata = this.testCaseMetadata.get(testName);
        if (metadata == null) {
            // No baseline data, make reasonable assumption
            return killingTests.contains(testName) ? "P2F" : "P2P";
        }

        boolean baselinePassed = metadata.isBaselinePassed();
        boolean currentPassed = survivingTests.contains(testName);
        boolean currentFailed = killingTests.contains(testName);

        // Check if this is a NON_VIABLE mutation (no tests were executed)
        boolean noTestsRun = !currentPassed && !currentFailed;
        
        if (noTestsRun) {
            // For NON_VIABLE mutations, assume the test would have the same result as baseline
            return baselinePassed ? "P2P" : "F2F";
        }

        if (baselinePassed && currentFailed) {
            return "P2F"; // Test passed in baseline but failed on mutation
        } else if (baselinePassed && currentPassed) {
            return "P2P"; // Test passed in baseline and passed on mutation
        } else if (!baselinePassed && currentFailed) {
            return "F2P"; // Test failed in baseline but "killed" the mutation (test now passes)
        } else if (!baselinePassed && currentPassed) {
            return "F2F"; // Test failed in baseline and survived the mutation
        } else {
            return "P2P"; // Default
        }
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
        } catch (IOException e) {
            throw new RuntimeException("Error closing CSV file", e);
        }
        
        // Create mutation summary CSV
        try {
            MutationResultsFileManager.createMutationSummaryCSV(mutationSummaries);
            System.out.println("INFO: Created mutation summary CSV with " + mutationSummaries.size() + " mutations");
        } catch (Exception e) {
            System.err.println("WARNING: Failed to create mutation summary CSV: " + e.getMessage());
        }
    }
}
