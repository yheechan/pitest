/*
 * CSV exporter for detailed mutation-test matrix data
 * For fault localization research on Defects4J
 */
package org.pitest.mutationtest.report.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
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
 * Format: mutant_id,class,method,line,mutator,test_name,transition,status
 */
public class FullMutationMatrixCSVReportListener implements MutationResultListener {

    private final Writer out;
    private final Map<String, TestCaseMetadata> testCaseMetadata;
    private final List<MutationResultSummary> mutationSummaries = new ArrayList<>();

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
        
        // Initialize the mutation results file system
        try {
            String actualReportDir = reportDir != null ? reportDir : System.getProperty("reportDir", "target/pit-reports");
            MutationResultsFileManager.initialize(actualReportDir);
        } catch (IOException e) {
            System.err.println("WARNING: Failed to initialize mutation results file manager: " + e.getMessage());
        }
    }

    @Override
    public void runStart() {
        try {
            // Write CSV header with new detailed columns
            out.write("mutant_id,class,method,line,mutator,test_name,result_transition,exception_type_transition,exception_msg_transition,stacktrace_transition,status,num_tests_run\n");
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
                    
                    // Use the new detailed results for enhanced reporting
                    for (DetailedMutationTestResult testResult : detailedResults) {
                        writeDetailedRow(String.valueOf(uniqueMutationId), className, methodName, lineNumber, 
                                mutatorName, testResult, status, numTestsRun);
                    }
                    
                    // Add to summary
                    mutationSummaries.add(new MutationResultSummary(uniqueMutationId, 
                        mutantDescription, detailedResults.size(), status));
                } else {
                    // Save empty results to file for legacy mutations
                    MutationResultsFileManager.saveMutationTestResults(uniqueMutationId, 
                        mutantDescription, null);
                    
                    // Fall back to legacy approach using killing/surviving test lists
                    handleLegacyMutationResult(mutation, String.valueOf(uniqueMutationId), className, methodName, 
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

    private void writeDetailedRow(String mutantId, String className, String methodName, 
                                 int lineNumber, String mutatorName, DetailedMutationTestResult testResult,
                                 String status, int numTestsRun) throws IOException {
        StringBuilder row = new StringBuilder();
        
        // mutant_id,class,method,line,mutator,test_name
        row.append(escapeCsv(mutantId)).append(",");
        row.append(escapeCsv(className)).append(",");
        row.append(escapeCsv(methodName)).append(",");
        row.append(lineNumber).append(",");
        row.append(escapeCsv(mutatorName)).append(",");
        row.append(escapeCsv(testResult.getTestName())).append(",");
        
        // result_transition (P2F, F2P, F2F, P2P)
        String resultTransition = computeResultTransition(testResult);
        row.append(resultTransition).append(",");
        
        // exception_type_transition (1 if changed, 0 otherwise)
        int exceptionTypeTransition = computeExceptionTypeTransition(testResult);
        row.append(exceptionTypeTransition).append(",");
        
        // exception_msg_transition (1 if changed, 0 otherwise)
        int exceptionMsgTransition = computeExceptionMsgTransition(testResult);
        row.append(exceptionMsgTransition).append(",");
        
        // stacktrace_transition (1 if changed, 0 otherwise)
        int stacktraceTransition = computeStacktraceTransition(testResult);
        row.append(stacktraceTransition).append(",");
        
        // status,num_tests_run
        row.append(escapeCsv(status)).append(",");
        row.append(numTestsRun);
        
        row.append("\n");
        out.write(row.toString());
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

    private void handleLegacyMutationResult(MutationResult mutation, String mutantId, String className, 
                                          String methodName, int lineNumber, String mutatorName, 
                                          String status, int numTestsRun) throws IOException {
        // Legacy logic for backward compatibility
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
                writeLegacyRow(mutantId, className, methodName, lineNumber, 
                        mutatorName, testName, killingTests, survivingTests, status, numTestsRun);
            }
        } else {
            // No tests executed - output one row with empty test name
            writeLegacyRow(mutantId, className, methodName, lineNumber, 
                    mutatorName, "NO_COVERAGE", killingTests, survivingTests, status, numTestsRun);
        }
    }

    private void writeLegacyRow(String mutantId, String className, String methodName, 
                               int lineNumber, String mutatorName, String testName,
                               List<String> killingTests, List<String> survivingTests,
                               String status, int numTestsRun) throws IOException {
        StringBuilder row = new StringBuilder();
        
        // mutant_id,class,method,line,mutator,test_name
        row.append(escapeCsv(mutantId)).append(",");
        row.append(escapeCsv(className)).append(",");
        row.append(escapeCsv(methodName)).append(",");
        row.append(lineNumber).append(",");
        row.append(escapeCsv(mutatorName)).append(",");
        row.append(escapeCsv(testName)).append(",");
        
        // result_transition (based on legacy logic)
        String resultTransition = getLegacyResultTransition(testName, killingTests, survivingTests);
        row.append(resultTransition).append(",");
        
        // exception transitions (set to 0 for legacy data)
        row.append("0,"); // exception_type_transition
        row.append("0,"); // exception_msg_transition  
        row.append("0,"); // stacktrace_transition
        
        // status,num_tests_run
        row.append(escapeCsv(status)).append(",");
        row.append(numTestsRun);
        
        row.append("\n");
        out.write(row.toString());
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
