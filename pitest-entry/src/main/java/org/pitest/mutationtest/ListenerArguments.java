package org.pitest.mutationtest;

import org.pitest.coverage.ReportCoverage;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.engine.MutationEngine;
import org.pitest.mutationtest.verify.BuildMessage;
import org.pitest.plugin.FeatureSetting;
import org.pitest.util.ResultOutputStrategy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Data passed to the listener MutationResultListener factories for use when
 * constructing listeners.
 */
public class ListenerArguments {

  private final ResultOutputStrategy outputStrategy;
  private final ReportCoverage       coverage;
  private final long                 startTime;
  private final SourceLocator        locator;
  private final MutationEngine       engine;
  private final boolean              fullMutationMatrix;
  private final ReportOptions        data;
  private final FeatureSetting       setting;
  private final List<BuildMessage> issues;
  private final Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> testCaseMetadata;

  public ListenerArguments(ResultOutputStrategy outputStrategy,
                           ReportCoverage coverage,
                           SourceLocator locator,
                           MutationEngine engine,
                           long startTime,
                           boolean fullMutationMatrix,
                           ReportOptions  data,
                           List<BuildMessage> issues) {
    this(outputStrategy, coverage, locator, engine, startTime, fullMutationMatrix, data, null, issues, null);
  }

  public ListenerArguments(ResultOutputStrategy outputStrategy,
                           ReportCoverage coverage,
                           SourceLocator locator,
                           MutationEngine engine,
                           long startTime,
                           boolean fullMutationMatrix,
                           ReportOptions  data,
                           List<BuildMessage> issues,
                           Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> testCaseMetadata) {
    this(outputStrategy, coverage, locator, engine, startTime, fullMutationMatrix, data, null, issues, testCaseMetadata);
  }

  ListenerArguments(ResultOutputStrategy outputStrategy,
                           ReportCoverage coverage,
                           SourceLocator locator,
                           MutationEngine engine,
                           long startTime,
                           boolean fullMutationMatrix,
                           ReportOptions  data,
                           FeatureSetting setting,
                           List<BuildMessage> issues,
                           Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> testCaseMetadata) {
    this.outputStrategy = outputStrategy;
    this.coverage = coverage;
    this.locator = locator;
    this.startTime = startTime;
    this.engine = engine;
    this.fullMutationMatrix = fullMutationMatrix;
    this.data = data;
    this.setting = setting;
    this.testCaseMetadata = testCaseMetadata;
    this.issues = issues.stream()
            .distinct()
            .sorted()
            .collect(Collectors.toList());
  }

  public ResultOutputStrategy getOutputStrategy() {
    return this.outputStrategy;
  }

  public ReportCoverage getCoverage() {
    return this.coverage;
  }

  public long getStartTime() {
    return this.startTime;
  }

  public SourceLocator getLocator() {
    return this.locator;
  }

  public MutationEngine getEngine() {
    return this.engine;
  }

  public boolean isFullMutationMatrix() {
  return fullMutationMatrix;
  }

  public ReportOptions data() {
    return data;
  }

  public Optional<FeatureSetting> settings() {
    return Optional.ofNullable(setting);
  }

  public List<BuildMessage> issues() {
    return Collections.unmodifiableList(issues);
  }

  public Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> getTestCaseMetadata() {
    return testCaseMetadata;
  }

  public ListenerArguments withSetting(FeatureSetting setting) {
    return new ListenerArguments(outputStrategy,
            coverage,
            locator,
            engine,
            startTime,
            fullMutationMatrix,
            data,
            setting,
            issues,
            testCaseMetadata);
  }

}
