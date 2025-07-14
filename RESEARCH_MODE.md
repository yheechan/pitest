# PIT Research Mode Documentation

## Overview

The `--fullMatrixResearchMode` flag enables advanced mutation testing capabilities designed specifically for fault localization and mutation testing research. This mode provides comprehensive test execution data, detailed transition analysis, and extensive output formats optimized for research analysis.

## Key Features

### 1. **Comprehensive Test Execution**
- **All Tests on All Mutations**: Executes every test case (both passing and failing) against every generated mutant
- **Baseline Capture**: Records complete baseline test results with exception details and line coverage
- **Mutation Filtering**: Only tests mutations on lines covered by failing tests (fault localization optimization)
- **Detailed Exception Tracking**: Captures exception type, message, and filtered stack traces for precise transition analysis

### 2. **Advanced Output Formats**
- **JSON Test Results**: Structured per-test-case results for both baseline and mutation testing
- **CSV Mutation Matrix**: Complete mutation-test matrix with transition bit sequences
- **Original Bytecode Preservation**: Saves unmutated class bytecode for comparison
- **Line Coverage Mapping**: Detailed mapping between bit sequences and actual code lines

### 3. **Research-Optimized Data**
- **Transition Analysis**: Tracks Pass→Fail, Fail→Pass, Pass→Pass, Fail→Fail transitions
- **Exception Transitions**: Monitors changes in exception types, messages, and stack traces
- **Bit Sequence Encoding**: Compact representation of test result patterns

### 4. **Mutant Bytecode Organization**

Research mode organizes mutated bytecode by method for precise analysis:

```
mutants/calc/Calculator/
├── add/                    # All mutations in add() method
│   ├── 5_byte_code.class   # Mutation #5 bytecode
│   ├── 5_byte_code.info    # Mutation #5 metadata
│   └── ...
└── <init>/                 # All mutations in constructor
    ├── 1_byte_code.class   # Mutation #1 bytecode
    ├── 1_byte_code.info    # Mutation #1 metadata
    └── ...
```

This organization enables:
- **Method-level Analysis**: Compare mutations within specific methods
- **Bytecode Differential Analysis**: Compare mutated vs original bytecode
- **Mutation Operator Evaluation**: Analyze different operators on same code
- **Fault Injection Studies**: Precisely control where mutations are applied

---

## Command Line Usage

```bash
java -cp pitest.jar org.pitest.mutationtest.commandline.MutationCoverageReport \
    --fullMatrixResearchMode \
    --reportDir target/pit-reports \
    --sourceDirs src/main/java \
    --targetClasses com.example.* \
    --targetTests com.example.*Test
```

### Required Parameters
- `--fullMatrixResearchMode`: Enables research mode
- `--reportDir`: Output directory for research data
- `--sourceDirs`: Source code directories
- `--targetClasses`: Classes to mutate
- `--targetTests`: Test classes to execute

---

## Output File Structure

```
target/pit-reports/
├── baselineTestResults/               # Baseline test execution results
│   ├── 0_test_results.json           # Individual test results (JSON)
│   ├── 1_test_results.json
│   ├── ...
│   └── tcs_outcome.csv               # Test case summary (CSV)
├── mutationResults/                  # Mutation test results
│   ├── 1_mutation_test_results.json  # Individual mutation results (JSON)
│   ├── 2_mutation_test_results.json
│   ├── ...
│   └── mutation_summary.csv          # Mutation summary (CSV)
├── mutants/                          # Mutant bytecode directory
│   └── {package}/                    # Package structure (e.g., calc/)
│       └── {ClassName}/              # Class directory (e.g., Calculator/)
│           ├── {method1}/            # Method-specific directories (e.g., add/)
│           │   ├── {mutantId}_byte_code.class    # Mutated bytecode
│           │   ├── {mutantId}_byte_code.info     # Mutation metadata
│           │   ├── {mutantId}_byte_code.class    # Another mutant
│           │   ├── {mutantId}_byte_code.info
│           │   └── ...
│           ├── {method2}/            # Another method (e.g., <init>/)
│           │   ├── {mutantId}_byte_code.class
│           │   ├── {mutantId}_byte_code.info
│           │   └── ...
│           └── ...
├── original/                         # Original bytecode preservation
│   └── {package}/                    # Package structure (e.g., calc/)
│       ├── ORIGINAL_{ClassName}.class    # Original bytecode
│       ├── ORIGINAL_{ClassName}.info     # Original metadata
│       └── ...
├── full_mutation_matrix.csv          # Complete mutation-test matrix
├── line_info.csv                     # Line coverage mapping
└── mutations.csv                     # Standard PIT mutation report (CSV)
```

---

## File Format Specifications

### 1. Baseline Test Results JSON (`{tcID}_test_results.json`)

