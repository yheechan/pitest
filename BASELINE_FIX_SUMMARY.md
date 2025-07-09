# Fixed Baseline Execution Issue in PIT Research Mode

## Problem
The previous implementation was capturing baseline test results multiple times - once for each mutation. This is inefficient and incorrect because:

1. **Performance**: Running baseline tests for each mutation is unnecessarily slow
2. **Correctness**: The baseline should be consistent across all mutations
3. **Logic**: The baseline represents the original code behavior and should be captured once

## Solution
Modified `MutationTestWorker.java` to capture baseline results once at the beginning of the mutation testing process:

### Key Changes:

1. **Added baseline results field**: 
   ```java
   private Map<String, Boolean> baselineResults = null;
   ```

2. **Modified `run()` method** to capture baseline once:
   ```java
   // In research mode, capture baseline results once for all mutations
   if (this.fullMatrixResearchMode && this.baselineResults == null) {
     captureBaselineResults(testSource);
   }
   ```

3. **Added `captureBaselineResults()` method** that:
   - Runs all tests on original (non-mutated) code
   - Records which tests pass and which fail
   - Stores results for use by all mutations

4. **Updated `doCorrectMutationTestingWithBaseline()` method** to:
   - Use pre-captured baseline results instead of capturing them each time
   - Skip the baseline capture step for each mutation
   - Only run tests on mutated code and compare against stored baseline

## Expected Behavior Now:

### Research Mode Execution Flow:
1. **Capture baseline once**: Run all tests on original code, record pass/fail status
2. **For each mutation**:
   - Apply mutation to code
   - Run all tests on mutated code
   - Compare results with baseline
   - A mutation is KILLED if:
     - Any test that originally passed now fails, OR
     - Any test that originally failed now passes
   - A mutation SURVIVES if all tests have the same result as baseline

### Debug Output Should Show:
```
2:XX:XX AM PIT >> FINE : Capturing baseline results for research mode
2:XX:XX AM PIT >> FINE : Baseline test passed: calc.CalculatorTest.testMultiplication
2:XX:XX AM PIT >> FINE : Baseline test failed: calc.CalculatorTest.testAddition  
2:XX:XX AM PIT >> FINE : Captured baseline: 6 tests, 4 passing, 2 failing

[Then for each mutation:]
2:XX:XX AM PIT >> FINE : Using pre-captured baseline results for mutation X
2:XX:XX AM PIT >> FINE : Applied mutation X, running tests on mutated code
```

## Performance Improvement:
- **Before**: N baseline executions (where N = number of mutations)
- **After**: 1 baseline execution + N mutation executions

For your calculator example with 12 mutations, this should reduce from 24 total test runs to 13 total test runs (1 baseline + 12 mutations).

## Testing:
Run your calculator example again with the updated PIT build. You should see:
1. One baseline capture at the beginning with DEBUG logging
2. Each mutation using "pre-captured baseline results"
3. Correct KILLED/SURVIVED detection based on baseline comparison
4. Faster execution overall
