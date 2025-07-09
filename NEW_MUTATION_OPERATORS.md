# New Mutation Operators in PiTest

This document describes the newly implemented mutation operators that have been added to PiTest based on the official PIT documentation. These operators provide more comprehensive mutation coverage and follow the mutation testing standards described in the research literature.

## Overview

The following mutation operators have been implemented:

- **AOR** (Arithmetic Operator Replacement) - 4 sub-mutators (comprehensive coverage)
- **AOD** (Arithmetic Operator Deletion) - 2 sub-mutators  
- **UOI** (Unary Operator Insertion) - 4 sub-mutators
- **CRCR** (Constant Replacement) - 6 sub-mutators
- **OBBN** (Bitwise Operator) - 3 sub-mutators
- **ROR** (Relational Operator Replacement) - 5 sub-mutators
- **ABS** (Negation Mutator) - 1 mutator

All operators are implemented as experimental mutators and can be enabled individually by specifying their names in the mutators configuration.

---

## Arithmetic Operator Replacement (AOR)

The AOR mutators replace binary arithmetic operations with other arithmetic operations according to a comprehensive replacement table that ensures complete coverage of all possible arithmetic operator substitutions.

### Comprehensive Replacement Table

| Original | AOR_1 | AOR_2 | AOR_3 | AOR_4 |
|----------|-------|-------|-------|-------|
| **+**    | **-** | **\*** | **/** | **%** |
| **-**    | **+** | **\*** | **/** | **%** |
| **\***   | **/** | **%** | **+** | **-** |
| **/**    | **\*** | **%** | **+** | **-** |
| **%**    | **\*** | **/** | **+** | **-** |

### Sub-mutators:

#### AOR_1
- **+** → **-** (addition to subtraction)
- **-** → **+** (subtraction to addition)
- **\*** → **/** (multiplication to division)
- **/** → **\*** (division to multiplication)
- **%** → **\*** (modulus to multiplication)

#### AOR_2
- **+** → **\*** (addition to multiplication)
- **-** → **\*** (subtraction to multiplication)
- **\*** → **%** (multiplication to modulus)
- **/** → **%** (division to modulus)
- **%** → **/** (modulus to division)

#### AOR_3
- **+** → **/** (addition to division)
- **-** → **/** (subtraction to division)
- **\*** → **+** (multiplication to addition)
- **/** → **+** (division to addition)
- **%** → **+** (modulus to addition)

