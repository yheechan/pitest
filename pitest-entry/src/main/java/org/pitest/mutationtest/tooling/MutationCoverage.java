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

    // Measure baseline test execution time for time estimation
    final long baselineStartTime = System.currentTimeMillis();
    CoverageDatabase coverageData = coverage().calculateCoverage(history.limitTests(unfilteredMutants));
    final long baselineEndTime = System.currentTimeMillis();
    final long baselineTestExecutionTime = baselineEndTime - baselineStartTime;
    
    history.processCoverage(coverageData);

    
    LOG.fine("Used memory after coverage calculation "
    + ((runtime.totalMemory() - runtime.freeMemory()) / MB) + " mb");
    LOG.fine("Free Memory after coverage calculation "
    + (runtime.freeMemory() / MB) + " mb");
    
    // Initialize testCaseMetadata
    Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> testCaseMetadata = new HashMap<>();
    
    // Capture baseline results in main process for fullMatrixResearchMode
    if (this.data.isFullMatrixResearchMode()) {
      // Capture both test case results and baseline results from coverage data in one pass
      testCaseMetadata = captureBaselineAndTestResultsFromCoverageData(coverageData);
      
      // Save original bytecode once in main process for all classes
      saveOriginalBytecodeInMainProcess();
    }

    this.timings.registerStart(Timings.Stage.BUILD_MUTATION_TESTS);
    final List<MutationAnalysisUnit> tus = buildMutationTests(coverageData, history,
            engine, args, allInterceptors(), testCaseMetadata);
    this.timings.registerEnd(Timings.Stage.BUILD_MUTATION_TESTS);

    // Assign unique mutation IDs after all filtering is complete
    assignMutantIds(tus);

    LOG.info("Created " + tus.size() + " mutation test units" );

    // If measureExpectedTime is enabled, estimate total time and exit
    if (this.data.isMeasureExpectedTime()) {
      // Calculate lines covered by failing tests for estimation metrics
      int linesCoveredByFailingTests = calculateLinesCoveredByFailingTests(coverageData);
      return estimateExpectedTime(tus, coverageData, baselineTestExecutionTime, unfilteredMutants.size(), linesCoveredByFailingTests);
    }

    LOG.fine("Used memory before analysis start "
        + ((runtime.totalMemory() - runtime.freeMemory()) / MB) + " mb");
    LOG.fine("Free Memory before analysis start " + (runtime.freeMemory() / MB)
        + " mb");

    ReportCoverage modifiedCoverage = transformCoverage(coverageData);
    final MutationStatisticsListener stats = new MutationStatisticsListener();
    final List<MutationResultListener> config = createConfig(t0, modifiedCoverage, history,
                stats, engine, issues, testCaseMetadata);

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
    List<MutationAnalysisUnit> mutants = buildMutationTests(new NoCoverage(), new NullHistory(), engine, args, noReportsOrFilters(), new HashMap<>());
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
                                                    MutationEngine engine, List<BuildMessage> issues,
                                                    Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> testCaseMetadata) {
    final List<MutationResultListener> ls = new ArrayList<>();

    ls.add(stats);

    final ListenerArguments args = new ListenerArguments(
        this.strategies.output(), coverageData, new SmartSourceLocator(
            data.getSourcePaths(), this.data.getInputEncoding()), engine, t0, this.data.isFullMutationMatrix(), data, issues, testCaseMetadata);

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
                                                        Predicate<MutationInterceptor> interceptorFilter,
                                                        Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> testCaseMetadata) {

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
            this.data.isFullMatrixResearchMode(), this.data.getClassPath().getLocalClassPath(), this.data.getReportDir(),
            testCaseMetadata);

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
   * Capture both baseline test results and test case results in a single pass for fullMatrixResearchMode.
   * This method efficiently extracts all necessary data from coverage in one iteration:
   * - Test pass/fail status and exception details for CSV output
   * - Line coverage information for mutation filtering
   * - Baseline results for child processes
   */
  private Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> captureBaselineAndTestResultsFromCoverageData(CoverageDatabase coverageData) {
    String reportDir = this.data.getReportDir();
    if (reportDir == null || reportDir.isEmpty()) {
      LOG.fine("No report directory configured, skipping baseline and test results capture");
      return new HashMap<>();
    }
    
    try {
      LOG.info("Capturing baseline test results and test case results for fullMatrixResearchMode");
      
      // Access the CoverageData object to get test results
      if (!(coverageData instanceof CoverageData)) {
        LOG.warning("Coverage data is not a CoverageData instance, cannot extract test results");
        createEmptyTestResultsCSV(reportDir);
        return new HashMap<>();
      }
      
      CoverageData cd = (CoverageData) coverageData;
      
      // Get all coverage results once to avoid multiple calls
      List<org.pitest.coverage.CoverageResult> allCoverageResults = cd.getAllCoverageResults();
      
      // === Single pass through coverage data to extract all test information ===
      
      // Collect all unique tests from coverage data
      Set<TestInfo> allTests = new HashSet<>();
      for (BlockCoverage blockCov : cd.createCoverage()) {
        Collection<TestInfo> testsForBlock = cd.getTestsForBlockLocation(blockCov.getBlock());
        allTests.addAll(testsForBlock);
      }
      
      if (allTests.isEmpty()) {
        LOG.warning("No test results found in coverage data");
        createEmptyTestResultsCSV(reportDir);
        return new HashMap<>();
      }
      
      // Get failing test descriptions and names
      Set<String> failingTestNames = cd.getFailingTestDescriptions().stream()
          .map(desc -> desc.getQualifiedName())
          .collect(java.util.stream.Collectors.toSet());
      
      // === Prepare data structures for baseline results ===
      Map<String, Boolean> baselineResults = new HashMap<>();
      Set<String> passingTestNames = new HashSet<>();
      Set<Integer> failingTestLines = new HashSet<>();
      Set<Integer> passingTestLines = new HashSet<>();
      Map<String, Set<Integer>> failingTestLinesByClass = new HashMap<>();
      Map<String, Set<Integer>> passingTestLinesByClass = new HashMap<>();
      Set<String> failingTestClassLines = new HashSet<>();
      Set<String> passingTestClassLines = new HashSet<>();
      
      // === Process each test to build baseline results ===
      for (TestInfo testInfo : allTests) {
        String testName = testInfo.getName();
        boolean testPassed = !failingTestNames.contains(testName);
        
        baselineResults.put(testName, testPassed);
        
        if (!testPassed) {
          LOG.info("Detected failing test: " + testName);
        } else {
          passingTestNames.add(testName);
          LOG.fine("Detected passing test: " + testName);
        }
      }
      
      // === Analyze line coverage by test status ===
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
              // Get the lines covered by this block
              Set<Integer> blockLines = getBlockLines(cd, blockLocation);
              
              // Check if this block covers the current line
              if (blockLines.contains(lineNumber)) {
                // Get the tests that cover this specific block
                Collection<TestInfo> testsForBlock = cd.getTestsForBlockLocation(blockLocation);
                
                // Check if any of the tests for this block are failing tests
                boolean blockCoveredByFailingTest = testsForBlock.stream()
                    .anyMatch(test -> failingTestNames.contains(test.getName()));
                
                // Check if any of the tests for this block are passing tests
                boolean blockCoveredByPassingTest = testsForBlock.stream()
                    .anyMatch(test -> !failingTestNames.contains(test.getName()));
                
                if (blockCoveredByFailingTest) {
                  coveredByFailingTest = true;
                }
                
                if (blockCoveredByPassingTest) {
                  coveredByPassingTest = true;
                }
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
      
      // === Store test case ID mapping and metadata ===
      Map<String, Integer> testCaseIdMapping = createTestCaseIdMapping(allTests);
      
      // Create complete test case metadata
      Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> completeMetadata = 
          createCompleteTestCaseMetadata(allTests, failingTestNames, testCaseIdMapping, allCoverageResults);
      
      // Save failing test lines data to BaselineResultsHolder for use by FailingTestCoverageFilter
      try {
          org.pitest.mutationtest.execute.BaselineResultsHolder.setFailingTestLines(failingTestClassLines);
          org.pitest.mutationtest.execute.BaselineResultsHolder.setFailingTestLinesByClass(failingTestLinesByClass);
          LOG.info("Saved failing test lines data to BaselineResultsHolder");
      } catch (Exception e) {
          LOG.warning("Failed to save failing test lines to BaselineResultsHolder: " + e.getMessage());
      }
      
      // === Write individual test result files in new format ===
      java.nio.file.Path baselineTestResultsDir = java.nio.file.Paths.get(reportDir, "baselineTestResults");
      java.nio.file.Files.createDirectories(baselineTestResultsDir);
      
      writeIndividualTestResultFiles(baselineTestResultsDir, allTests, failingTestNames, allCoverageResults, cd);
      
      // === Log summary ===
      LOG.info("Captured baseline results for " + baselineResults.size() + " tests");
      LOG.info("Found " + failingTestNames.size() + " failing tests");
      LOG.info("Found " + passingTestNames.size() + " passing tests");
      LOG.info("Found " + failingTestLines.size() + " lines covered by failing tests");
      LOG.info("Found " + passingTestLines.size() + " lines covered by passing tests");
      LOG.info("Failing test lines by class: " + failingTestLinesByClass);
      LOG.info("Passing test lines by class: " + passingTestLinesByClass);
      LOG.info("Successfully captured test case results to: " + baselineTestResultsDir);
      
      return completeMetadata;
      
    } catch (Exception e) {
      LOG.severe("Failed to capture baseline and test results: " + e.getMessage());
      e.printStackTrace();
      return new HashMap<>();
    }
  }
  
  /**
   * Create empty test results when no tests are found.
   */
  private void createEmptyTestResultsCSV(String reportDir) {
    try {
      // Create empty baselineTestResults directory
      java.nio.file.Path baselineTestResultsDir = java.nio.file.Paths.get(reportDir, "baselineTestResults");
      java.nio.file.Files.createDirectories(baselineTestResultsDir);
      LOG.info("Created empty baselineTestResults directory: " + baselineTestResultsDir);
    } catch (Exception e) {
      LOG.warning("Failed to create empty test results: " + e.getMessage());
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

  /**
   * Represents a line with its metadata for mapping to bit sequence.
   */
  private static class LineInfo {
    final String lineId;
    final String className;
    final String fileName;
    final String methodSignature;
    final int lineNumber;

    LineInfo(String className, String fileName, String methodSignature, int lineNumber) {
      this.lineId = className + ":" + lineNumber;
      this.className = className;
      this.fileName = fileName;
      this.methodSignature = methodSignature;
      this.lineNumber = lineNumber;
    }
    
    @Override
    public String toString() {
      return "LineInfo{lineId='" + lineId + "', className='" + className 
             + "', fileName='" + fileName + "', methodSignature='" + methodSignature 
             + "', lineNumber=" + lineNumber + "}";
    }
  }

  /**
   * Extract simple filename from fully qualified class name.
   */
  private String extractFileName(String className) {
    String simpleName = className.substring(className.lastIndexOf('.') + 1);
    return simpleName + ".java";
  }
  
  /**
   * Format method signature for display with full class name, method name, readable parameters, and line number.
   * Extracts the first line number from the method's instructions directly.
   * Example: org.apache.commons.lang3.math$NumberUtils#createNumber(java.lang.String):462
   */
  private String formatMethodSignature(org.pitest.bytecode.analysis.MethodTree method) {
    String methodName = method.rawNode().name;
    String className = method.asLocation().getClassName().asJavaName();
    String methodDesc = method.rawNode().desc;
    
    // Extract the first line number from method instructions
    int lineNumber = getFirstLineNumberFromMethod(method);
    
    // Use ASM's Type class to get readable parameter types
    String readableParameters = getReadableParametersFromDescriptor(methodDesc);
    
    // Format as: fully.qualified.ClassName#methodName(readableParameters):lineNumber
    return className + "#" + methodName + "(" + readableParameters + "):" + lineNumber;
  }
  
  /**
   * Extract the first line number from a method's instructions.
   * Returns -1 if no line number information is found.
   */
  private int getFirstLineNumberFromMethod(org.pitest.bytecode.analysis.MethodTree method) {
    for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions()) {
      if (insn instanceof org.objectweb.asm.tree.LineNumberNode) {
        return ((org.objectweb.asm.tree.LineNumberNode) insn).line;
      }
    }
    return -1; // No line number found
  }
  
  /**
   * Get readable parameter types from method descriptor using ASM's Type class.
   * Example: (Ljava/lang/String;I)V -> java.lang.String,int
   */
  private String getReadableParametersFromDescriptor(String descriptor) {
    if (descriptor == null || descriptor.isEmpty()) {
      return "";
    }
    
    try {
      org.objectweb.asm.Type methodType = org.objectweb.asm.Type.getMethodType(descriptor);
      org.objectweb.asm.Type[] argTypes = methodType.getArgumentTypes();
      
      List<String> paramTypes = new ArrayList<>();
      for (org.objectweb.asm.Type argType : argTypes) {
        paramTypes.add(argType.getClassName());
      }
      
      return String.join(",", paramTypes);
    } catch (Exception e) {
      LOG.fine("Failed to parse method descriptor: " + descriptor + " - " + e.getMessage());
      return "";
    }
  }
  
  /**
   * Get lines executed by failing tests for optimized processing.
   * This significantly reduces the scope of line coverage analysis.
   * Only includes lines from target classes (code under test), not test classes.
   */
  private Set<String> getLinesExecutedByFailingTests(CoverageData coverageData, Set<String> failingTestNames) {
    Set<String> linesExecutedByFailingTests = new HashSet<>();
    
    if (failingTestNames.isEmpty()) {
      LOG.info("No failing tests found - returning empty line set");
      return linesExecutedByFailingTests;
    }
    
    LOG.info("Analyzing line coverage for " + failingTestNames.size() + " failing tests");
    
    // Get target classes under test (exclude test classes)
    Collection<ClassName> targetClasses = this.code.getCodeUnderTestNames();
    Set<String> targetClassNames = targetClasses.stream()
        .map(ClassName::asJavaName)
        .collect(Collectors.toSet());
    
    // Build a map of line -> tests for efficient lookup
    for (BlockCoverage blockCov : coverageData.createCoverage()) {
      Collection<TestInfo> testsForBlock = coverageData.getTestsForBlockLocation(blockCov.getBlock());
      
      // Check if any failing test covers this block
      boolean blockCoveredByFailingTest = testsForBlock.stream()
          .anyMatch(test -> failingTestNames.contains(test.getName()));
      
      if (blockCoveredByFailingTest) {
        ClassName className = blockCov.getBlock().getLocation().getClassName();
        String classNameString = className.asJavaName();
        
        // Only include lines from target classes (code under test), not test classes
        if (targetClassNames.contains(classNameString)) {
          Set<Integer> blockLines = getBlockLines(coverageData, blockCov.getBlock());
          
          for (Integer lineNumber : blockLines) {
            linesExecutedByFailingTests.add(classNameString + ":" + lineNumber);
          }
        }
      }
    }
    
    LOG.info("Found " + linesExecutedByFailingTests.size() + " lines executed by failing tests");
    return linesExecutedByFailingTests;
  }

  /**
   * Get line info only for specific lines (much faster than scanning all lines).
   */
  private List<LineInfo> getLineInfoForSpecificLines(Set<String> targetLines) {
    List<LineInfo> lineInfos = new ArrayList<>();
    
    // Group lines by class for efficient processing
    Map<String, Set<Integer>> linesByClass = new HashMap<>();
    for (String lineId : targetLines) {
      String[] parts = lineId.split(":");
      if (parts.length == 2) {
        String className = parts[0];
        try {
          int lineNumber = Integer.parseInt(parts[1]);
          linesByClass.computeIfAbsent(className, k -> new HashSet<>()).add(lineNumber);
        } catch (NumberFormatException e) {
          LOG.fine("Invalid line number in: " + lineId);
        }
      }
    }
    
    // Process each class only once
    for (Map.Entry<String, Set<Integer>> entry : linesByClass.entrySet()) {
      String javaClassName = entry.getKey();
      Set<Integer> classLines = entry.getValue();
      
      try {
        ClassName className = ClassName.fromString(javaClassName.replace('.', '/'));
        Optional<byte[]> classBytes = this.code.fetchClassBytes(className);
        
        if (classBytes.isPresent()) {
          org.pitest.bytecode.analysis.ClassTree classTree = 
              org.pitest.bytecode.analysis.ClassTree.fromBytes(classBytes.get());
          
          String fileName = extractFileName(javaClassName);
          
          // Map line numbers to methods
          Map<Integer, String> lineToMethod = new HashMap<>();
          for (org.pitest.bytecode.analysis.MethodTree method : classTree.methods()) {
            if ((method.isBridge() || method.isSynthetic()) && !method.isGeneratedLambdaMethod()) {
              continue;
            }
            
            Set<Integer> methodLines = method.instructions().stream()
                .filter(n -> n instanceof org.objectweb.asm.tree.LineNumberNode)
                .map(n -> ((org.objectweb.asm.tree.LineNumberNode) n).line)
                .collect(java.util.stream.Collectors.toSet());
            
            for (Integer lineNumber : methodLines) {
              String methodSignature = formatMethodSignature(method);
              lineToMethod.put(lineNumber, methodSignature);
            }
          }
          
          // Create LineInfo objects only for target lines
          for (Integer lineNumber : classLines) {
            String methodSignature = lineToMethod.getOrDefault(lineNumber, "unknown");
            lineInfos.add(new LineInfo(javaClassName, fileName, methodSignature, lineNumber));
          }
        }
      } catch (Exception e) {
        LOG.fine("Failed to process class " + javaClassName + ": " + e.getMessage());
      }
    }
    
    // Sort by lineId for consistent ordering
    lineInfos.sort((a, b) -> a.lineId.compareTo(b.lineId));
    return lineInfos;
  }

  /**
   * Build a test-to-line coverage mapping for efficient bit sequence generation.
   * This pre-computes all coverage relationships to avoid repeated expensive lookups.
   */
  private Map<String, Set<String>> buildTestLineCoverageMap(List<TestInfo> allTests, CoverageData coverageData, Set<String> targetLines) {
    Map<String, Set<String>> testLineCoverageMap = new HashMap<>();
    
    LOG.info("Building test coverage map for " + allTests.size() + " tests and " + targetLines.size() + " target lines");
    
    // Initialize map
    for (TestInfo testInfo : allTests) {
      testLineCoverageMap.put(testInfo.getName(), new HashSet<>());
    }
    
    // Process coverage data once to build complete mapping
    for (BlockCoverage blockCov : coverageData.createCoverage()) {
      Collection<TestInfo> testsForBlock = coverageData.getTestsForBlockLocation(blockCov.getBlock());
      ClassName className = blockCov.getBlock().getLocation().getClassName();
      Set<Integer> blockLines = getBlockLines(coverageData, blockCov.getBlock());
      
      // Check which target lines are in this block
      Set<String> blockTargetLines = new HashSet<>();
      for (Integer lineNumber : blockLines) {
        String lineId = className.asJavaName() + ":" + lineNumber;
        if (targetLines.contains(lineId)) {
          blockTargetLines.add(lineId);
        }
      }
      
      // If this block contains target lines, map them to covering tests
      if (!blockTargetLines.isEmpty()) {
        for (TestInfo test : testsForBlock) {
          Set<String> testLines = testLineCoverageMap.get(test.getName());
          if (testLines != null) {
            testLines.addAll(blockTargetLines);
          }
        }
      }
    }
    
    LOG.info("Built test coverage map with coverage for " + testLineCoverageMap.values().stream().mapToInt(Set::size).sum() + " total test-line pairs");
    return testLineCoverageMap;
  }

  /**
   * Create optimized bit sequence using pre-computed coverage mapping.
   */
  private String createOptimizedLineCoverageBitSequence(String testName, List<String> targetLines, Map<String, Set<String>> testLineCoverageMap) {
    Set<String> coveredLinesByTest = testLineCoverageMap.getOrDefault(testName, Collections.emptySet());
    
    StringBuilder bitSequence = new StringBuilder();
    for (String line : targetLines) {
      bitSequence.append(coveredLinesByTest.contains(line) ? "1" : "0");
    }
    
    return bitSequence.toString();
  }

  /**
   * Write optimized line mapping CSV file for only the lines executed by failing tests.
   */
  private void writeOptimizedLineMappingCSV(java.nio.file.Path baselineTestResultsDir, List<LineInfo> failingTestLinesInfo) throws java.io.IOException {
    java.nio.file.Path lineMappingFile = baselineTestResultsDir.getParent().resolve("line_info.csv");
    
    try (java.io.PrintWriter csvWriter = new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(lineMappingFile,
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING))) {
      
      // Write CSV header
      csvWriter.println("line_id,code_filename,line_info");
      
      // Write each line mapping
      for (int i = 0; i < failingTestLinesInfo.size(); i++) {
        LineInfo lineInfo = failingTestLinesInfo.get(i);
        csvWriter.printf("%d,%s,%s%n", 
            i,
            escapeCsvValue(lineInfo.fileName),
            escapeCsvValue(lineInfo.methodSignature));
      }
    }
    
    LOG.info("Created optimized line mapping CSV: " + lineMappingFile + " with " + failingTestLinesInfo.size() + " lines (failing-test-executed only)");
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

  /**
   * Save original bytecode for all classes under test in the main process.
   * This is more efficient than having each worker save original bytecode independently.
   * Only saves if reportDir is configured for research mode.
   */
  private void saveOriginalBytecodeInMainProcess() {
    String reportDir = this.data.getReportDir();
    if (reportDir == null || reportDir.isEmpty()) {
      LOG.fine("No report directory configured, skipping original bytecode saving");
      return;
    }
    
    try {
      LOG.info("Saving original bytecode for all classes under test in main process");
      
      // Get all classes under test
      Collection<ClassName> classesUnderTest = this.code.getCodeUnderTestNames();
      LOG.info("Found " + classesUnderTest.size() + " classes under test to save original bytecode");
      
      for (ClassName className : classesUnderTest) {
        saveOriginalBytecodeForClass(className, reportDir);
      }
      
      LOG.info("Completed saving original bytecode for " + classesUnderTest.size() + " classes");
      
    } catch (Exception e) {
      LOG.warning("Failed to save original bytecode in main process: " + e.getMessage());
      if (LOG.isLoggable(java.util.logging.Level.FINE)) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Save original bytecode for a single class.
   */
  private void saveOriginalBytecodeForClass(ClassName className, String reportDir) {
    final String classNameStr = className.asJavaName();
    
    try {
      // Get the original bytecode from the class loader
      final String classInternalName = className.asInternalName();
      final String classResourcePath = classInternalName + ".class";
      
      // Try to load the original class bytecode
      byte[] originalBytecode = null;
      
      // Try multiple ways to get the original bytecode
      ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
      if (contextLoader != null) {
        try (java.io.InputStream classStream = contextLoader.getResourceAsStream(classResourcePath)) {
          if (classStream != null) {
            originalBytecode = classStream.readAllBytes();
          }
        } catch (final Exception ex) {
          // Try system class loader
          try (java.io.InputStream classStream = ClassLoader.getSystemResourceAsStream(classResourcePath)) {
            if (classStream != null) {
              originalBytecode = classStream.readAllBytes();
            }
          } catch (final Exception ex2) {
            LOG.fine("Could not load original bytecode for " + classNameStr + ": " + ex2.getMessage());
          }
        }
      }
      
      if (originalBytecode != null) {
        // Create the full package directory structure for the original class
        final String packagePath = classNameStr.contains(".") 
          ? classNameStr.substring(0, classNameStr.lastIndexOf('.')).replace('.', java.io.File.separatorChar) : "";
        final String simpleClassName = classNameStr.substring(classNameStr.lastIndexOf('.') + 1);
        
        // Save original bytecode in the main report directory with package structure
        final java.nio.file.Path originalClassDir = packagePath.isEmpty()
            ? java.nio.file.Paths.get(reportDir, "original")
            : java.nio.file.Paths.get(reportDir, "original", packagePath);
        java.nio.file.Files.createDirectories(originalClassDir);
        
        final String originalFilename = "ORIGINAL_" + simpleClassName + ".class";
        final java.nio.file.Path originalBytecodeFile = originalClassDir.resolve(originalFilename);
        
        java.nio.file.Files.write(originalBytecodeFile, originalBytecode, 
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        
        // Create a metadata file for the original class
        final String originalMetadataFilename = "ORIGINAL_" + simpleClassName + ".info";
        final java.nio.file.Path originalMetadataFile = originalClassDir.resolve(originalMetadataFilename);
        final String originalMetadata = String.format(
            "Original Class: %s%n"
            + "Bytecode Size: %d bytes%n"
            + "Saved From: %s%n"
            + "Package Path: %s%n"
            + "Saved To: %s%n"
            + "Purpose: Baseline for mutation comparison%n"
            + "Saved By: Main process (MutationCoverage)%n"
            + "%n"
            + "This is the original (unmutated) version of the class.%n"
            + "Compare with mutated versions to see the exact changes made.%n"
            + "%n"
            + "To decompile this original bytecode:%n"
            + "  javap -c -p %s%n"
            + "  Or use any Java decompiler like CFR, Fernflower, or JD-GUI%n"
            + "%n"
            + "To compare original vs mutated (example):%n"
            + "  diff <(javap -c %s) <(javap -c /path/to/mutant/Line_X_Index_Y_Mutator.class)%n",
            classNameStr,
            originalBytecode.length,
            classResourcePath,
            packagePath.isEmpty() ? "(default package)" : packagePath,
            originalBytecodeFile.toString(),
            originalFilename,
            originalFilename
        );
        java.nio.file.Files.write(originalMetadataFile, originalMetadata.getBytes(), 
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        
        LOG.fine("Saved original bytecode for " + classNameStr + " to " + originalBytecodeFile);
        
      } else {
        LOG.fine("Could not find original bytecode for class " + classNameStr);
      }
      
    } catch (final Exception ex) {
      LOG.fine("Failed to save original bytecode for " + classNameStr + ": " + ex.getMessage());
    }
  }
  
  
  /**
   * Escape CSV values to handle commas, quotes, and newlines.
   */
  private String escapeCsvValue(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    
    // If value contains comma, quote, or newline, wrap in quotes and escape internal quotes
    if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    
    return value;
  }

  /**
   * Create a consistent test case ID mapping based on sorted test names.
   * This ensures the same tcID is assigned to each test across all runs.
   */
  private Map<String, Integer> createTestCaseIdMapping(Set<TestInfo> allTests) {
    // Sort tests by name to ensure consistent ordering
    List<TestInfo> sortedTests = allTests.stream()
        .sorted((t1, t2) -> t1.getName().compareTo(t2.getName()))
        .collect(java.util.stream.Collectors.toList());
    
    Map<String, Integer> mapping = new HashMap<>();
    for (int tcID = 0; tcID < sortedTests.size(); tcID++) {
      String testName = sortedTests.get(tcID).getName();
      mapping.put(testName, tcID);
    }
    
    LOG.info("Created test case ID mapping for " + mapping.size() + " tests");
    return mapping;
  }
  
  /**
   * Create complete test case metadata from coverage data.
   * This combines all the information needed by mutation minions into a single structure.
   */
  private Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> createCompleteTestCaseMetadata(
      Set<TestInfo> allTests, Set<String> failingTestNames, Map<String, Integer> testCaseIdMapping, List<org.pitest.coverage.CoverageResult> allCoverageResults) {
    
    Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> metadata = new HashMap<>();
    
    LOG.info("Creating complete test case metadata for " + allTests.size() + " tests");
    
    for (TestInfo testInfo : allTests) {
      String testName = testInfo.getName();
      Integer tcID = testCaseIdMapping.get(testName);
      boolean baselinePassed = !failingTestNames.contains(testName);
      double executionTimeMs = testInfo.getTime();
      
      String exceptionType = "None";
      String exceptionMessage = "None";
      String stackTrace = "None";
      
      // Extract exception details from coverage data if test failed
      if (!baselinePassed) {
        // Find the corresponding CoverageResult for this test
        for (org.pitest.coverage.CoverageResult cr : allCoverageResults) {
          if (cr.getTestUnitDescription().getQualifiedName().equals(testName)) {
            if (cr.getExceptionType() != null && !cr.getExceptionType().isEmpty() && !"None".equals(cr.getExceptionType())) {
              exceptionType = cr.getExceptionType();
            }
            
            if (cr.getExceptionMessage() != null && !cr.getExceptionMessage().isEmpty() && !"None".equals(cr.getExceptionMessage())) {
              exceptionMessage = cr.getExceptionMessage();
            }
            
            if (cr.getStackTrace() != null && !cr.getStackTrace().isEmpty() && !"None".equals(cr.getStackTrace())) {
              // Filter the stack trace to remove infrastructure differences
              stackTrace = org.pitest.mutationtest.execute.DetailedMutationTestResult.getRelevantStackTrace(cr.getStackTrace());
            }
            break;
          }
        }
      }
      
      // Create the complete metadata object
      org.pitest.mutationtest.execute.TestCaseMetadata tcMetadata = 
          new org.pitest.mutationtest.execute.TestCaseMetadata(
              tcID != null ? tcID : -1, testName, baselinePassed, 
              exceptionType, exceptionMessage, stackTrace, executionTimeMs);
      
      metadata.put(testName, tcMetadata);
      
      if (LOG.isLoggable(java.util.logging.Level.FINE)) {
        LOG.fine("Created metadata for test " + testName + ": tcID=" + tcID + ", passed=" + baselinePassed 
                + ", executionTime=" + String.format("%.2f", executionTimeMs) + "ms");
      }
    }
    
    LOG.info("Created complete test case metadata for " + metadata.size() + " tests");
    return metadata;
  }
  
  /**
   * Write individual test result files in the mutation results format
   */
  private void writeIndividualTestResultFiles(java.nio.file.Path baselineTestResultsDir, Set<TestInfo> allTests, 
                                            Set<String> failingTestNames, List<org.pitest.coverage.CoverageResult> allCoverageResults, CoverageData coverageData) throws java.io.IOException {
    // Sort tests by name to ensure consistent ordering across runs
    List<TestInfo> sortedTests = allTests.stream()
        .sorted((t1, t2) -> t1.getName().compareTo(t2.getName()))
        .collect(java.util.stream.Collectors.toList());
    
    LOG.info("Writing individual test result files for " + sortedTests.size() + " tests");
    
    // OPTIMIZATION: Only generate bit sequences for lines executed by failing tests
    // This dramatically reduces processing time by focusing only on relevant lines.
    // Previously: O(tests * all_lines_in_codebase * blocks_per_line)
    // Now: O(tests * lines_executed_by_failing_tests) with pre-computed mappings
    LOG.info("Identifying lines executed by failing tests for optimized bit sequence generation");
    Set<String> linesExecutedByFailingTests = getLinesExecutedByFailingTests(coverageData, failingTestNames);
    LOG.info("Found " + linesExecutedByFailingTests.size() + " lines executed by failing tests (vs all lines in codebase)");
    
    // Create optimized line info only for lines executed by failing tests
    List<LineInfo> failingTestLinesInfo = getLineInfoForSpecificLines(linesExecutedByFailingTests);
    List<String> failingTestLines = failingTestLinesInfo.stream()
        .map(lineInfo -> lineInfo.lineId)
        .collect(java.util.stream.Collectors.toList());
    
    // Pre-compute test-to-line coverage mapping for efficient bit sequence generation
    LOG.info("Pre-computing test coverage mappings for " + sortedTests.size() + " tests");
    Map<String, Set<String>> testLineCoverageMap = buildTestLineCoverageMap(sortedTests, coverageData, linesExecutedByFailingTests);

    // Write the tcs_outcome.csv file in the refactored directory
    java.nio.file.Path outcomeFile = baselineTestResultsDir.resolve("tcs_outcome.csv");
    try (java.io.PrintWriter csvWriter = new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(outcomeFile))) {
      // Write CSV header
      csvWriter.println("tcID,test_name,result,execution_time_ms");
      
      // Write individual test result files and CSV entries
      for (int tcID = 0; tcID < sortedTests.size(); tcID++) {
        TestInfo testInfo = sortedTests.get(tcID);
        String testName = testInfo.getName();
        boolean passed = !failingTestNames.contains(testName);
        double durationMillis = testInfo.getTime();
        String result = passed ? "PASS" : "FAIL";
        
        // Write CSV entry
        csvWriter.printf("%d,%s,%s,%.2f%n", 
            tcID,
            escapeCsvValue(testName),
            result,
            durationMillis);
        
        // Create optimized bit sequence using pre-computed mapping
        String bitSequence = createOptimizedLineCoverageBitSequence(testName, failingTestLines, testLineCoverageMap);
        
        // Write individual test result file
        writeBaselineTestResultFile(baselineTestResultsDir, tcID, testName, result, durationMillis, allCoverageResults, bitSequence);
        
        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
          LOG.fine("Created individual test result file for tcID=" + tcID + ": " + testName + " = " + result);
        }
      }
    }
    
    // Write the line mapping CSV file that maps bit sequence positions to actual code lines (only failing test lines)
    writeOptimizedLineMappingCSV(baselineTestResultsDir, failingTestLinesInfo);
    
    LOG.info("Created " + sortedTests.size() + " individual test result files and tcs_outcome.csv in: " + baselineTestResultsDir);
  }
  
  /**
   * Write a single test result file in the mutation results format
   */
  private void writeBaselineTestResultFile(java.nio.file.Path baselineTestResultsDir, int tcID, String testName, 
                                         String result, double durationMillis, List<org.pitest.coverage.CoverageResult> allCoverageResults, String bitSequence) throws java.io.IOException {
    
    // Find the corresponding CoverageResult for this test
    String exceptionType = "None";
    String exceptionMessage = "None";
    String stackTrace = "None";
    
    for (org.pitest.coverage.CoverageResult cr : allCoverageResults) {
      if (cr.getTestUnitDescription().getQualifiedName().equals(testName)) {
        if (cr.getExceptionType() != null && !cr.getExceptionType().isEmpty() && !"None".equals(cr.getExceptionType())) {
          exceptionType = cr.getExceptionType();
        }
        
        if (cr.getExceptionMessage() != null && !cr.getExceptionMessage().isEmpty() && !"None".equals(cr.getExceptionMessage())) {
          exceptionMessage = cr.getExceptionMessage();
        }
        
        if (cr.getStackTrace() != null && !cr.getStackTrace().isEmpty() && !"None".equals(cr.getStackTrace())) {
          // Filter the stack trace to remove infrastructure differences
          stackTrace = org.pitest.mutationtest.execute.DetailedMutationTestResult.getRelevantStackTrace(cr.getStackTrace());
        }
        break;
      }
    }
    
    // Write JSON format for easier parsing
    try {
      org.pitest.mutationtest.execute.JsonTestResultWriter.writeBaselineTestResultJson(
          baselineTestResultsDir, tcID, testName, result, durationMillis, 
          exceptionType, exceptionMessage, stackTrace, bitSequence);
    } catch (Exception e) {
      LOG.warning("Failed to write JSON format for baseline test result: " + e.getMessage());
    }
    
    LOG.fine("Created individual test result JSON for tcID=" + tcID + " with bit sequence length " + bitSequence.length());
  }
  
  /**
   * Write line mapping CSV file that maps bit sequence positions to actual code lines.
   */
  /**
   * Measure expected mutation testing time instead of running actual mutation tests.
   * This method:
   * 1. Runs baseline tests to measure execution time
   * 2. Counts unfiltered mutations
   * 3. Estimates total time based on baseline time, mutation count, and thread count
   * 4. Returns time estimation results without running mutation tests
   */
  /**
   * Estimates the expected mutation testing time based on the filtered mutations and actual baseline test execution time.
   * This method is called after mutations have been built and filtered, using the same data
   * that would be used for actual mutation testing.
   */
  private CombinedStatistics estimateExpectedTime(List<MutationAnalysisUnit> mutationTestUnits, 
                                                 CoverageDatabase coverageData, 
                                                 long baselineTestExecutionTime,
                                                 int unfilteredMutationCount,
                                                 int linesCoveredByFailingTests) {
    
    // Count total filtered mutations from the test units
    int filteredMutations = mutationTestUnits.stream()
        .mapToInt(unit -> unit.mutants().size())
        .sum();

    if (filteredMutations == 0) {
      LOG.info("No mutations to test - expected time is 0");
      return emptyStatistics();
    }

    // Calculate test counts from coverage data using the same logic as captureBaselineAndTestResultsFromCoverageData
    int failingTestCount = 0;
    int passingTestCount = 0;
    int totalTestCount = 0;
    
    if (coverageData instanceof CoverageData) {
      CoverageData cd = (CoverageData) coverageData;
      
      // Collect all unique tests from coverage data (same approach as captureBaselineAndTestResultsFromCoverageData)
      Set<TestInfo> allTests = new HashSet<>();
      for (BlockCoverage blockCov : cd.createCoverage()) {
        Collection<TestInfo> testsForBlock = cd.getTestsForBlockLocation(blockCov.getBlock());
        allTests.addAll(testsForBlock);
      }
      
      // Get failing test names
      Set<String> failingTestNames = cd.getFailingTestDescriptions().stream()
          .map(desc -> desc.getQualifiedName())
          .collect(java.util.stream.Collectors.toSet());
      
      // Count tests correctly
      totalTestCount = allTests.size();
      failingTestCount = failingTestNames.size();
      passingTestCount = totalTestCount - failingTestCount;
    } else {
      totalTestCount = coverageData.testCount();
      passingTestCount = totalTestCount;
    }
    
    LOG.info("Baseline test execution time (actual): " + baselineTestExecutionTime + " ms (" + formatTime(baselineTestExecutionTime) + ")");

    // Get thread count
    final int numberOfThreads = numberOfThreads();
    
    // Calculate time estimation using the formula:
    // Total time = (baseline test execution time * number of filtered mutations) / number of threads
    final long totalEstimatedTime = (baselineTestExecutionTime * filteredMutations) / numberOfThreads;
    
    // Add some overhead for setup, teardown, and coordination between threads
    final long overheadTime = (long)(totalEstimatedTime * 0.15); // 15% overhead
    final long finalEstimatedTime = totalEstimatedTime + overheadTime;

    // Calculate mutation density metrics (only for filtered mutations)
    double mutationsPerFailingLine = linesCoveredByFailingTests > 0 
            ? (double) filteredMutations / linesCoveredByFailingTests : 0.0;

    // Log detailed results
    LOG.info("=== Time Estimation Results ===");
    LOG.info("Number of failing tests: " + failingTestCount);
    LOG.info("Number of passing tests: " + passingTestCount);
    LOG.info("Baseline test execution time: " + baselineTestExecutionTime + " ms (" + formatTime(baselineTestExecutionTime) + ")");
    LOG.info("Number of mutations before filter: " + unfilteredMutationCount);
    LOG.info("Number of mutations after filter: " + filteredMutations);
    LOG.info("Lines covered by failing tests: " + linesCoveredByFailingTests);
    LOG.info("Average mutations per failing test line (after filter): " + String.format("%.2f", mutationsPerFailingLine));
    LOG.info("Number of threads: " + numberOfThreads);
    LOG.info("Estimated time per mutation: " + baselineTestExecutionTime + " ms");
    LOG.info("Total estimated mutation testing time (parallel): " + formatTime(totalEstimatedTime));
    LOG.info("Estimated time with overhead (15%): " + formatTime(finalEstimatedTime));
    LOG.info("========================================");

    // Create minimal statistics for the result
    final CoverageSummary coverageSummary = new CoverageSummary(
        0, // No lines analyzed for mutations
        0, // No lines covered in mutation testing
        totalTestCount // Number of tests from baseline
    );

    final MutationStatistics mutationStats = new MutationStatistics(
        emptyList(), // No mutation scores
        filteredMutations, // Total mutations found (for info)
        0, // No mutations killed  
        0, // No surviving mutations
        0, // No timeout mutations
        emptySet() // No mutated classes
    );

    return new CombinedStatistics(mutationStats, coverageSummary, emptyList());
  }

  /**
   * Format time in a human-readable format.
   */
  private String formatTime(long milliseconds) {
    if (milliseconds < 1000) {
      return milliseconds + " ms";
    } else if (milliseconds < 60000) {
      return String.format("%.1f seconds", milliseconds / 1000.0);
    } else if (milliseconds < 3600000) {
      return String.format("%.1f minutes", milliseconds / 60000.0);
    } else {
      return String.format("%.1f hours", milliseconds / 3600000.0);
    }
  }
  
  /**
   * Calculate the number of lines covered by failing test cases.
   * This is used for time estimation metrics to show mutation density.
   */
  private int calculateLinesCoveredByFailingTests(CoverageDatabase coverageData) {
    if (!(coverageData instanceof CoverageData)) {
      LOG.fine("Coverage data is not a CoverageData instance, cannot calculate failing test lines");
      return 0;
    }
    
    try {
      CoverageData cd = (CoverageData) coverageData;
      
      // Get failing test names
      Set<String> failingTestNames = cd.getFailingTestDescriptions().stream()
          .map(desc -> desc.getQualifiedName())
          .collect(java.util.stream.Collectors.toSet());
      
      if (failingTestNames.isEmpty()) {
        LOG.info("No failing tests found - lines covered by failing tests: 0");
        return 0;
      }
      
      Set<String> linesCoveredByFailingTests = new HashSet<>();
      
      // Analyze line coverage by failing tests
      for (ClassName className : this.code.getCodeUnderTestNames()) {
        Set<ClassLine> coveredLines = cd.getCoveredLines(className);
        
        for (ClassLine line : coveredLines) {
          int lineNumber = line.getLineNumber();
          boolean coveredByFailingTest = false;
          
          // Check all block coverage to find blocks that cover this specific line
          for (BlockCoverage blockCov : cd.createCoverage()) {
            BlockLocation blockLocation = blockCov.getBlock();
            
            // Only check blocks from the same class
            if (blockLocation.getLocation().getClassName().equals(className)) {
              // Get the lines covered by this block
              Set<Integer> blockLines = getBlockLines(cd, blockLocation);
              
              // Check if this block covers the current line
              if (blockLines.contains(lineNumber)) {
                // Get the tests that cover this specific block
                Collection<TestInfo> testsForBlock = cd.getTestsForBlockLocation(blockLocation);
                
                // Check if any of the tests for this block are failing tests
                boolean blockCoveredByFailingTest = testsForBlock.stream()
                    .anyMatch(test -> failingTestNames.contains(test.getName()));
                
                if (blockCoveredByFailingTest) {
                  coveredByFailingTest = true;
                  break;
                }
              }
            }
          }
          
          if (coveredByFailingTest) {
            linesCoveredByFailingTests.add(className.asJavaName() + ":" + lineNumber);
          }
        }
      }
      
      LOG.info("Found " + linesCoveredByFailingTests.size() + " lines covered by failing tests");
      return linesCoveredByFailingTests.size();
      
    } catch (Exception e) {
      LOG.warning("Failed to calculate lines covered by failing tests: " + e.getMessage());
      return 0;
    }
  }

  /**
   * Assigns unique mutation IDs to all mutations in the analysis units.
   * This is called after all filtering is complete to ensure consistent IDs.
   */
  private void assignMutantIds(List<MutationAnalysisUnit> analysisUnits) {
    int mutantId = 1;
    int totalMutations = 0;
    
    for (MutationAnalysisUnit unit : analysisUnits) {
      Collection<MutationDetails> mutations = unit.mutants();
      for (MutationDetails mutation : mutations) {
        if (!mutation.hasMutantId()) {
          mutation.setMutantId(mutantId++);
          LOG.fine("Assigned mutant ID " + mutation.getMutantId() + " to mutation " + mutation.getId());
        }
        totalMutations++;
      }
    }
    
    LOG.info("Assigned unique mutation IDs to " + totalMutations + " mutations across " + analysisUnits.size() + " analysis units");
  }
}
