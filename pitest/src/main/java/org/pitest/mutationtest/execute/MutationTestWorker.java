/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package org.pitest.mutationtest.execute;

import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationStatusTestPair;
import org.pitest.mutationtest.environment.ResetEnvironment;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.testapi.TestResult;
import org.pitest.testapi.TestUnit;
import org.pitest.testapi.execute.Container;
import org.pitest.testapi.execute.ExitingResultCollector;
import org.pitest.testapi.execute.Pitest;
import org.pitest.testapi.execute.containers.ConcreteResultCollector;
import org.pitest.testapi.execute.containers.UnContainer;
import org.pitest.util.Log;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.pitest.util.Unchecked.translateCheckedException;

public class MutationTestWorker {

  private static final Logger                               LOG   = Log
      .getLogger();

  // micro optimise debug logging
  private static final boolean                              DEBUG = LOG
      .isLoggable(Level.FINE);

  private final Mutater                                     mutater;
  private final ClassLoader                                 loader;
  private final HotSwap                                     hotswap;
  private final boolean                                     fullMutationMatrix;
  private final boolean                                     fullMatrixResearchMode;
  private final String                                      reportDir;

  private final ResetEnvironment                            reset;

  // Test case metadata passed from main process
  private final Map<String, TestCaseMetadata>              testCaseMetadata;

  // Baseline results extracted from test case metadata for research mode
  private Map<String, Boolean> baselineResults = null;


  public MutationTestWorker(HotSwap hotswap,
                            Mutater mutater,
                            ClassLoader loader,
                            ResetEnvironment reset,
                            boolean fullMutationMatrix,
                            boolean fullMatrixResearchMode,
                            String reportDir,
                            Map<String, TestCaseMetadata> testCaseMetadata) {
    this.loader = loader;
    this.reset = reset;
    this.mutater = mutater;
    this.hotswap = hotswap;
    this.fullMutationMatrix = fullMutationMatrix;
    this.fullMatrixResearchMode = fullMatrixResearchMode;
    this.reportDir = reportDir;
    this.testCaseMetadata = testCaseMetadata;
  }

  protected void run(final Collection<MutationDetails> range, final Reporter r,
      final TimeOutDecoratedTestSource testSource) throws IOException {

    // In research mode, extract baseline results from metadata once for all mutations
    if (this.fullMatrixResearchMode && this.baselineResults == null) {
      System.out.println("DEBUG: MutationTestWorker - fullMatrixResearchMode enabled, extracting baseline results from metadata");
      extractBaselineResultsFromMetadata();
      System.out.println("DEBUG: MutationTestWorker - baseline extraction completed, stored " 
                        + (this.baselineResults != null ? this.baselineResults.size() : 0) 
                        + " results");
    }

    for (final MutationDetails mutation : range) {
      if (DEBUG) {
        LOG.fine("Running mutation " + mutation);
      }
      final long t0 = System.nanoTime();
      processMutation(r, testSource, mutation);
      if (DEBUG) {
        LOG.fine("processed mutation in " + NANOSECONDS.toMillis(System.nanoTime() - t0)
            + " ms.");
      }
    }

  }

  private void processMutation(Reporter r,
                               TimeOutDecoratedTestSource testSource,
                               MutationDetails mutationDetails) {

    final MutationIdentifier mutationId = mutationDetails.getId();
    final Mutant mutatedClass = this.mutater.getMutation(mutationId);

    // Save mutated bytecode for research mode analysis
    saveMutatedBytecode(mutationId, mutatedClass);

    reset.resetFor(mutatedClass);

    if (DEBUG) {
      LOG.fine("mutating method " + mutatedClass.getDetails().getMethod());
    }
    
    // In research mode, use all tests instead of just covering tests
    final List<TestUnit> relevantTests;
    if (this.fullMatrixResearchMode) {
      List<TestUnit> allTests = testSource.getAllTests();
      relevantTests = orderTestsByTestCaseId(allTests);
      if (DEBUG) {
        LOG.fine("Research mode: Using all " + relevantTests.size() + " tests for mutation " + mutationId + " (ordered by tcID)");
      }
    } else {
      relevantTests = testSource.translateTests(mutationDetails.getTestsInOrder());
      if (DEBUG) {
        LOG.fine("Standard mode: Using " + relevantTests.size() + " covering tests for mutation " + mutationId);
      }
    }

    r.describe(mutationId);

    final MutationStatusTestPair mutationDetected = handleMutation(
        mutationDetails, mutatedClass, relevantTests);

    r.report(mutationId, mutationDetected);
    if (DEBUG) {
      LOG.fine("Mutation " + mutationId + " detected = " + mutationDetected);
    }
  }