```json
{
  "test_info": {
    "test_id": 0,
    "test_name": "com.example.CalculatorTest::testAdd",
    "result": "PASS",
    "execution_time_ms": 15.67
  },
  "exception": {
    "type": "None",
    "message": "None", 
    "stack_trace": "None"
  },
  "coverage": {
    "line_coverage_bit_sequence": "10110100...",
    "bit_sequence_length": 247
  },
  "metadata": {
    "format_version": "1.0",
    "generated_by": "PIT baseline test execution",
    "timestamp": 1705234567890
  }
}
```

### 2. Mutation Test Results JSON (`{mutationId}_mutation_test_results.json`)

```json
{
  "mutation_info": {
    "mutation_id": 1001,
    "description": "Replaced + with - in Calculator.add",
    "num_tests_executed": 25
  },
  "test_results": [
    {
      "test_info": {
        "test_index": 0,
        "test_name": "com.example.CalculatorTest::testAdd",
        "result": "FAIL"
      },
      "exception": {
        "type": "AssertionError",
        "message": "expected: <5> but was: <-1>",
        "stack_trace": "java.lang.AssertionError: expected: <5> but was: <-1>\n\tat org.junit.Assert.fail(Assert.java:88)..."
      }
    }
    ...
  ],
  "summary": {
    "total_tests": 25,
    "passed_tests": 20,
    "failed_tests": 5,
    "pass_rate": 0.8
  },
  "metadata": {
    "format_version": "1.0",
    "generated_by": "PIT mutation test execution",
    "timestamp": 1705234567890
  }
}
```

### 3. Full Mutation Matrix CSV (`full_mutation_matrix.csv`)

```csv
mutant_id,class,method,line,mutator,F2P_transition,P2F_transition,P2P_transition,F2F_transition,exception_type_transition,exception_msg_transition,stacktrace_transition,status,num_tests_run
1001,com.example.Calculator,add,15,MATH_1,0000000000,1000000000,0111111111,0000000000,0000000001,0000000001,0000000001,KILLED,25
1002,com.example.Calculator,divide,28,MATH_1,0000000000,0100000000,1011111111,0000000000,0000000000,0000000000,0000000000,KILLED,25
```

**Column Descriptions:**
- `mutant_id`: Unique mutation identifier
- `class`: Fully qualified class name
- `method`: Method name containing the mutation
- `line`: Line number of the mutation
- `mutator`: Mutation operator used
- `F2P_transition`: Fail→Pass bit sequence (one bit per test)
- `P2F_transition`: Pass→Fail bit sequence (one bit per test)  
- `P2P_transition`: Pass→Pass bit sequence (one bit per test)
- `F2F_transition`: Fail→Fail bit sequence (one bit per test)
- `exception_type_transition`: Exception type change bit sequence
- `exception_msg_transition`: Exception message change bit sequence
- `stacktrace_transition`: Stack trace change bit sequence
- `status`: Mutation status (KILLED, SURVIVED, etc.)
- `num_tests_run`: Number of tests executed

### 4. Test Case Summary CSV (`tcs_outcome.csv`)

```csv
tcID,test_name,result,execution_time_ms
0,com.example.CalculatorTest::testAdd,PASS,15.67
1,com.example.CalculatorTest::testSubtract,PASS,12.34
2,com.example.CalculatorTest::testDivideByZero,FAIL,8.91
```

### 5. Line Coverage Mapping CSV (`line_info.csv`)

```csv
line_id,code_filename,line_info
0,Calculator.java,calc.Calculator#add(int,int):15
1,Calculator.java,calc.Calculator#add(int,int):16
2,Calculator.java,calc.Calculator#<init>():8
```

### 6. Mutant Bytecode Files (`mutants/{package}/{class}/{method}/`)

**Bytecode File (`{mutantId}_byte_code.class`):**
- Contains the actual mutated Java bytecode
- Can be decompiled or analyzed with standard Java tools
- Organized by method to show exactly where mutations occur

**Metadata File (`{mutantId}_byte_code.info`):**
```
Mutation ID: 5
Class: calc.Calculator
Method: add
Line: 12
Mutator: MATH_1
Description: Replaced + with - at line 12
Original Bytecode Size: 234 bytes
Mutated Bytecode Size: 234 bytes
Package Path: calc
Saved To: mutants/calc/Calculator/add/5_byte_code.class
Purpose: Mutated bytecode for mutation analysis

This file contains the mutated version of the class.
Use with the original bytecode for differential analysis.

To decompile this mutated bytecode:
  javap -c -p 5_byte_code.class
  Or use CFR, Fernflower, or JD-GUI for source code reconstruction
```

### 7. Mutation Summary CSV (`mutationResults/mutation_summary.csv`)

This file provides a high-level summary of all mutations and their outcomes:

