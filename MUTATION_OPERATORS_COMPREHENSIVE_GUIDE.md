# PIT Mutation Operators Comprehensive Guide

## Overview

This document explains the comprehensive mutation operators available in PIT (Pitest), including the standard operators and the experimental comprehensive operator families. It also documents the fix implemented to ensure all sub-operators within each family are properly applied during mutation testing.

## Mutation Operator Families

### Standard Operators
These are the traditional PIT mutators:
- **DEFAULTS**: Basic set including conditionals, increments, math, negation, etc.
- **STRONGER**: Enhanced set with additional operators
- **ALL**: All available standard operators

### Experimental Comprehensive Operators
These implement comprehensive mutation operator families based on mutation testing literature:

#### 1. Arithmetic Operator Replacement (AOR)
Replaces binary arithmetic operators with other arithmetic operators:
- **AOR_1**: `+` ↔ `-` (addition/subtraction swap)
- **AOR_2**: `+` → `*` (addition to multiplication)
- **AOR_3**: `+` → `/` (addition to division)
- **AOR_4**: `+` → `%` (addition to modulo)

Similar replacements apply to `-`, `*`, `/`, `%` operations.

#### 2. Arithmetic Operator Deletion (AOD)
Deletes one operand from binary arithmetic operations:
- **AOD_1**: `a + b` → `a` (delete second operand)
- **AOD_2**: `a + b` → `b` (delete first operand)

#### 3. Unary Operator Insertion (UOI)
Inserts unary operators before variables:
- **UOI_1**: `var` → `-var` (insert unary minus)
- **UOI_2**: `var` → `+var` (insert unary plus)
- **UOI_3**: `var` → `~var` (insert bitwise complement)
- **UOI_4**: `var` → `!var` (insert logical negation, for boolean)

#### 4. Bitwise Operator (OBBN)
Replaces bitwise operators:
- **OBBN_1**: `&` ↔ `|` (bitwise AND/OR swap)
- **OBBN_2**: `&` ↔ `^` (bitwise AND/XOR swap)
- **OBBN_3**: `|` ↔ `^` (bitwise OR/XOR swap)

#### 5. Absolute Value (ABS)
- **ABS**: Negates numerical values and expressions

## The Sub-Operator Registration Issue

### Problem Description
Previously, when using `--mutators ALL`, only one sub-operator from each family would be applied instead of all sub-operators. For example:
- Only `AOR_1` would be applied instead of `AOR_1`, `AOR_2`, `AOR_3`, `AOR_4`
- Only `UOI_1` would be applied instead of `UOI_1`, `UOI_2`, `UOI_3`, `UOI_4`

### Root Cause
The issue occurred because:
1. Experimental mutators are implemented as Java enums
2. The service loader mechanism registered the enum class but not individual enum values
3. When PIT resolved mutator names, it only found one representative per family

### Solution Implementation
A comprehensive fix was implemented by creating `ComprehensiveMutatorGroups.java`:

```java
public class ComprehensiveMutatorGroups implements MutatorGroup {
    @Override
    public void register(Map<String, List<MethodMutatorFactory>> mutators) {
        // Register individual AOR sub-mutators
        mutators.put("AOR_1", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_1));
        mutators.put("AOR_2", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_2));
        mutators.put("AOR_3", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_3));
        mutators.put("AOR_4", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_4));
        
        // Similar registration for AOD, UOI, OBBN, ABS...
        
        // Register family groups
        mutators.put("AOR_ALL", Arrays.asList(
            ArithmeticOperatorReplacementMutator.AOR_1,
            ArithmeticOperatorReplacementMutator.AOR_2,
            ArithmeticOperatorReplacementMutator.AOR_3,
            ArithmeticOperatorReplacementMutator.AOR_4
        ));
    }
}
```

## Usage Examples

### Using Individual Sub-Operators
```bash
# Apply only specific AOR sub-operators
--mutators AOR_1,AOR_2,AOR_3,AOR_4

# Apply only UOI_1 and UOI_2
--mutators UOI_1,UOI_2

# Mix different families
--mutators AOR_1,AOD_1,UOI_1,ABS
```

### Using Family Groups
```bash
# Apply all AOR sub-operators
--mutators AOR_ALL

# Apply all UOI sub-operators
--mutators UOI_ALL

# Combine multiple families
--mutators AOR_ALL,UOI_ALL,OBBN_ALL
```

### Using Comprehensive Set
```bash
# Apply all experimental comprehensive operators
--mutators COMPREHENSIVE

# Combine with standard operators
--mutators DEFAULTS,AOR_ALL,UOI_ALL
```

## Full Matrix Research Mode

When using `--fullMatrixResearchMode`, each mutation is tested against every test case individually, providing detailed information about:
- Which specific tests detect each mutation
- Transition states (F2F, F2P, P2P, P2F)
- Survival/kill status per test

Example output with all AOR sub-operators:
```csv
mutant_id,class,method,line,mutator,test_name,transition,status,num_tests_run
"...,mutator=...AOR_1]",Calculator,add,8,AOR_1,testAddition,F2P,KILLED,6
"...,mutator=...AOR_2]",Calculator,add,8,AOR_2,testAddition,F2F,SURVIVED,6
"...,mutator=...AOR_3]",Calculator,add,8,AOR_3,testAddition,F2F,SURVIVED,6
"...,mutator=...AOR_4]",Calculator,add,8,AOR_4,testAddition,F2F,SURVIVED,6
```

## Implementation Details

### File Structure
- **Mutator Implementations**: `pitest/src/main/java/org/pitest/mutationtest/engine/gregor/mutators/experimental/`
- **Group Registration**: `pitest/src/main/java/org/pitest/mutationtest/engine/gregor/config/ComprehensiveMutatorGroups.java`
- **Service Loader**: `pitest/src/main/resources/META-INF/services/org.pitest.mutationtest.engine.gregor.config.MutatorGroup`

### Service Loader Registration
The `ComprehensiveMutatorGroups` class is registered in:
```
META-INF/services/org.pitest.mutationtest.engine.gregor.config.MutatorGroup
```

This ensures that all individual sub-mutators and family groups are available when PIT loads mutators.

## Validation

The fix was validated by:
1. Testing with specific sub-operator lists: `--mutators AOR_1,AOR_2,AOR_3,AOR_4`
2. Verifying all sub-operators are applied to the same mutation point
3. Confirming different mutation behaviors for each sub-operator
4. Testing in full matrix research mode to observe detailed results

## Benefits

This implementation provides:
1. **Complete Coverage**: All sub-operators within each family are now available
2. **Fine-Grained Control**: Users can select specific sub-operators
3. **Convenience Groups**: Family groups (e.g., `AOR_ALL`) for easier usage
4. **Research Compatibility**: Full support for detailed mutation analysis
5. **Backward Compatibility**: Existing mutator specifications continue to work

## Recommendations

For comprehensive mutation testing:
1. Use family groups like `AOR_ALL`, `UOI_ALL` for complete coverage
2. Use `--fullMatrixResearchMode` with `CSV` output for detailed analysis
3. Consider combining multiple families: `--mutators AOR_ALL,UOI_ALL,AOD_ALL`
4. For research purposes, use the `COMPREHENSIVE` group for all experimental operators

This fix ensures that PIT now provides the comprehensive mutation operator coverage that researchers and practitioners expect for thorough mutation testing analysis.