  private MutationStatusTestPair handleMutation(
      final MutationDetails mutationId, final Mutant mutatedClass,
      final List<TestUnit> relevantTests) {
    final MutationStatusTestPair mutationDetected;
    if ((relevantTests == null) || relevantTests.isEmpty()) {
      LOG.log(Level.WARNING, "No test coverage for mutation " + mutationId + " in " + mutatedClass.getDetails().getMethod()
              + ". This should have been detected in the outer process so treating as an error");
      mutationDetected =  MutationStatusTestPair.notAnalysed(0, DetectionStatus.RUN_ERROR, Collections.emptyList());
    } else {
      mutationDetected = handleCoveredMutation(mutationId, mutatedClass,
          relevantTests);

    }
    return mutationDetected;
  }

  private MutationStatusTestPair handleCoveredMutation(
      final MutationDetails mutationId, final Mutant mutatedClass,
      final List<TestUnit> relevantTests) {
    final MutationStatusTestPair mutationDetected;
    if (DEBUG) {
      LOG.fine(relevantTests.size() + " relevant test for "
          + mutatedClass.getDetails().getMethod());
    }

    final Container c = createNewContainer();
    final long t0 = System.nanoTime();

    if (this.fullMatrixResearchMode) {
      // Implement correct mutation testing with baseline comparison
      mutationDetected = doCorrectMutationTestingWithBaseline(c, mutationId, mutatedClass, relevantTests);
    } else {
      // Standard PIT approach
      if (this.hotswap.insertClass(mutationId.getClassName(), this.loader,
          mutatedClass.getBytes())) {
        if (DEBUG) {
          LOG.fine("replaced class with mutant in "
              + NANOSECONDS.toMillis(System.nanoTime() - t0) + " ms");
        }

        mutationDetected = doTestsDetectMutation(c, relevantTests);
      } else {
        LOG.warning("Mutation " + mutationId + " was not viable ");
        mutationDetected = MutationStatusTestPair.notAnalysed(0,
            DetectionStatus.NON_VIABLE, relevantTests.stream()
                .map(t -> t.getDescription().getQualifiedName())
                .collect(Collectors.toList()));
      }
    }
    return mutationDetected;
  }

  /**
   * Perform correct mutation testing by comparing results with baseline.
   * This method uses pre-captured baseline results and applies mutation and compares.
   */
  private MutationStatusTestPair doCorrectMutationTestingWithBaseline(
      final Container c, 
      final MutationDetails mutationId, 
      final Mutant mutatedClass,
      final List<TestUnit> relevantTests) {
    
    try {
      // Use pre-captured baseline results
      if (this.baselineResults == null) {
        throw new IllegalStateException("Baseline results not captured for research mode");
      }
      
      if (DEBUG) {
        LOG.fine("Using pre-captured baseline results for mutation " + mutationId);
      }
      
      // Apply mutation and run tests
      if (!this.hotswap.insertClass(mutationId.getClassName(), this.loader, mutatedClass.getBytes())) {
        LOG.warning("Mutation " + mutationId + " was not viable ");
        return MutationStatusTestPair.notAnalysed(0, DetectionStatus.NON_VIABLE, 
            relevantTests.stream().map(t -> t.getDescription().getQualifiedName()).collect(Collectors.toList()));
      }
      
      if (DEBUG) {
        LOG.fine("Applied mutation " + mutationId 
                + ", running tests on mutated code");
      }
      
      // Run tests on mutated code with baseline-aware listener
      final BaselineAwareMutationTestListener mutatedListener;
      if (this.testCaseMetadata != null && !this.testCaseMetadata.isEmpty()) {
        mutatedListener = BaselineAwareMutationTestListener.fromTestCaseMetadata(this.testCaseMetadata);
      } else {
        mutatedListener = new BaselineAwareMutationTestListener(this.baselineResults);
      }
      final Pitest mutatedPit = new Pitest(mutatedListener);
      mutatedPit.run(c, relevantTests);
      
      // Create result based on baseline comparison
      if (DEBUG) {
        LOG.fine("Mutation " + mutationId + " status: " 
                + mutatedListener.getOverallStatus() 
                + ", killing tests: " + mutatedListener.getKillingTests().size());
      }
      
      return createBaselineAwareStatusTestPair(mutatedListener, relevantTests);
      
    } catch (final Exception ex) {
      throw translateCheckedException(ex);
    }
  }

