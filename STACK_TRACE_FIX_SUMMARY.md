# Stack Trace Truncation Fix

## Problem Description

Mutation test stack traces were being truncated to only 5 lines with "... N more" appended, while baseline test stack traces were captured in full. This inconsistency made it difficult to compare and analyze test failures between baseline and mutation runs.

## Root Cause

The issue was in the `DetailedMutationTestResult.getStackTraceString()` method in `/pitest/src/main/java/org/pitest/mutationtest/execute/DetailedMutationTestResult.java`. This method was artificially limiting stack traces to 5 elements and adding "... N more" text when the stack was longer.

### Original Code (Problematic)

```java
private static String getStackTraceString(Throwable error) {
    if (error == null) {
        return "No stack trace";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append("\n");
    
    StackTraceElement[] elements = error.getStackTrace();
    // Limit to first 5 stack trace elements to avoid excessive data
    int limit = Math.min(5, elements.length);
    for (int i = 0; i < limit; i++) {
        sb.append("\tat ").append(elements[i].toString()).append("\n");
    }
    
    if (elements.length > limit) {
        sb.append("\t... ").append(elements.length - limit).append(" more\n");
    }
    
    return sb.toString().trim();
}
```

### Comparison with Baseline Test Code

The baseline test stack trace capture methods used the full `printStackTrace()` output:

```java
// From CoverageTestExecutionListener.java
private String getStackTraceString(Throwable throwable) {
    if (throwable == null) {
        return "";
    }
    
    java.io.StringWriter sw = new java.io.StringWriter();
    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
    throwable.printStackTrace(pw);
    pw.close();
    
    // Return full stack trace without truncation
    return sw.toString();
}
```

## Solution

Updated the `DetailedMutationTestResult.getStackTraceString()` method to use the same approach as baseline test collection - capturing the full stack trace using `printStackTrace()`.

### Fixed Code

```java
private static String getStackTraceString(Throwable error) {
    if (error == null) {
        return "No stack trace";
    }
    
    java.io.StringWriter sw = new java.io.StringWriter();
    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
    error.printStackTrace(pw);
    pw.close();
    
    // Return full stack trace without truncation, matching baseline test behavior
    return sw.toString().trim();
}
```

## Impact

1. **Consistency**: Mutation test stack traces now match the format and completeness of baseline test stack traces
2. **Better Analysis**: Full stack traces provide complete context for debugging test failures
3. **No "... N more"**: Eliminates the truncated stack trace problem that was causing confusion
4. **Backward Compatibility**: The change maintains the same interface and only improves the data quality

## Testing

Created a unit test (`DetailedMutationTestResultStackTraceTest`) that verifies:
- Stack traces no longer contain "... more" text
- Stack traces include full exception information
- Stack traces have more than 5 lines for deep call stacks

The test creates a deep nested exception and confirms that the full stack trace is captured without truncation.

## Files Modified

1. `/pitest/src/main/java/org/pitest/mutationtest/execute/DetailedMutationTestResult.java` - Fixed the stack trace truncation
2. `/pitest/src/test/java/org/pitest/mutationtest/execute/DetailedMutationTestResultStackTraceTest.java` - Added test to verify the fix

## Before and After Example

### Before (Truncated)
```
java.lang.RuntimeException: Test exception
	at com.example.Test.method1(Test.java:10)
	at com.example.Test.method2(Test.java:15)
	at com.example.Test.method3(Test.java:20)
	at com.example.Test.method4(Test.java:25)
	at com.example.Test.method5(Test.java:30)
	... 15 more
```

### After (Full Stack Trace)
```
java.lang.RuntimeException: Test exception
	at com.example.Test.method1(Test.java:10)
	at com.example.Test.method2(Test.java:15)
	at com.example.Test.method3(Test.java:20)
	at com.example.Test.method4(Test.java:25)
	at com.example.Test.method5(Test.java:30)
	at com.example.Test.method6(Test.java:35)
	at com.example.Test.method7(Test.java:40)
	... (all remaining stack frames)
```

This fix ensures that mutation test stack traces provide the same level of detail as baseline test stack traces, enabling better comparison and analysis of test failures.
