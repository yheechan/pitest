/*
 * JSON test result writer for mutation testing
 * Provides structured JSON output for better data processing and analysis
 */
package org.pitest.mutationtest.execute;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes test results in JSON format for easier parsing and processing.
 * Supports both baseline test results and mutation test results.
 */
public class JsonTestResultWriter {
    
    /**
     * Write baseline test result in JSON format
     */
    public static void writeBaselineTestResultJson(Path outputDir, int tcID, String testName, 
                                                 String result, double durationMillis, 
                                                 String exceptionType, String exceptionMessage, 
                                                 String stackTrace, String bitSequence) throws IOException {
        Path jsonFile = outputDir.resolve(tcID + "_test_results.json");
        
        Map<String, Object> testResult = new HashMap<>();
        
        // Test information
        Map<String, Object> testInfo = new HashMap<>();
        testInfo.put("test_id", tcID);
        testInfo.put("test_name", testName);
        testInfo.put("result", result);
        testInfo.put("execution_time_ms", durationMillis);
        testResult.put("test_info", testInfo);
        
        // Exception details
        Map<String, String> exceptionDetails = new HashMap<>();
        exceptionDetails.put("type", exceptionType != null ? exceptionType : "None");
        exceptionDetails.put("message", exceptionMessage != null ? exceptionMessage : "None");
        exceptionDetails.put("stack_trace", stackTrace != null ? stackTrace : "None");
        testResult.put("exception", exceptionDetails);
        
        // Coverage information
        Map<String, Object> coverage = new HashMap<>();
        coverage.put("line_coverage_bit_sequence", bitSequence != null ? bitSequence : "");
        coverage.put("bit_sequence_length", bitSequence != null ? bitSequence.length() : 0);
        testResult.put("coverage", coverage);
        
        // Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("format_version", "1.0");
        metadata.put("generated_by", "PIT baseline test execution");
        metadata.put("timestamp", System.currentTimeMillis());
        testResult.put("metadata", metadata);
        
        writeJsonToFile(jsonFile, testResult);
    }
    
    /**
     * Write mutation test results in JSON format
     */
    public static void writeMutationTestResultJson(Path outputDir, long mutationId, 
                                                 String mutantDescription,
                                                 List<DetailedMutationTestResult> testResults) throws IOException {
        Path jsonFile = outputDir.resolve(mutationId + "_mutation_test_results.json");
        
        Map<String, Object> mutationResult = new HashMap<>();
        
        // Mutation information
        Map<String, Object> mutationInfo = new HashMap<>();
        mutationInfo.put("mutation_id", mutationId);
        mutationInfo.put("description", mutantDescription != null ? mutantDescription : "N/A");
        mutationInfo.put("num_tests_executed", testResults != null ? testResults.size() : 0);
        mutationResult.put("mutation_info", mutationInfo);
        
        // Test results array
        List<Map<String, Object>> testResultsList = new ArrayList<>();
        
        if (testResults != null && !testResults.isEmpty()) {
            for (int i = 0; i < testResults.size(); i++) {
                DetailedMutationTestResult testResult = testResults.get(i);
                
                Map<String, Object> testEntry = new HashMap<>();
                
                // Basic test information
                Map<String, Object> testInfo = new HashMap<>();
                testInfo.put("test_index", i);
                testInfo.put("test_name", testResult.getTestName() != null ? testResult.getTestName() : "N/A");
                testInfo.put("result", testResult.isPassed() ? "PASS" : "FAIL");
                testEntry.put("test_info", testInfo);
                
                // Exception details
                Map<String, String> exceptionDetails = new HashMap<>();
                exceptionDetails.put("type", testResult.getExceptionType() != null ? testResult.getExceptionType() : "None");
                exceptionDetails.put("message", testResult.getExceptionMessage() != null ? testResult.getExceptionMessage() : "None");
                exceptionDetails.put("stack_trace", testResult.getStackTrace() != null ? testResult.getStackTrace() : "None");
                testEntry.put("exception", exceptionDetails);
                
                testResultsList.add(testEntry);
            }
        }
        
        mutationResult.put("test_results", testResultsList);
        
        // Summary information
        Map<String, Object> summary = new HashMap<>();
        int totalTests = testResults != null ? testResults.size() : 0;
        int passedTests = 0;
        int failedTests = 0;
        
        if (testResults != null) {
            for (DetailedMutationTestResult result : testResults) {
                if (result.isPassed()) {
                    passedTests++;
                } else {
                    failedTests++;
                }
            }
        }
        
        summary.put("total_tests", totalTests);
        summary.put("passed_tests", passedTests);
        summary.put("failed_tests", failedTests);
        summary.put("pass_rate", totalTests > 0 ? (double) passedTests / totalTests : 0.0);
        mutationResult.put("summary", summary);

        // Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("format_version", "1.0");
        metadata.put("generated_by", "PIT mutation test execution");
        metadata.put("timestamp", System.currentTimeMillis());
        mutationResult.put("metadata", metadata);
        
        writeJsonToFile(jsonFile, mutationResult);
    }
    
    /**
     * Write JSON object to file with proper formatting
     */
    private static void writeJsonToFile(Path jsonFile, Map<String, Object> data) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(jsonFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            
            String jsonString = formatJson(data, 0);
            writer.write(jsonString);
        }
    }
    
    /**
     * Simple JSON formatter for readable output
     * This is a basic implementation - for production use, consider using a proper JSON library
     */
    private static String formatJson(Object obj, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);
        
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof String) {
            sb.append("\"").append(escapeJsonString((String) obj)).append("\"");
        } else if (obj instanceof Double) {
            // Preserve full precision for double values without unnecessary trailing zeros
            double value = (Double) obj;
            // Always show at least some decimal precision to make it clear it's a double
            // Use BigDecimal for precise formatting
            java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value);
            sb.append(bd.toPlainString());
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
        } else if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            sb.append("{\n");
            
            String[] keys = map.keySet().toArray(new String[0]);
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                Object value = map.get(key);
                
                sb.append(indentStr).append("  \"").append(key).append("\": ");
                sb.append(formatJson(value, indent + 1));
                
                if (i < keys.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            
            sb.append(indentStr).append("}");
        } else if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            sb.append("[\n");
            
            for (int i = 0; i < list.size(); i++) {
                sb.append(indentStr).append("  ");
                sb.append(formatJson(list.get(i), indent + 1));
                
                if (i < list.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            
            sb.append(indentStr).append("]");
        } else {
            sb.append("\"").append(escapeJsonString(obj.toString())).append("\"");
        }
        
        return sb.toString();
    }
    
    /**
     * Escape special characters in JSON strings
     */
    private static String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f");
    }
}