```csv
mutation_id,mutant_description,num_tests_executed,overall_status
1,"Replaced + with - in Calculator.<init>",6,KILLED
2,"Replaced - with + in Calculator.<init>",6,KILLED
5,"Replaced + with - in Calculator.add",6,KILLED
6,"Replaced - with + in Calculator.add",6,SURVIVED
10,"Replaced * with / in Calculator.add",6,KILLED
```

**Column Descriptions:**
- `mutation_id`: Unique mutation identifier (matches JSON filenames)
- `mutant_description`: Human-readable description of the mutation applied
- `num_tests_executed`: Number of test cases executed against this mutant
- `overall_status`: Final mutation status (KILLED, SURVIVED, NO_COVERAGE, etc.)

---

## Advanced Features

### 1. **Failing Test Coverage Filter**

Research mode automatically filters mutations to only include those on lines covered by failing tests:

```java
// Only mutations on lines like these are tested:
com.example.Calculator:15  // Line covered by failing test
com.example.Calculator:28  // Line covered by failing test
```

### 2. **Enhanced Mutation Operators**

Research mode includes comprehensive mutation operators for thorough analysis:

#### Arithmetic Operator Replacement (AOR)
- **AOR_1**: `+`→`-`, `-`→`+`, `*`→`/`, `/`→`*`, `%`→`*`
- **AOR_2**: `+`→`*`, `-`→`*`, `*`→`%`, `/`→`%`, `%`→`/`
- **AOR_3**: `+`→`/`, `-`→`/`, `*`→`+`, `/`→`+`, `%`→`+`
- **AOR_4**: `+`→`%`, `-`→`%`, `*`→`-`, `/`→`-`, `%`→`-`

#### Arithmetic Operator Deletion (AOD)
- **AOD_1**: `a + b` → `a`, `a - b` → `a`, etc.
- **AOD_2**: `a + b` → `b`, `a - b` → `b`, etc.

#### Unary Operator Insertion (UOI)
- **UOI_1**: Insert unary minus: `a` → `-a`
- **UOI_2**: Insert unary plus: `a` → `+a`
- **UOI_3**: Insert increment: `a` → `++a`
- **UOI_4**: Insert decrement: `a` → `--a`

#### Constant Replacement (CRCR)
- **CRCR_1**: Replace with 0
- **CRCR_2**: Replace with 1
- **CRCR_3**: Replace with -1
- **CRCR_4**: Replace with MAX_VALUE
- **CRCR_5**: Replace with MIN_VALUE
- **CRCR_6**: Replace with original+1

#### Relational Operator Replacement (ROR)
- **ROR_1**: `<`→`<=`, `<=`→`<`, `>`→`>=`, `>=`→`>`, `==`→`!=`, `!=`→`==`
- **ROR_2**: `<`→`>`, `<=`→`>=`, `>`→`<`, `>=`→`<=`, `==`→`!=`, `!=`→`==`
- **ROR_3**: `<`→`>=`, `<=`→`>`, `>`→`<=`, `>=`→`<`, `==`→`!=`, `!=`→`==`
- **ROR_4**: `<`→`!=`, `<=`→`!=`, `>`→`!=`, `>=`→`!=`, `==`→`!=`, `!=`→`==`
- **ROR_5**: `<`→`==`, `<=`→`==`, `>`→`==`, `>=`→`==`, `==`→`!=`, `!=`→`==`

#### Bitwise Operator (OBBN)
- **OBBN_1**: `&`→`|`, `|`→`&`, `^`→`&`
- **OBBN_2**: `&`→`^`, `|`→`^`, `^`→`|`
- **OBBN_3**: `&`→`&`, `|`→`|`, `^`→`^` (identity - for completeness)

#### Negation Mutator (ABS)
- **ABS**: Insert negation: `a` → `!a`, `!a` → `a`

### 3. **Original Bytecode Preservation**

For each mutated class, research mode saves:

```
original/com/example/
├── ORIGINAL_Calculator.class     # Original bytecode
└── ORIGINAL_Calculator.info      # Metadata file
```

**Metadata file content:**
```
Original Class: com.example.Calculator
Bytecode Size: 1234 bytes
Saved From: com/example/Calculator.class
Package Path: com/example
Saved To: /path/to/original/com/example/ORIGINAL_Calculator.class
Purpose: Baseline for mutation comparison

This is the original (unmutated) version of the class.
Compare with mutated versions to see the exact changes made.

To decompile this original bytecode:
  javap -c -p ORIGINAL_Calculator.class
  Or use any Java decompiler like CFR, Fernflower, or JD-GUI
```

---

## Version Compatibility

- **Minimum PIT Version**: 1.15.0+
- **Java Requirements**: Java 11+
- **Maven Plugin**: 1.15.0+
- **Research Mode**: Experimental feature

For the latest updates and research mode enhancements, refer to the project's GitHub repository and research documentation.
