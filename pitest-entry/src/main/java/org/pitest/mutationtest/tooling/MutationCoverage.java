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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expres        // Store baseline results in both holders for access by child processes and CSV reporter
        BaselineResultsHolder.setBaselineResults(baselineResults);
        BaselineResultsFileHolder.storeBaselineResults(baselineResults);
        
        LOG.info("Captured baseline results for " + baselineResults.size() + " tests");
        LOG.info("Found " + failingTestNames.size() + " failing tests: " + failingTestNames);
        LOG.info("Found " + failingTestLines.size() + " lines covered by failing tests: " + failingTestLines);
        LOG.info("Failing test lines by class: " + failingTestLinesByClass);ed.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.tooling;

import org.pitest.classinfo.CachingByteArraySource;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.ClassPathByteArraySource;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.classpath.CodeSource;
import org.pitest.coverage.CoverageDatabase;
import org.pitest.coverage.CoverageData;
import org.pitest.coverage.CoverageGenerator;
import org.pitest.coverage.CoverageSummary;
import org.pitest.coverage.BlockCoverage;
import org.pitest.coverage.BlockLocation;
import org.pitest.coverage.TestInfo;
import org.pitest.coverage.ClassLine;
import org.pitest.coverage.NoCoverage;
import org.pitest.coverage.ReportCoverage;
import org.pitest.help.Help;
import org.pitest.help.PitHelpError;
import org.pitest.mutationtest.History;
import org.pitest.mutationtest.EngineArguments;
import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationConfig;
import org.pitest.mutationtest.MutationResultInterceptor;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.build.MutationAnalysisUnit;
import org.pitest.mutationtest.build.MutationGrouper;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.build.MutationSource;
import org.pitest.mutationtest.build.MutationTestBuilder;
import org.pitest.mutationtest.build.PercentAndConstantTimeoutStrategy;
import org.pitest.mutationtest.build.TestPrioritiser;
import org.pitest.mutationtest.build.WorkerFactory;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.config.SettingsFactory;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationEngine;
import org.pitest.mutationtest.execute.MutationAnalysisExecutor;
import org.pitest.mutationtest.incremental.HistoryListener;
import org.pitest.mutationtest.incremental.NullHistory;
import org.pitest.mutationtest.statistics.MutationStatistics;
import org.pitest.mutationtest.statistics.MutationStatisticsListener;
import org.pitest.mutationtest.statistics.Score;
import org.pitest.mutationtest.verify.BuildMessage;
import org.pitest.mutationtest.execute.BaselineResultsHolder;
import org.pitest.mutationtest.execute.BaselineResultsFileHolder;
import org.pitest.util.Log;
import org.pitest.util.StringUtil;
import org.pitest.util.Timings;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class MutationCoverage {

  private static final int         MB  = 1024 * 1024;

  private static final Logger      LOG = Log.getLogger();
  private final ReportOptions      data;

  private final MutationStrategies strategies;
  private final Timings            timings;
  private final CodeSource         code;
  private final File               baseDir;
  private final SettingsFactory    settings;

  public MutationCoverage(final MutationStrategies strategies,
      final File baseDir, final CodeSource code, final ReportOptions data,
      final SettingsFactory settings, final Timings timings) {
    this.strategies = strategies;
    this.data = data;
    this.settings = settings;
    this.timings = timings;
    this.code = code;
    this.baseDir = baseDir;
  }

  public CombinedStatistics runReport() throws IOException {

    if (!this.data.getVerbosity().showMinionOutput()) {
      LOG.info("Verbose logging is disabled. If you encounter a problem, please enable it before reporting an issue.");
    }
    Log.setVerbose(this.data.getVerbosity());

    final Runtime runtime = Runtime.getRuntime();

    LOG.fine("Running report with " + this.data);

    LOG.fine("System class path is " + System.getProperty("java.class.path"));
    LOG.fine("Maximum available memory is " + (runtime.maxMemory() / MB)
        + " mb");

    final long t0 = System.nanoTime();

    List<BuildMessage> issues = verifyBuildSuitableForMutationTesting();

    checkExcludedRunners();

    final EngineArguments args = EngineArguments.arguments()
            .withExcludedMethods(this.data.getExcludedMethods())
            .withMutators(this.data.getMutators());
    final MutationEngine engine = this.strategies.factory().createEngine(args);

    List<MutationAnalysisUnit> preScanMutations = findMutations(engine, args);
    LOG.info("Created " + preScanMutations.size() + " mutation test units in pre scan");

    // throw error if configured to do so
    checkMutationsFound(preScanMutations);

    if (preScanMutations.isEmpty()) {
      LOG.info("Skipping coverage and analysis as no mutations found" );
      return emptyStatistics();
    }

    // Extract mutants from analysis units. Advanced history implementations can
    // use this data to reduce the number of tests run during coverage.
    // This is list of mutants is potentially larger than the one finally
    // assessed, as no filters have been run across it.
    List<MutationDetails> unfilteredMutants = preScanMutations.stream()
            .flatMap(unit -> unit.mutants().stream())
            .collect(Collectors.toList());

    return runAnalysis(runtime, t0, args, engine, issues, unfilteredMutants);

  }

  private CombinedStatistics emptyStatistics() {
    MutationStatistics mutationStatistics = new MutationStatistics(emptyList(),0,0,0,0, emptySet());
    return new CombinedStatistics(mutationStatistics, new CoverageSummary(0,0, 0), Collections.emptyList());
  }

  private CombinedStatistics runAnalysis(Runtime runtime, long t0, EngineArguments args, MutationEngine engine, List<BuildMessage> issues, List<MutationDetails> unfilteredMutants) {
    History history = this.strategies.history();
    history.initialize();

    CoverageDatabase coverageData = coverage().calculateCoverage(history.limitTests(unfilteredMutants));
    history.processCoverage(coverageData);

    LOG.fine("Used memory after coverage calculation "
        + ((runtime.totalMemory() - runtime.freeMemory()) / MB) + " mb");
    LOG.fine("Free Memory after coverage calculation "
        + (runtime.freeMemory() / MB) + " mb");

    // Capture baseline results in main process for fullMatrixResearchMode
    if (this.data.isFullMatrixResearchMode()) {
      captureBaselineResultsInMainProcess(coverageData);
    }

    this.timings.registerStart(Timings.Stage.BUILD_MUTATION_TESTS);
    final List<MutationAnalysisUnit> tus = buildMutationTests(coverageData, history,
            engine, args, allInterceptors());
    this.timings.registerEnd(Timings.Stage.BUILD_MUTATION_TESTS);

    LOG.info("Created " + tus.size() + " mutation test units" );

    LOG.fine("Used memory before analysis start "
        + ((runtime.totalMemory() - runtime.freeMemory()) / MB) + " mb");
    LOG.fine("Free Memory before analysis start " + (runtime.freeMemory() / MB)
        + " mb");

    ReportCoverage modifiedCoverage = transformCoverage(coverageData);
    final MutationStatisticsListener stats = new MutationStatisticsListener();
    final List<MutationResultListener> config = createConfig(t0, modifiedCoverage, history,
                stats, engine, issues);

    final MutationAnalysisExecutor mae = new MutationAnalysisExecutor(
        numberOfThreads(), resultInterceptor(), config);
    this.timings.registerStart(Timings.Stage.RUN_MUTATION_TESTS);
    mae.run(tus);
    this.timings.registerEnd(Timings.Stage.RUN_MUTATION_TESTS);

    LOG.info("Completed in " + timeSpan(t0));

    MutationStatistics mutationStats = stats.getStatistics();
    CombinedStatistics combined = new CombinedStatistics(mutationStats,
            createSummary(coverageData.testCount(), modifiedCoverage, mutationStats.mutatedClasses()), issues);

    printStats(combined);

    return combined;
  }

  private ReportCoverage transformCoverage(ReportCoverage coverageData) {
    // cosmetic changes to coverage are made only after tests are assigned to
    // mutants to ensure they cannot affect results.
    return strategies.coverageTransformer().transform(coverageData);
  }

  private CoverageSummary createSummary(int numberOfTests, ReportCoverage modifiedCoverage, Set<ClassName> mutatedClasses) {
    List<ClassName> examinedClasses = this.code.getCodeUnderTestNames().stream()
            .filter(mutatedClasses::contains)
            .collect(Collectors.toList());

    int numberOfCodeLines = examinedClasses.stream()
            .map(c -> modifiedCoverage.getCodeLinesForClass(c))
            .map(c -> c.getNumberOfCodeLines())
            .reduce(0, Integer::sum);

    int coveredLines = examinedClasses.stream()
            .mapToInt(c -> modifiedCoverage.getCoveredLines(c).size())
            .sum();

    return new CoverageSummary(numberOfCodeLines, coveredLines, numberOfTests);
  }

  private Predicate<MutationInterceptor> allInterceptors() {
    return i -> true;
  }

  private List<MutationAnalysisUnit> findMutations(MutationEngine engine, EngineArguments args) {
    // Run mutant discovery without coverage data or history.
    // Ideally we'd ony discover mutants once, but the process is currently tightly
    // entangled with coverage data. Generating coverage data is expensive for
    // some projects, while discovery usually takes less than 1 second. By doing
    // an initial run here we are able to skip coverage generation when no mutants
    // are found, e.g if pitest is being run against diffs.
    this.timings.registerStart(Timings.Stage.MUTATION_PRE_SCAN);
    List<MutationAnalysisUnit> mutants = buildMutationTests(new NoCoverage(), new NullHistory(), engine, args, noReportsOrFilters());
    this.timings.registerEnd(Timings.Stage.MUTATION_PRE_SCAN);
    return mutants;
  }

  private Predicate<MutationInterceptor> noReportsOrFilters() {
    return i -> i.type().includeInPrescan();
  }


  private void checkExcludedRunners() {
    final Collection<String> excludedRunners = this.data.getExcludedRunners();
    if (!excludedRunners.isEmpty()) {
      // Check whether JUnit4 is available or not
      try {
        Class.forName("org.junit.runner.RunWith");
      } catch (final ClassNotFoundException e) {
        // JUnit4 is not available on the classpath
        throw new PitHelpError(Help.NO_JUNIT_EXCLUDE_RUNNERS);
      }
    }
  }

  private int numberOfThreads() {
    return Math.max(1, this.data.getNumberOfThreads());
  }

  private List<MutationResultListener> createConfig(long t0,
                                                    ReportCoverage coverageData,
                                                    History history,
                                                    MutationStatisticsListener stats,
                                                    MutationEngine engine, List<BuildMessage> issues) {
    final List<MutationResultListener> ls = new ArrayList<>();

    ls.add(stats);

    final ListenerArguments args = new ListenerArguments(
        this.strategies.output(), coverageData, new SmartSourceLocator(
            data.getSourcePaths(), this.data.getInputEncoding()), engine, t0, this.data.isFullMutationMatrix(), data, issues);

    final MutationResultListener mutationReportListener = this.strategies
        .listenerFactory().getListener(this.data.getFreeFormProperties(), args);

    ls.add(mutationReportListener);
    ls.add(new HistoryListener(history));

    if (this.data.getVerbosity().showSpinner()) {
      ls.add(new SpinnerListener(System.out));
    }
    return ls;
  }

  private MutationResultInterceptor resultInterceptor() {
    return this.strategies.resultInterceptor();
  }

  private List<BuildMessage> verifyBuildSuitableForMutationTesting() {
    return this.strategies.buildVerifier().verifyBuild();
  }

  private void printStats(CombinedStatistics combinedStatistics) {
    MutationStatistics stats = combinedStatistics.getMutationStatistics();
    final PrintStream ps = System.out;

    ps.println(StringUtil.separatorLine('='));
    ps.println("- Mutators");
    ps.println(StringUtil.separatorLine('='));
    for (final Score each : stats.getScores()) {
      each.report(ps);
      ps.println(StringUtil.separatorLine());
    }

    ps.println(StringUtil.separatorLine('='));
    ps.println("- Timings");
    ps.println(StringUtil.separatorLine('='));
    this.timings.report(ps);

    ps.println(StringUtil.separatorLine('='));
    ps.println("- Statistics");
    ps.println(StringUtil.separatorLine('='));

    final CoverageSummary coverage = combinedStatistics.getCoverageSummary();
    if (coverage != null) {
      ps.println(String.format(">> Line Coverage (for mutated classes only): %d/%d (%d%%)", coverage.getNumberOfCoveredLines(),
              coverage.getNumberOfLines(), coverage.getCoverage()));
      ps.println(String.format(">> %d tests examined", coverage.getNumberOfTests()));
    }

    stats.report(ps);

    if (!combinedStatistics.getIssues().isEmpty()) {
      ps.println();
      ps.println("Build messages:- ");
      combinedStatistics.getIssues().forEach(m -> ps.println("* " + m));
    }
  }

  private List<MutationAnalysisUnit> buildMutationTests(CoverageDatabase coverageData,
                                                        History history,
                                                        MutationEngine engine,
                                                        EngineArguments args,
                                                        Predicate<MutationInterceptor> interceptorFilter) {

    final MutationConfig mutationConfig = new MutationConfig(engine, coverage()
        .getLaunchOptions());

    final ClassByteArraySource bas = new CachingByteArraySource(fallbackToClassLoader(new ClassPathByteArraySource(
        this.data.getClassPath())), 200);

    final TestPrioritiser testPrioritiser = this.settings.getTestPrioritiser()
        .makeTestPrioritiser(this.data.getFreeFormProperties(), this.code,
            coverageData);

    final MutationInterceptor interceptor = this.settings.getInterceptor()
            .createInterceptor(this.data, coverageData, bas, testPrioritiser, code)
            .filter(interceptorFilter);

    interceptor.initialise(this.code);

    final MutationSource source = new MutationSource(mutationConfig, testPrioritiser, bas, interceptor);


    final WorkerFactory wf = new WorkerFactory(this.baseDir, coverage()
        .getConfiguration(), mutationConfig, args,
        new PercentAndConstantTimeoutStrategy(this.data.getTimeoutFactor(),
            this.data.getTimeoutConstant()), this.data.getVerbosity(), this.data.isFullMutationMatrix(),
            this.data.isFullMatrixResearchMode(), this.data.getClassPath().getLocalClassPath());

    final MutationGrouper grouper = this.settings.getMutationGrouper().makeFactory(
        this.data.getFreeFormProperties(), this.code,
        this.data.getNumberOfThreads(), this.data.getMutationUnitSize());

    final MutationTestBuilder builder = new MutationTestBuilder(data.mode(), wf, history,
        source, grouper, this.code);

    return builder.createMutationTestUnits(this.code.getCodeUnderTestNames());
  }
  private void checkMutationsFound(final List<MutationAnalysisUnit> tus) {
    if (tus.isEmpty()) {
      if (this.data.shouldFailWhenNoMutations()) {
        throw new PitHelpError(Help.NO_MUTATIONS_FOUND);
      } else {
        LOG.warning(Help.NO_MUTATIONS_FOUND.toString());
      }
    }
  }

  /**
   * Capture baseline test results in the main process for fullMatrixResearchMode.
   * This extracts test pass/fail status from the coverage data that was just calculated.
   * Records which lines are covered by failing tests and which lines are covered by passing tests
   * for mutation filtering and analysis.
   */
  private void captureBaselineResultsInMainProcess(CoverageDatabase coverageData) {
    try {
      LOG.info("Capturing baseline test results for fullMatrixResearchMode");
      
      Map<String, Boolean> baselineResults = new HashMap<>();
      Set<String> failingTestNames = new HashSet<>();
      Set<String> passingTestNames = new HashSet<>();
      Set<Integer> failingTestLines = new HashSet<>();
      Set<Integer> passingTestLines = new HashSet<>();
      Map<String, Set<Integer>> failingTestLinesByClass = new HashMap<>();
      Map<String, Set<Integer>> passingTestLinesByClass = new HashMap<>();
      Set<String> failingTestClassLines = new HashSet<>();
      Set<String> passingTestClassLines = new HashSet<>();
      
      // Access the CoverageData object to get test results
      if (coverageData instanceof CoverageData) {
        CoverageData cd = (CoverageData) coverageData;
        
        // First, collect all unique tests from all block coverage
        Set<TestInfo> allTests = new HashSet<>();
        for (BlockCoverage blockCov : cd.createCoverage()) {
          Collection<TestInfo> testsForBlock = cd.getTestsForBlockLocation(blockCov.getBlock());
          allTests.addAll(testsForBlock);
        }
        
        // Get failing test names from the failing test descriptions
        Set<String> failingTestDescs = cd.getFailingTestDescriptions().stream()
            .map(desc -> desc.getQualifiedName())
            .collect(java.util.stream.Collectors.toSet());
        
        LOG.info("Found failing test descriptions: " + failingTestDescs);
        
        // Get failing test names
        for (TestInfo testInfo : allTests) {
          String testName = testInfo.getName();
          
          // A test is considered failed if it's in the failing test descriptions
          boolean testPassed = !cd.getFailingTestDescriptions().stream()
              .anyMatch(desc -> desc.getQualifiedName().equals(testName));
          
          baselineResults.put(testName, testPassed);
          
          if (!testPassed) {
            failingTestNames.add(testName);
            LOG.info("Detected failing test: " + testName);
          } else {
            passingTestNames.add(testName);
            LOG.fine("Detected passing test: " + testName);
          }
        }
        
        // Now collect lines covered by failing and passing tests using block level coverage
        for (ClassName className : this.code.getCodeUnderTestNames()) {
          Set<ClassLine> coveredLines = cd.getCoveredLines(className);
          Set<Integer> failingLines = new HashSet<>();
          Set<Integer> passingLines = new HashSet<>();
          
          for (ClassLine line : coveredLines) {
            int lineNumber = line.getLineNumber();
            boolean coveredByFailingTest = false;
            boolean coveredByPassingTest = false;
            
            // Check all block coverage to find blocks that cover this specific line
            for (BlockCoverage blockCov : cd.createCoverage()) {
              BlockLocation blockLocation = blockCov.getBlock();
              
              // Only check blocks from the same class
              if (blockLocation.getLocation().getClassName().equals(className)) {
                // Get the lines covered by this block using reflection to access LegacyClassCoverage
                Set<Integer> blockLines = getBlockLines(cd, blockLocation);
                
                // Check if this block covers the current line
                if (blockLines.contains(lineNumber)) {
                  // Get the tests that cover this specific block
                  Collection<TestInfo> testsForBlock = cd.getTestsForBlockLocation(blockLocation);
                  
                  // Check if any of the tests for this block are failing tests
                  boolean blockCoveredByFailingTest = testsForBlock.stream()
                      .anyMatch(test -> failingTestDescs.contains(test.getName()));
                  
                  // Check if any of the tests for this block are passing tests
                  boolean blockCoveredByPassingTest = testsForBlock.stream()
                      .anyMatch(test -> !failingTestDescs.contains(test.getName()));
                  
                  if (blockCoveredByFailingTest) {
                    coveredByFailingTest = true;
                  }
                  
                  if (blockCoveredByPassingTest) {
                    coveredByPassingTest = true;
                  }
                  
                  // Continue checking all blocks to get complete coverage
                }
              }
            }
            
            if (coveredByFailingTest) {
              failingTestLines.add(lineNumber);
              failingLines.add(lineNumber);
              failingTestClassLines.add(className.asJavaName() + ":" + lineNumber);
              
              LOG.fine("Line " + lineNumber + " in class " + className + " is covered by failing test");
            }
            
            if (coveredByPassingTest) {
              passingTestLines.add(lineNumber);
              passingLines.add(lineNumber);
              passingTestClassLines.add(className.asJavaName() + ":" + lineNumber);
              
              LOG.fine("Line " + lineNumber + " in class " + className + " is covered by passing test");
            }
          }
          
          if (!failingLines.isEmpty()) {
            failingTestLinesByClass.put(className.asJavaName(), failingLines);
          }
          
          if (!passingLines.isEmpty()) {
            passingTestLinesByClass.put(className.asJavaName(), passingLines);
          }
        }
        
        // Store baseline results in both holders for access by child processes and CSV reporter
        BaselineResultsHolder.setBaselineResults(baselineResults);
        BaselineResultsFileHolder.storeBaselineResults(baselineResults);
        
        LOG.info("Captured baseline results for " + baselineResults.size() + " tests");
        LOG.info("Found " + failingTestNames.size() + " failing tests");
        LOG.info("Found " + passingTestNames.size() + " passing tests");
        LOG.info("Found " + failingTestLines.size() + " lines covered by failing tests");
        LOG.info("Found " + passingTestLines.size() + " lines covered by passing tests");
        LOG.info("Failing test lines by class: " + failingTestLinesByClass);
        LOG.info("Passing test lines by class: " + passingTestLinesByClass);
        LOG.info("Failing test class:lines (" + failingTestClassLines.size() + " total): " + failingTestClassLines);
        LOG.info("Passing test class:lines (" + passingTestClassLines.size() + " total): " + passingTestClassLines);
        
        // Save failing test lines data to BaselineResultsHolder for use by FailingTestCoverageFilter
        try {
            org.pitest.mutationtest.execute.BaselineResultsHolder.setFailingTestLines(failingTestClassLines);
            org.pitest.mutationtest.execute.BaselineResultsHolder.setFailingTestLinesByClass(failingTestLinesByClass);
            LOG.info("Saved failing test lines data to BaselineResultsHolder");
        } catch (Exception e) {
            LOG.warning("Failed to save failing test lines to BaselineResultsHolder: " + e.getMessage());
        }
        
      } else {
        LOG.warning("Coverage data is not a CoverageData instance, cannot extract test results");
      }
      
    } catch (Exception e) {
      LOG.warning("Failed to capture baseline results: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Helper method to get lines covered by a specific block.
   * Uses reflection to access the private getLinesForBlock method in LegacyClassCoverage.
   */
  private Set<Integer> getBlockLines(CoverageData cd, BlockLocation blockLocation) {
    try {
      // Access the legacyClassCoverage field
      java.lang.reflect.Field legacyField = CoverageData.class.getDeclaredField("legacyClassCoverage");
      legacyField.setAccessible(true);
      Object legacyClassCoverage = legacyField.get(cd);
      
      // Call the getLinesForBlock method
      java.lang.reflect.Method getLinesMethod = legacyClassCoverage.getClass().getDeclaredMethod("getLinesForBlock", BlockLocation.class);
      getLinesMethod.setAccessible(true);
      
      @SuppressWarnings("unchecked")
      Set<Integer> lines = (Set<Integer>) getLinesMethod.invoke(legacyClassCoverage, blockLocation);
      return lines != null ? lines : Collections.emptySet();
      
    } catch (Exception e) {
      LOG.fine("Could not access block lines for " + blockLocation + ": " + e.getMessage());
      return Collections.emptySet();
    }
  }

  private String timeSpan(final long t0) {
    return "" + (NANOSECONDS.toSeconds(System.nanoTime() - t0)) + " seconds";
  }

  private CoverageGenerator coverage() {
    return this.strategies.coverage();
  }

  // Since java 9 rt.jar is no longer on the classpath so jdk classes will not resolve from
  // the filesystem and must be pulled out via the classloader
  private ClassByteArraySource fallbackToClassLoader(final ClassByteArraySource bas) {
    final ClassByteArraySource clSource = ClassloaderByteArraySource.fromContext();
    return clazz -> {
      final Optional<byte[]> maybeBytes = bas.getBytes(clazz);
      if (maybeBytes.isPresent()) {
        return maybeBytes;
      }
      return clSource.getBytes(clazz);
    };
  }
}
