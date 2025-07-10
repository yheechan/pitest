# Original (Baseline) Bytecode Saving

The enhanced `MutationTestWorker` now automatically saves the original (baseline) bytecode of classes before they are mutated. This allows for easy comparison between the original and mutated versions.

## How It Works

### Automatic Original Class Saving
- When the first mutation for a class is processed, the system automatically saves the original bytecode
- Original classes are saved only once per class to avoid duplication
- Uses multiple class loader strategies to find the original bytecode

### File Structure
```
reportDir/
  mutants/
    com/example/Calculator/
      add/
        ORIGINAL_Calculator.class           # Original (baseline) bytecode
        ORIGINAL_Calculator.info            # Metadata for original class
        Line_42_Index_1_MATH_MUTATOR.class  # Mutated bytecode
        Line_42_Index_1_MATH_MUTATOR.info   # Metadata for mutation
        Line_45_Index_2_MATH_MUTATOR.class  # Another mutation
        Line_45_Index_2_MATH_MUTATOR.info   # Metadata for another mutation
```

## Features

### 1. Original Bytecode Preservation
- **File naming**: `ORIGINAL_ClassName.class`
- **Metadata file**: `ORIGINAL_ClassName.info`
- **One-time saving**: Each class's original is saved only once, regardless of how many mutations are created

### 2. Enhanced Metadata
The mutation metadata files now include:
- Reference to the original class file
- Comparison commands for easy analysis
- Instructions for decompiling both original and mutated versions

### 3. Multiple Class Loader Strategy
The system tries to find original bytecode using:
1. **Primary class loader** (from PIT's loader)
2. **System class loader**
3. **Thread context class loader**

This ensures maximum compatibility with different project setups.

## Usage Examples

### Compare Original vs Mutated (Bytecode Level)
```bash
# Compare bytecode disassembly
diff <(javap -c ORIGINAL_Calculator.class) <(javap -c Line_42_Index_1_MATH_MUTATOR.class)

# Compare with more detail
diff <(javap -c -p -v ORIGINAL_Calculator.class) <(javap -c -p -v Line_42_Index_1_MATH_MUTATOR.class)
```

### Decompile Both Versions
```bash
# Using CFR decompiler
java -jar cfr.jar ORIGINAL_Calculator.class > original.java
java -jar cfr.jar Line_42_Index_1_MATH_MUTATOR.class > mutated.java
diff original.java mutated.java

# Using javap (shows bytecode, not Java source)
javap -c ORIGINAL_Calculator.class
javap -c Line_42_Index_1_MATH_MUTATOR.class
```

### Analyze Multiple Mutations
```bash
# See all mutations for a class compared to original
for mutant in Line_*.class; do
  echo "=== Comparing $mutant with original ==="
  diff <(javap -c ORIGINAL_Calculator.class) <(javap -c "$mutant")
  echo
done
```

## Metadata Information

### Original Class Metadata (`ORIGINAL_ClassName.info`)
```
Original Class: com.example.Calculator
Bytecode Size: 2048 bytes
Saved From: com/example/Calculator.class
Purpose: Baseline for mutation comparison

This is the original (unmutated) version of the class.
Compare with mutated versions to see the exact changes made.

To decompile this original bytecode:
  javap -c -p ORIGINAL_Calculator.class
  Or use any Java decompiler like CFR, Fernflower, or JD-GUI

To compare original vs mutated (example):
  diff <(javap -c ORIGINAL_Calculator.class) <(javap -c Line_X_Index_Y_Mutator.class)
```

### Enhanced Mutation Metadata
```
Mutation ID: [mutation details]
Class: com.example.Calculator
Method: add
Line Number: 42
Mutation Index: 1
Mutator: MATH_MUTATOR
Description: replaced + with -
Bytecode Size: 2051 bytes
Bytecode File: Line_42_Index_1_MATH_MUTATOR.class
Original Class File: ORIGINAL_Calculator.class

To compare with original:
  diff <(javap -c Line_42_Index_1_MATH_MUTATOR.class) <(javap -c ORIGINAL_Calculator.class)

To decompile this bytecode to Java source, you can use:
  javap -c -p Line_42_Index_1_MATH_MUTATOR.class
  Or use any Java decompiler like CFR, Fernflower, or JD-GUI
```

## Benefits

### 1. **Easy Comparison**
- Side-by-side analysis of original vs mutated code
- Understand exactly what each mutation operator does
- Verify mutation correctness

### 2. **Research & Analysis**
- Study mutation operator behavior
- Analyze bytecode-level changes
- Debug mutation issues

### 3. **Educational Value**
- Learn how mutations affect bytecode
- Understand Java compilation and bytecode structure
- Study mutation testing concepts

### 4. **Debugging Support**
- Verify mutations are applied correctly
- Identify potential issues with custom mutation operators
- Analyze unexpected mutation behavior

## Configuration

This functionality is automatically enabled when:
- `fullMatrixResearchMode = true`
- `reportDir` is specified and accessible

No additional configuration is required. The system will automatically:
1. Create the necessary directory structure
2. Save original bytecode on first mutation of each class
3. Generate comprehensive metadata files
4. Provide usage instructions in metadata

## Implementation Details

### Memory Efficiency
- Original bytecode is loaded only when needed
- Each class's original is saved only once (tracked via `savedOriginalClasses` set)
- Graceful handling of missing or inaccessible classes

### Error Handling
- Multiple fallback strategies for loading original bytecode
- Detailed logging for debugging
- Continues processing even if original bytecode cannot be found

### Thread Safety
- Uses thread-safe collections for tracking saved classes
- No shared mutable state between mutation processing threads

This enhancement makes PIT's research mode significantly more powerful for analyzing the exact changes made by mutations and understanding their impact on the codebase.
