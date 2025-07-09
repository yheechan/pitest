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
import org.pitest.testapi.Description;
import org.pitest.testapi.TestResult;
import org.pitest.testapi.TestUnit;
import org.pitest.testapi.execute.Container;
import org.pitest.testapi.execute.ExitingResultCollector;
import org.pitest.testapi.execute.Pitest;
import org.pitest.testapi.execute.containers.ConcreteResultCollector;
import org.pitest.testapi.execute.containers.UnContainer;
import org.pitest.util.Log;

import java.io.IOException;
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

  private final ResetEnvironment                            reset;

  // Baseline results captured once for all mutations in research mode
  private Map<String, Boolean> baselineResults = null;


  public MutationTestWorker(HotSwap hotswap,
                            Mutater mutater,
                            ClassLoader loader,
                            ResetEnvironment reset,
                            boolean fullMutationMatrix,
                            boolean fullMatrixResearchMode) {
    this.loader = loader;
    this.reset = reset;
    this.mutater = mutater;
    this.hotswap = hotswap;
    this.fullMutationMatrix = fullMutationMatrix;
    this.fullMatrixResearchMode = fullMatrixResearchMode;
  }

  protected void run(final Collection<MutationDetails> range, final Reporter r,
      final TimeOutDecoratedTestSource testSource) throws IOException {

    // In research mode, capture baseline results once for all mutations
    if (this.fullMatrixResearchMode && this.baselineResults == null) {
      System.out.println("DEBUG: MutationTestWorker - fullMatrixResearchMode enabled, capturing baseline results");
      captureBaselineResults(testSource);
      System.out.println("DEBUG: MutationTestWorker - baseline capture completed, stored " 
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

    reset.resetFor(mutatedClass);

    if (DEBUG) {
      LOG.fine("mutating method " + mutatedClass.getDetails().getMethod());
    }
    
    // In research mode, use all tests instead of just covering tests
    final List<TestUnit> relevantTests;
    if (this.fullMatrixResearchMode) {
      relevantTests = testSource.getAllTests();
      if (DEBUG) {
        LOG.fine("Research mode: Using all " + relevantTests.size() + " tests for mutation " + mutationId);
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
      final BaselineAwareMutationTestListener mutatedListener = 
          new BaselineAwareMutationTestListener(this.baselineResults);
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
   * Capture baseline test results once for all mutations in research mode
   */
  private void captureBaselineResults(final TimeOutDecoratedTestSource testSource) {
    System.out.println("DEBUG: MutationTestWorker.captureBaselineResults() - Starting baseline capture");
    if (DEBUG) {
      LOG.fine("Capturing baseline results for research mode");
    }
    
    try {
      final List<TestUnit> allTests = testSource.getAllTests();
      System.out.println("DEBUG: Found " + allTests.size() + " tests for baseline capture");
      this.baselineResults = new HashMap<>();
      final CheckTestHasFailedResultListener baselineListener = new CheckTestHasFailedResultListener(true);
      final Container c = createNewContainer();
      final Pitest baselinePit = new Pitest(baselineListener);
      
      System.out.println("DEBUG: Running baseline tests...");
      baselinePit.run(c, allTests);
      System.out.println("DEBUG: Baseline test execution completed");
      
      // Record baseline results
      for (Description passingTest : baselineListener.getSucceedingTests()) {
        this.baselineResults.put(passingTest.getQualifiedName(), true);
        System.out.println("DEBUG: Baseline test passed: " + passingTest.getQualifiedName());
        if (DEBUG) {
          LOG.fine("Baseline test passed: " + passingTest.getQualifiedName());
        }
      }
      for (Description failingTest : baselineListener.getFailingTests()) {
        this.baselineResults.put(failingTest.getQualifiedName(), false);
        System.out.println("DEBUG: Baseline test failed: " + failingTest.getQualifiedName());
        if (DEBUG) {
          LOG.fine("Baseline test failed: " + failingTest.getQualifiedName());
        }
      }
      
      // Store baseline results in file-based holder for CSV reporter access
      BaselineResultsHolder.setBaselineResults(this.baselineResults);
      
      // Also store in file-based holder for cross-thread access
      BaselineResultsFileHolder.storeBaselineResults(this.baselineResults);
      
      // Debug: Verify baseline results are stored
      System.out.println("DEBUG: Stored baseline results in holder, size: " + this.baselineResults.size());
      System.out.println("DEBUG: Sample stored results: " + this.baselineResults.entrySet().stream().limit(3).collect(java.util.stream.Collectors.toList()));
      System.out.println("DEBUG: Verified holder has results: " + BaselineResultsHolder.hasBaselineResults());
      
      if (DEBUG) {
        LOG.fine("Captured baseline: " + this.baselineResults.size() + " tests, "
                + baselineListener.getSucceedingTests().size() + " passing, "
                + baselineListener.getFailingTests().size() + " failing");
      }
    } catch (final Exception ex) {
      throw translateCheckedException(ex);
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
      final BaselineAwareMutationTestListener baselineListener = new BaselineAwareMutationTestListener(this.baselineResults);
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

    return new MutationStatusTestPair(listener.getNumberOfTestsRun(),
        listener.getOverallStatus(), killingTests, survivingTests, coveredTests);
  }

}