#### AOR_4
- **+** → **%** (addition to modulus)
- **-** → **%** (subtraction to modulus)
- **\*** → **-** (multiplication to subtraction)
- **/** → **-** (division to subtraction)
- **%** → **-** (modulus to subtraction)

### Example:
```java
// Original code
int result = a + b;

// AOR_1 mutation: + -> -
int result = a - b;

// AOR_2 mutation: + -> *
int result = a * b;

// AOR_3 mutation: + -> /
int result = a / b;

// AOR_4 mutation: + -> %
int result = a % b;
```

### Usage:
```xml
<mutators>
    <mutator>AOR_1</mutator>
    <mutator>AOR_2</mutator>
    <mutator>AOR_3</mutator>
    <mutator>AOR_4</mutator>
</mutators>
```

---

## Arithmetic Operator Deletion (AOD)

The AOD mutators replace an arithmetic operation with one of its operands, effectively "deleting" the operation.

### Sub-mutators:

#### AOD_1
Replaces the arithmetic operation with the **first operand**.

#### AOD_2
Replaces the arithmetic operation with the **second operand**.

### Example:
```java
// Original code
int result = a + b;

// AOD_1 mutation
int result = a;

// AOD_2 mutation  
int result = b;
```

### Usage:
```xml
<mutators>
    <mutator>AOD_1</mutator>
    <mutator>AOD_2</mutator>
</mutators>
```

---

## Unary Operator Insertion (UOI)

The UOI mutators insert unary operators (increment/decrement) to variable operations, affecting local variables, array variables, fields, and parameters.

### Sub-mutators:

#### UOI_1 (Post-increment)
- **a** → **a++**

#### UOI_2 (Post-decrement)
- **a** → **a--**

#### UOI_3 (Pre-increment)
- **a** → **++a**

#### UOI_4 (Pre-decrement)
- **a** → **--a**

### Example:
```java
// Original code
int getValue() {
    return value;
}

// UOI_1 mutation (post-increment)
int getValue() {
    return value++;
}

// UOI_3 mutation (pre-increment)
int getValue() {
    return ++value;
}
```

### Usage:
```xml
<mutators>
    <mutator>UOI_1</mutator>
    <mutator>UOI_2</mutator>
    <mutator>UOI_3</mutator>
    <mutator>UOI_4</mutator>
</mutators>
```

---

## Constant Replacement (CRCR)

The CRCR mutators replace constant values according to a comprehensive replacement table that tests the sensitivity of the code to different constant values.

### Sub-mutators:

#### CRCR_1
- **c** → **1**

#### CRCR_2
- **c** → **0**

#### CRCR_3
- **c** → **-1**

#### CRCR_4
- **c** → **-c** (negation)

#### CRCR_5
- **c** → **c+1** (increment)

#### CRCR_6
- **c** → **c-1** (decrement)

### Example:
```java
// Original code
int x = 5;

// CRCR_1 mutation
int x = 1;

// CRCR_2 mutation
int x = 0;

// CRCR_4 mutation
int x = -5;
```

### Usage:
```xml
<mutators>
    <mutator>CRCR_1</mutator>
    <mutator>CRCR_2</mutator>
    <mutator>CRCR_3</mutator>
    <mutator>CRCR_4</mutator>
    <mutator>CRCR_5</mutator>
    <mutator>CRCR_6</mutator>
</mutators>
```

---

## Bitwise Operator (OBBN)

The OBBN mutators modify bitwise operations (&, |, ^) in three different ways to test the robustness of bitwise logic.

### Sub-mutators:

#### OBBN_1 (Reverse operators)
- **&** ↔ **|** (AND ↔ OR)
- **^** → **&** (XOR → AND)

#### OBBN_2 (Replace with first operand)
Replaces the bitwise operation with the first operand.

#### OBBN_3 (Replace with second operand)
Replaces the bitwise operation with the second operand.

### Example:
```java
// Original code
int result = a & b;

// OBBN_1 mutation
int result = a | b;

// OBBN_2 mutation
int result = a;

// OBBN_3 mutation
int result = b;
```

### Usage:
```xml
<mutators>
    <mutator>OBBN_1</mutator>
    <mutator>OBBN_2</mutator>
    <mutator>OBBN_3</mutator>
</mutators>
```

---

## Relational Operator Replacement (ROR)

The ROR mutators replace relational operators with other relational operators according to the official PIT documentation. The mutator is composed of 5 sub-mutators (ROR1 to ROR5) that mutate the operators according to the table below.

### Official PIT Replacement Table

| Original | ROR_1 | ROR_2 | ROR_3 | ROR_4 | ROR_5 |
|----------|-------|-------|-------|-------|-------|
| **<**    | **<=** | **>** | **>=** | **==** | **!=** |
| **<=**   | **<** | **>** | **>=** | **==** | **!=** |
| **>**    | **<** | **<=** | **>=** | **==** | **!=** |
| **>=**   | **<** | **<=** | **>** | **==** | **!=** |
| **==**   | **<** | **<=** | **>** | **>=** | **!=** |
| **!=**   | **<** | **<=** | **>** | **>=** | **==** |

### Sub-mutators:

#### ROR_1
- **<** → **<=** (less than to less than or equal)
- **<=** → **<** (less than or equal to less than)
- **>** → **<** (greater than to less than)
- **>=** → **<** (greater than or equal to less than)
- **==** → **<** (equality to less than)
- **!=** → **<** (inequality to less than)

#### ROR_2
- **<** → **>** (less than to greater than)
- **<=** → **>** (less than or equal to greater than)
- **>** → **<=** (greater than to less than or equal)
- **>=** → **<=** (greater than or equal to less than or equal)
- **==** → **<=** (equality to less than or equal)
- **!=** → **<=** (inequality to less than or equal)

#### ROR_3
- **<** → **>=** (less than to greater than or equal)
- **<=** → **>=** (less than or equal to greater than or equal)
- **>** → **>=** (greater than to greater than or equal)
- **>=** → **>** (greater than or equal to greater than)
- **==** → **>** (equality to greater than)
- **!=** → **>** (inequality to greater than)

#### ROR_4
- **<** → **==** (less than to equality)
- **<=** → **==** (less than or equal to equality)
- **>** → **==** (greater than to equality)
- **>=** → **==** (greater than or equal to equality)
- **==** → **>=** (equality to greater than or equal)
- **!=** → **>=** (inequality to greater than or equal)

#### ROR_5
- **<** → **!=** (less than to inequality)
- **<=** → **!=** (less than or equal to inequality)
- **>** → **!=** (greater than to inequality)
- **>=** → **!=** (greater than or equal to inequality)
- **==** → **!=** (equality to inequality)
- **!=** → **==** (inequality to equality)

### Example:
```java
// Original code
if (a < b) {
    // do something
}

// ROR_1 mutation: < -> <=
if (a <= b) {
    // do something
}

// ROR_2 mutation: < -> >
if (a > b) {
    // do something
}

// ROR_3 mutation: < -> >=
if (a >= b) {
    // do something
}

// ROR_4 mutation: < -> ==
if (a == b) {
    // do something
}

// ROR_5 mutation: < -> !=
if (a != b) {
    // do something
}
```

### Usage:
```xml
<mutators>
    <mutator>ROR_1</mutator>
    <mutator>ROR_2</mutator>
    <mutator>ROR_3</mutator>
    <mutator>ROR_4</mutator>
    <mutator>ROR_5</mutator>
</mutators>
```

---

## Negation Mutator (ABS)

The ABS mutator replaces any use of a numeric variable (local variable, field, array cell) with its negation.

### Example:
```java
// Original code
public float getValue(final float i) {
    return i;
}

// ABS mutation
public float getValue(final float i) {
    return -i;
}
```

### Usage:
```xml
<mutators>
    <mutator>ABS</mutator>
</mutators>
```

---

## Usage Examples

### Using All New Mutators
```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>dev-SNAPSHOT</version>
    <configuration>
        <mutators>
            <!-- Arithmetic Operator Replacement (Comprehensive) -->
            <mutator>AOR_1</mutator>
            <mutator>AOR_2</mutator>
            <mutator>AOR_3</mutator>
            <mutator>AOR_4</mutator>
            
            <!-- Arithmetic Operator Deletion -->
            <mutator>AOD_1</mutator>
            <mutator>AOD_2</mutator>
            
            <!-- Unary Operator Insertion -->
            <mutator>UOI_1</mutator>
            <mutator>UOI_2</mutator>
            <mutator>UOI_3</mutator>
            <mutator>UOI_4</mutator>
            
            <!-- Constant Replacement -->
            <mutator>CRCR_1</mutator>
            <mutator>CRCR_2</mutator>
            <mutator>CRCR_3</mutator>
            <mutator>CRCR_4</mutator>
            <mutator>CRCR_5</mutator>
            <mutator>CRCR_6</mutator>
            
            <!-- Bitwise Operator -->
            <mutator>OBBN_1</mutator>
            <mutator>OBBN_2</mutator>
            <mutator>OBBN_3</mutator>
            
            <!-- Relational Operator Replacement -->
            <mutator>ROR_1</mutator>
            <mutator>ROR_2</mutator>
            <mutator>ROR_3</mutator>
            <mutator>ROR_4</mutator>
            <mutator>ROR_5</mutator>
            
            <!-- Negation -->
            <mutator>ABS</mutator>
        </mutators>
    </configuration>
</plugin>
```

### Using with Command Line
```bash
mvn org.pitest:pitest-maven:mutationCoverage -Dpit.mutators=AOR_1,AOD_1,UOI_1,CRCR_1,OBBN_1,ROR_1,ABS
```

### Using with Default Mutators
```xml
<mutators>
    <!-- Include default mutators -->
    <mutator>DEFAULTS</mutator>
    
    <!-- Add specific new mutators -->
    <mutator>AOR_1</mutator>
    <mutator>AOD_1</mutator>
    <mutator>UOI_1</mutator>
    <mutator>ABS</mutator>
</mutators>
```

---

## Implementation Details

### Technical Notes

1. **Bytecode Level**: All mutators operate at the bytecode level using ASM, ensuring compatibility with any JVM language that compiles to bytecode.

2. **Type Safety**: The mutators are type-aware and handle different numeric types (int, long, float, double) appropriately.

3. **Stack Management**: Complex mutators like AOD and UOI properly manage the JVM operand stack to ensure correct bytecode generation.

4. **Mutation Context**: Each mutator integrates with PiTest's mutation context system for proper mutation tracking and reporting.

5. **Comprehensive Coverage**: The AOR mutator now provides complete coverage of all arithmetic operator replacements, ensuring thorough testing of arithmetic logic.

### Performance Considerations

- These mutators may generate more mutations than the default set, potentially increasing test execution time
- The comprehensive AOR implementation creates 4-5 mutations per arithmetic operation
- Some mutators (especially UOI and CRCR) may create many mutations for code with frequent variable access or constants
- Consider using specific subsets of mutators based on your testing goals

### Compatibility

- Compatible with Java 8+
- Works with existing PiTest configurations
- Can be combined with existing default and experimental mutators
- Supports all PiTest output formats (XML, HTML, CSV)

---

## Key Improvements

### Enhanced AOR Coverage

The updated AOR implementation now provides **complete arithmetic operator replacement coverage**:

- **All 5 arithmetic operators** (`+`, `-`, `*`, `/`, `%`) can be replaced with **all other arithmetic operators**
- **20 total replacement patterns** across 4 sub-mutators (5 patterns per mutator)
- **Systematic and comprehensive** coverage following established mutation testing research
- **Balanced distribution** of replacement patterns to avoid bias toward specific operators

This ensures that your tests are thoroughly evaluated against all possible arithmetic operator mistakes.

---

## References

- [PiTest Official Documentation](https://pitest.org/quickstart/mutators/)
- [Mutation Testing: A Comprehensive Survey](https://ieeexplore.ieee.org/document/8863992)
- [An Analysis and Survey of the Development of Mutation Testing](https://ieeexplore.ieee.org/document/5487526)

---

## Contributing

If you find issues with these mutators or have suggestions for improvements, please:

1. Check the existing implementation in the `experimental` package
2. Ensure your test cases cover the specific mutation scenarios
3. Consider the bytecode implications of any proposed changes
4. Follow the existing code style and documentation patterns
