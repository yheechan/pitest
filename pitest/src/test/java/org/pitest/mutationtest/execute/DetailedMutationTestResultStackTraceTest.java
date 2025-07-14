package org.pitest.mutationtest.execute;

import org.junit.Test;
import static org.junit.Assert.*;

public class DetailedMutationTestResultStackTraceTest {

    /**
     * Test that ensures stack traces are not truncated and don't contain "... more" text
     */
    @Test
    public void testStackTraceNotTruncated() {
        // Create a nested exception with a deep stack trace
        Exception deepException = createDeepStackTraceException(10);
        
        // Create a DetailedMutationTestResult using the failed() factory method
        DetailedMutationTestResult result = DetailedMutationTestResult.failed("test.name", deepException, 100);
        
        String stackTrace = result.getStackTrace();
        
        // Verify stack trace is not truncated
        assertFalse("Stack trace should not contain '... more' text", stackTrace.contains("... more"));
        assertFalse("Stack trace should not contain '... N more' pattern", stackTrace.matches(".*\\.\\.\\. \\d+ more.*"));
        
        // Verify stack trace contains the full exception information
        assertTrue("Stack trace should contain exception class name", 
                   stackTrace.contains("RuntimeException"));
        assertTrue("Stack trace should contain method names from the call stack", 
                   stackTrace.contains("createDeepStackTraceException"));
        
        // Verify it's a complete stack trace (should have many lines)
        String[] lines = stackTrace.split("\n");
        assertTrue("Stack trace should have more than 5 lines", lines.length > 5);
        
        System.out.println("Test passed - stack trace has " + lines.length + " lines");
        System.out.println("Sample stack trace (first 200 chars): " + 
                          stackTrace.substring(0, Math.min(200, stackTrace.length())) + "...");
    }
    
    /**
     * Helper method to create an exception with a deep stack trace
     */
    private Exception createDeepStackTraceException(int depth) {
        if (depth <= 0) {
            return new RuntimeException("Deep stack trace test exception");
        }
        try {
            return createDeepStackTraceException(depth - 1);
        } catch (Exception e) {
            throw new RuntimeException("Nested exception at depth " + depth, e);
        }
    }
}
