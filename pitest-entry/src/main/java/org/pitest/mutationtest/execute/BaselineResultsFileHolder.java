/*
 * File-based baseline results holder for sharing between threads
 */
package org.pitest.mutationtest.execute;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * File-based holder for baseline test results to ensure thread-safe sharing
 * between the mutation worker and CSV reporter
 */
public class BaselineResultsFileHolder {
    
    private static Path baselineResultsPath = null;
    
    /**
     * Store baseline results to a temporary file
     */
    public static void storeBaselineResults(Map<String, Boolean> baselineResults) {
        try {
            // Create temporary file
            baselineResultsPath = Files.createTempFile("pitest-baseline-results", ".properties");
            
            // Convert to properties and save
            Properties props = new Properties();
            for (Map.Entry<String, Boolean> entry : baselineResults.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue().toString());
            }
            
            try (FileOutputStream fos = new FileOutputStream(baselineResultsPath.toFile())) {
                props.store(fos, "Baseline test results for PIT research mode");
            }
            
            System.out.println("DEBUG: Stored baseline results to file: " + baselineResultsPath);
            System.out.println("DEBUG: Stored " + baselineResults.size() + " baseline results");
            
        } catch (IOException e) {
            System.err.println("ERROR: Failed to store baseline results: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load baseline results from the temporary file
     */
    public static Map<String, Boolean> loadBaselineResults() {
        Map<String, Boolean> results = new HashMap<>();
        
        if (baselineResultsPath == null || !Files.exists(baselineResultsPath)) {
            System.out.println("DEBUG: No baseline results file found");
            return results;
        }
        
        try (FileInputStream fis = new FileInputStream(baselineResultsPath.toFile())) {
            Properties props = new Properties();
            props.load(fis);
            
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                results.put(key, Boolean.parseBoolean(value));
            }
            
            System.out.println("DEBUG: Loaded " + results.size() + " baseline results from file");
            
        } catch (IOException e) {
            System.err.println("ERROR: Failed to load baseline results: " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Check if baseline results are available
     */
    public static boolean hasBaselineResults() {
        return baselineResultsPath != null && Files.exists(baselineResultsPath);
    }
    
    /**
     * Clean up the temporary file
     */
    public static void cleanup() {
        if (baselineResultsPath != null && Files.exists(baselineResultsPath)) {
            try {
                Files.delete(baselineResultsPath);
            } catch (IOException e) {
                System.err.println("WARNING: Failed to clean up baseline results file: " + e.getMessage());
            }
        }
    }
}
