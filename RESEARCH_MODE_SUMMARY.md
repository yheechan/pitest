# PIT Research Mode Implementation Summary

## Overview
This implementation adds a research mode to PIT (mutation testing tool) to support fault localization research on Defects4J. The research mode provides baseline-aware mutation detection and outputs a full mutation-test-case matrix.

## Key Features Added

### 1. Command Line Option
- Added `--fullMatrixResearchMode` flag to enable research mode
- Located in: `pitest-command-line/src/main/java/org/pitest/mutationtest/commandline/OptionsParser.java`
- Automatically enables the FULL_MATRIX_RESEARCH feature when set

### 2. Configuration Support
- Added `FULL_MATRIX_RESEARCH_MODE` to `ConfigOption.java`
- Added support in `ReportOptions.java` to track research mode state
- Full wiring through the configuration pipeline

### 3. Test Selection Logic
- Modified `MutationTestWorker.java` to use all tests in research mode (not just covering tests)
- Added `getAllTests()` method to `TimeOutDecoratedTestSource.java`
- Modified test class selection in `MutationTestBuilder.java` and `MutationTestUnit.java`

### 4. Baseline-Aware Mutation Detection
- Implemented `BaselineAwareMutationTestListener.java` for correct KILLED/SURVIVED logic
- A mutation is KILLED if:
  - Any originally passing test fails on the mutant, OR
  - Any originally failing test passes on the mutant
- Otherwise, the mutation SURVIVES

### 5. Full Matrix Reporting
- Created `FullMutationMatrixListener.java` for detailed per-test result tracking
- Created `FullMutationMatrixCSVReportListener.java` for CSV output
- Created `FullMutationMatrixCSVReportFactory.java` for service registration
- Outputs (mutant, test, result) tuples for all combinations

### 6. Worker Process Changes
- Modified `WorkerFactory.java` to pass research mode flag to workers
- Modified `MutationTestWorker.java` to run all tests on original code, then on mutant
- Implemented comparison logic to determine correct mutation status

## Modified Files

### Core Logic
- `pitest/src/main/java/org/pitest/mutationtest/execute/MutationTestWorker.java`
- `pitest/src/main/java/org/pitest/mutationtest/execute/TimeOutDecoratedTestSource.java`
- `pitest-entry/src/main/java/org/pitest/coverage/execute/DefaultCoverageGenerator.java`

### Configuration & Options
- `pitest-command-line/src/main/java/org/pitest/mutationtest/commandline/OptionsParser.java`
- `pitest-entry/src/main/java/org/pitest/mutationtest/config/ConfigOption.java`
- `pitest-entry/src/main/java/org/pitest/mutationtest/config/ReportOptions.java`

### Build & Test Setup
- `pitest-entry/src/main/java/org/pitest/mutationtest/build/WorkerFactory.java`
- `pitest-entry/src/main/java/org/pitest/mutationtest/build/MutationTestUnit.java`
- `pitest-entry/src/main/java/org/pitest/mutationtest/build/MutationTestBuilder.java`

### New Files
- `pitest/src/main/java/org/pitest/mutationtest/execute/FullMutationMatrixListener.java`
- `pitest/src/main/java/org/pitest/mutationtest/execute/BaselineAwareMutationTestListener.java`
- `pitest-entry/src/main/java/org/pitest/mutationtest/report/csv/FullMutationMatrixCSVReportListener.java`
- `pitest-entry/src/main/java/org/pitest/mutationtest/report/csv/FullMutationMatrixCSVReportFactory.java`

### Service Registration
- `pitest-entry/src/main/resources/META-INF/services/org.pitest.mutationtest.MutationResultListenerFactory`

### Tests
- `pitest/src/test/java/org/pitest/mutationtest/execute/MutationTestWorkerTest.java`

## Usage

To use the research mode:

```bash
java -jar pitest-command-line.jar --fullMatrixResearchMode \
  --targetClasses=com.example.* \
  --targetTests=com.example.* \
  --sourceDirs=src/main/java \
  --classPath=target/classes:target/test-classes
```

This will:
1. Run all tests against the original code to establish baseline
2. For each mutant, run all tests (not just covering tests)
3. Compare results against baseline to determine KILLED/SURVIVED status
4. Output full mutation-test matrix in CSV format

## Key Differences from Standard PIT

### Standard PIT Behavior
- Only runs covering tests for each mutant
- Stops after first killing test is found
- Uses simple pass/fail logic for mutation detection

### Research Mode Behavior
- Runs ALL tests for each mutant
- Runs all tests to completion (no early stopping)
- Uses baseline-aware comparison for mutation detection
- Outputs detailed (mutant, test, result) matrix
- Supports fault localization research needs

## Implementation Notes

1. **Backward Compatibility**: Research mode is opt-in and does not affect standard PIT operation
2. **Performance**: Research mode will be slower due to running all tests for all mutants
3. **Baseline Handling**: The implementation correctly handles cases where tests fail on the original code
4. **Test Coverage**: Added comprehensive tests for the new research mode functionality

## Testing

The implementation includes tests that verify:
- Research mode flag is properly handled
- Baseline-aware mutation detection works correctly
- Standard mode continues to work as before
- All tests pass successfully

The research mode has been successfully tested and is ready for use in fault localization research on Defects4J.