  /**
   * Extract baseline test results from received metadata in research mode
   */
  private void extractBaselineResultsFromMetadata() {
    System.out.println("DEBUG: MutationTestWorker.extractBaselineResultsFromMetadata() - Starting baseline extraction from metadata");
    if (DEBUG) {
      LOG.fine("Extracting baseline results from test case metadata for research mode");
    }
    
    try {
      if (this.testCaseMetadata == null || this.testCaseMetadata.isEmpty()) {
        System.out.println("DEBUG: No test case metadata available, cannot extract baseline results");
        LOG.warning("No test case metadata available for research mode");
        this.baselineResults = new HashMap<>();
        return;
      }
      
      this.baselineResults = new HashMap<>();
      
      // Extract baseline results from metadata
      for (Map.Entry<String, TestCaseMetadata> entry : this.testCaseMetadata.entrySet()) {
        String testName = entry.getKey();
        TestCaseMetadata metadata = entry.getValue();
        this.baselineResults.put(testName, metadata.isBaselinePassed());
        
        System.out.println("DEBUG: Baseline test " + (metadata.isBaselinePassed() ? "passed" : "failed") + ": " + testName);
        if (DEBUG) {
          LOG.fine("Baseline test " + (metadata.isBaselinePassed() ? "passed" : "failed") + ": " + testName);
        }
      }
      
      // Store baseline results in holder for other components if needed
      BaselineResultsHolder.setBaselineResults(this.baselineResults);
      
      // Debug: Verify baseline results are stored
      System.out.println("DEBUG: Stored baseline results in holder, size: " + this.baselineResults.size());
      System.out.println("DEBUG: Sample stored results: " + this.baselineResults.entrySet().stream().limit(3).collect(java.util.stream.Collectors.toList()));
      System.out.println("DEBUG: Verified holder has results: " + BaselineResultsHolder.hasBaselineResults());
      
      if (DEBUG) {
        long passingCount = this.baselineResults.values().stream().mapToLong(b -> b ? 1 : 0).sum();
        long failingCount = this.baselineResults.size() - passingCount;
        LOG.fine("Extracted baseline: " + this.baselineResults.size() + " tests, "
                + passingCount + " passing, "
                + failingCount + " failing");
      }
    } catch (final Exception ex) {
      System.out.println("DEBUG: Error extracting baseline results from metadata: " + ex.getMessage());
      LOG.log(Level.WARNING, "Error extracting baseline results from metadata", ex);
      this.baselineResults = new HashMap<>();
      throw translateCheckedException(ex);
    }
  }



