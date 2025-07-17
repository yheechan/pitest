/*
 * File manager for storing detailed mutation test results
 */
package org.pitest.mutationtest.execute;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages saving detailed mutation test results to individual files
 * for each mutation in the format: <reportDir>/mutationResults/{mutationId}_mutation_test_results.json
 */
public class MutationResultsFileManager {
    
    private static final AtomicLong MUTATION_ID_COUNTER = new AtomicLong(0);
    private static Path mutationResultsDir = null;
    
    /**
     * Initialize the mutation results directory
     */
    public static synchronized void initialize(String reportDir) throws IOException {
        if (reportDir == null || reportDir.isEmpty()) {
            throw new IllegalArgumentException("Report directory cannot be null or empty");
        }
        
        mutationResultsDir = Paths.get(reportDir, "mutationResults");
        Files.createDirectories(mutationResultsDir);
        
        // Reset counter for new run
        MUTATION_ID_COUNTER.set(0);
    }
    
    /**
     * Generate a unique mutation ID
     */
    public static long generateMutationId() {
        return MUTATION_ID_COUNTER.incrementAndGet();
    }
    
    /**
     * Save detailed test results for a specific mutation
     */
    public static synchronized void saveMutationTestResults(long mutationId, 
                                                          String mutantDescription,
                                                          List<DetailedMutationTestResult> testResults) {
        if (mutationResultsDir == null) {
            System.err.println("ERROR: Mutation results directory not initialized");
            return;
        }
        
        try {
            // Write JSON format for easier parsing
            JsonTestResultWriter.writeMutationTestResultJson(
                mutationResultsDir, mutationId, mutantDescription, testResults);
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to save mutation test results for mutation " + mutationId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    /**
     * Create a summary CSV file with all mutation results
     */
    public static synchronized void createMutationSummaryCSV(List<MutationResultSummary> summaries) {
        if (mutationResultsDir == null) {
            System.err.println("ERROR: Mutation results directory not initialized");
            return;
        }
        
        try {
            Path summaryFile = mutationResultsDir.resolve("mutation_summary.csv");
            
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(summaryFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                
                // Write CSV header
                writer.println("mutation_id,mutant_description,num_tests_executed,overall_status");
                
                // Write summary data
                for (MutationResultSummary summary : summaries) {
                    writer.printf("%d,%s,%d,%s%n",
                        summary.getMutationId(),
                        escapeCsvValue(summary.getMutantDescription()),
                        summary.getNumTestsExecuted(),
                        escapeCsvValue(summary.getOverallStatus()));
                }
            }
            
            // ...existing code...
            
        } catch (IOException e) {
            System.err.println("ERROR: Failed to create mutation summary CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Escape CSV values to handle commas, quotes, and newlines
     */
    private static String escapeCsvValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        
        // If value contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
    
    /**
     * Get the current mutation results directory
     */
    public static Path getMutationResultsDir() {
        return mutationResultsDir;
    }
    
    /**
     * Check if the mutation results system is initialized
     */
    public static boolean isInitialized() {
        return mutationResultsDir != null;
    }
    
    /**
     * Clean up any resources (for testing)
     */
    public static synchronized void cleanup() {
        mutationResultsDir = null;
        MUTATION_ID_COUNTER.set(0);
    }
}