  /**
   * Save the mutated bytecode to disk for analysis purposes.
   * Only used in fullMatrixResearchMode when reportDir is available.
   * Creates a directory structure: reportDir/mutants/ClassName/MethodName/
   */
  private void saveMutatedBytecode(final MutationIdentifier mutationId, final Mutant mutatedClass) {
    if (!this.fullMatrixResearchMode || this.reportDir == null || this.reportDir.isEmpty()) {
      return;
    }
    
    try {
      // Create structured directory under reportDir/mutants/
      final String className = mutationId.getClassName().asJavaName();
      final String methodName = mutatedClass.getDetails().getMethod();
      final int lineNumber = mutatedClass.getDetails().getLineNumber();
      final int mutationIndex = mutatedClass.getDetails().getFirstIndex();
      final String mutator = mutatedClass.getDetails().getMutator();
      
      // Create directory structure: mutants/ClassName/MethodName/
      final Path classDir = Paths.get(this.reportDir, "mutants", className.replace('.', File.separatorChar), methodName);
      Files.createDirectories(classDir);
      
      // Note: Original bytecode is now saved once in main process for efficiency
      
      // Create a descriptive filename
      // Format: Line_LineNumber_Index_MutationIndex_Mutator.class
      final String filename = String.format("Line_%d_Index_%d_%s.class", 
          lineNumber, mutationIndex, mutator.replace(' ', '_').replace('/', '_'));
      
      final Path bytecodeFile = classDir.resolve(filename);
      
      // Write the mutated bytecode
      Files.write(bytecodeFile, mutatedClass.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      
      // Also create a metadata file with detailed information about this mutant
      final String metadataFilename = filename.replace(".class", ".info");
      final Path metadataFile = classDir.resolve(metadataFilename);
      final String metadata = String.format(
          "Mutation ID: %s%n"
          + "Class: %s%n"
          + "Method: %s%n"
          + "Line Number: %d%n"
          + "Mutation Index: %d%n"
          + "Mutator: %s%n"
          + "Description: %s%n"
          + "Bytecode Size: %d bytes%n"
          + "Bytecode File: %s%n"
          + "Original Class File: ORIGINAL_%s.class (saved by main process)%n"
          + "%n"
          + "To compare with original:%n"
          + "  diff <(javap -c %s) <(javap -c ORIGINAL_%s.class)%n"
          + "%n"
          + "To decompile this bytecode to Java source, you can use:%n"
          + "  javap -c -p %s%n"
          + "  Or use any Java decompiler like CFR, Fernflower, or JD-GUI%n",
          mutationId.toString(),
          className,
          methodName,
          lineNumber,
          mutationIndex,
          mutator,
          mutatedClass.getDetails().getDescription(),
          mutatedClass.getBytes().length,
          filename,
          className.substring(className.lastIndexOf('.') + 1),
          filename,
          className.substring(className.lastIndexOf('.') + 1),
          filename
      );
      Files.write(metadataFile, metadata.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      
      if (DEBUG) {
        LOG.fine("Saved mutated bytecode for " + mutationId + " to " + bytecodeFile);
        LOG.fine("Saved mutation metadata to " + metadataFile);
      }
      
    } catch (final Exception ex) {
      LOG.warning("Failed to save mutated bytecode for " + mutationId + ": " + ex.getMessage());
      if (DEBUG) {
        ex.printStackTrace();
      }
    }
  }

  private static Container createNewContainer() {
    return new UnContainer() {
      @Override
      public List<TestResult> execute(final TestUnit group) {
        final Collection<TestResult> results = new ConcurrentLinkedDeque<>();
        final ExitingResultCollector rc = new ExitingResultCollector(
            new ConcreteResultCollector(results));
        group.execute(rc);
        return new ArrayList<>(results);
      }
    };
  }



  @Override
  public String toString() {
    return "MutationTestWorker [mutater=" + this.mutater + ", loader="
        + this.loader + ", hotswap=" + this.hotswap + "]";
  }

  private MutationStatusTestPair doTestsDetectMutation(final Container c,
      final List<TestUnit> tests) {
    try {
      if (this.fullMatrixResearchMode) {
        // Use baseline-aware approach for accurate mutation detection
        return doCorrectMutationTesting(c, tests);
      } else {
        // Use standard PIT behavior with CheckTestHasFailedResultListener
        final CheckTestHasFailedResultListener listener = new CheckTestHasFailedResultListener(this.fullMutationMatrix);
        final Pitest pit = new Pitest(listener);

        pit.run(c, tests);

        return createStatusTestPair(listener, tests);
      }
    } catch (final Exception ex) {
      throw translateCheckedException(ex);
    }

  }

  /**
   * Perform correct mutation testing by comparing results against baseline.
   * This method implements the correct definition of mutation killing:
   * - A mutation is KILLED if any test changes its result from the baseline
   * - A mutation SURVIVES if all tests have the same result as baseline
   */
  private MutationStatusTestPair doCorrectMutationTesting(final Container c, final List<TestUnit> tests) {
    try {
      // Check if we have baseline results
      if (this.baselineResults == null || this.baselineResults.isEmpty()) {
        LOG.warning("No baseline results available for baseline-aware mutation testing. Using simple approach.");
        // Fall back to simple approach
        final FullMutationMatrixListener fullMatrixListener = new FullMutationMatrixListener();
        final Pitest pit = new Pitest(fullMatrixListener);
        pit.run(c, tests);
        return createFullMatrixStatusTestPair(fullMatrixListener, tests);
      }
      
      // Use baseline-aware listener with captured baseline results
      final BaselineAwareMutationTestListener baselineListener;
      if (this.testCaseMetadata != null && !this.testCaseMetadata.isEmpty()) {
        baselineListener = BaselineAwareMutationTestListener.fromTestCaseMetadata(this.testCaseMetadata);
      } else {
        baselineListener = new BaselineAwareMutationTestListener(this.baselineResults);
      }
      final Pitest pit = new Pitest(baselineListener);
      
      // Run tests on the mutated code
      pit.run(c, tests);
      
      // Return results using baseline-aware comparison
      return createBaselineAwareStatusTestPair(baselineListener, tests);
      
    } catch (final Exception ex) {
      throw translateCheckedException(ex);
    }
  }

  private MutationStatusTestPair createFullMatrixStatusTestPair(
      final FullMutationMatrixListener listener, List<TestUnit> relevantTests) {
    List<String> killingTests = listener.getKillingTests();
    List<String> survivingTests = listener.getSurvivingTests();
    List<String> coveredTests = relevantTests.stream()
        .map(t -> t.getDescription().getQualifiedName()).collect(Collectors.toList());

    return new MutationStatusTestPair(listener.getNumberOfTestsRun(),
        listener.getOverallStatus(), killingTests, survivingTests, coveredTests);
  }

  private MutationStatusTestPair createStatusTestPair(
      final CheckTestHasFailedResultListener listener, List<TestUnit> relevantTests) {
    if (this.fullMutationMatrix) {
      // For full mutation matrix, record all test results
      List<String> killingTests = listener.getFailingTests().stream()
          .map(d -> d.getQualifiedName()).collect(Collectors.toList());
      List<String> survivingTests = listener.getSucceedingTests().stream()
          .map(d -> d.getQualifiedName()).collect(Collectors.toList());
      List<String> coveredTests = relevantTests.stream()
          .map(t -> t.getDescription().getQualifiedName()).collect(Collectors.toList());

      return new MutationStatusTestPair(listener.getNumberOfTestsRun(),
          listener.status(), killingTests, survivingTests, coveredTests);
    } else {
      // Standard PIT behavior - return only the first killing test
      if (!listener.getFailingTests().isEmpty()) {
        return new MutationStatusTestPair(listener.getNumberOfTestsRun(),
            DetectionStatus.KILLED, listener.getFailingTests().get(0).getQualifiedName());
      } else {
        return new MutationStatusTestPair(listener.getNumberOfTestsRun(),
            DetectionStatus.SURVIVED, "");
      }
    }
  }

  /**
   * Create status test pair from baseline-aware listener results
   */
  private MutationStatusTestPair createBaselineAwareStatusTestPair(
      final BaselineAwareMutationTestListener listener, List<TestUnit> relevantTests) {
    List<String> killingTests = listener.getKillingTests();
    List<String> survivingTests = listener.getSurvivingTests();
    List<String> coveredTests = relevantTests.stream()
        .map(t -> t.getDescription().getQualifiedName()).collect(Collectors.toList());
    List<DetailedMutationTestResult> detailedResults = listener.getTestResults();

    return new MutationStatusTestPair(listener.getNumberOfTestsRun(),
        listener.getOverallStatus(), killingTests, survivingTests, coveredTests, detailedResults);
  }

  /**
   * Order tests by their test case ID when test case metadata is available.
   * This ensures tests are executed in the same order as they were initiated.
   * @param tests the list of tests to order
   * @return ordered list of tests
   */
  private List<TestUnit> orderTestsByTestCaseId(final List<TestUnit> tests) {
    if (this.testCaseMetadata == null || this.testCaseMetadata.isEmpty()) {
      return tests; // Return original order if no metadata
    }
    
    // Create a map of test name to TestUnit for easy lookup
    Map<String, TestUnit> testMap = new HashMap<>();
    for (TestUnit test : tests) {
      testMap.put(test.getDescription().getQualifiedName(), test);
    }
    
    // Create ordered list based on tcID
    List<TestUnit> orderedTests = new ArrayList<>();
    
    // First, add tests that have metadata in tcID order
    this.testCaseMetadata.entrySet().stream()
        .sorted(Map.Entry.comparingByValue((m1, m2) -> Integer.compare(m1.getTcID(), m2.getTcID())))
        .forEach(entry -> {
          String testName = entry.getKey();
          TestUnit test = testMap.get(testName);
          if (test != null) {
            orderedTests.add(test);
            testMap.remove(testName); // Remove from map to avoid duplicates
          }
        });
    
    // Add any remaining tests that don't have metadata
    orderedTests.addAll(testMap.values());
    
    if (DEBUG) {
      LOG.fine("Ordered " + orderedTests.size() + " tests by tcID. " 
              + (tests.size() - testMap.size()) + " tests had metadata, " 
              + testMap.size() + " tests did not.");
    }
    
    return orderedTests;
  }

}