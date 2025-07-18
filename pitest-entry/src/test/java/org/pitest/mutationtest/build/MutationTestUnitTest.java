package org.pitest.mutationtest.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.pitest.mutationtest.LocationMother.aMutationId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.CodeSource;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.EngineArguments;
import org.pitest.mutationtest.MutationConfig;
import org.pitest.mutationtest.MutationMetaData;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationStatusTestPair;
import org.pitest.mutationtest.TimeoutLengthStrategy;
import org.pitest.mutationtest.config.TestPluginArguments;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationDetailsMother;
import org.pitest.mutationtest.engine.MutationEngine;
import org.pitest.process.JavaAgent;
import org.pitest.process.LaunchOptions;
import org.pitest.util.Verbosity;

public class MutationTestUnitTest {

  private MutationTestUnit      testee;
  private List<MutationDetails> mutations;
  private Collection<ClassName> tests;

  private MutationConfig        mutationConfig;

  @Mock
  private TimeoutLengthStrategy timeout;

  @Mock
  private JavaAgent             javaAgent;

  @Mock
  private MutationEngine        engine;

  @Mock
  private CodeSource            codeSource;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    this.mutationConfig = new MutationConfig(this.engine, new LaunchOptions(
        this.javaAgent));
    this.mutations = new ArrayList<>();
    this.tests = new ArrayList<>();
    this.testee = new MutationTestUnit(this.mutations,
        new WorkerFactory(null, TestPluginArguments.defaults(), this.mutationConfig, EngineArguments.arguments(), this.timeout,
            Verbosity.DEFAULT, false, false, null, null, java.util.Collections.emptyMap()), this.codeSource);

  }

  @Test
  public void shouldReportWhenMutationsNotCoveredByAnyTest() throws Exception {
    addMutation();
    this.tests.add(ClassName.fromString("foo"));
    final MutationMetaData actual = this.testee.call();
    final MutationResult expected = new MutationResult(this.mutations.get(0),
        MutationStatusTestPair.notAnalysed(0, DetectionStatus.NO_COVERAGE, Collections.emptyList()));
    assertThat(actual.getMutations()).contains(expected);
  }

  @Test
  public void shouldReportPriorityBasedOnNumberOfMutations() {
    this.mutations.add(MutationDetailsMother.aMutationDetail().build());
    this.testee = new MutationTestUnit(MutationDetailsMother.aMutationDetail()
        .build(42), null, this.codeSource);
    assertThat(this.testee.priority()).isEqualTo(42);
  }

  private void addMutation() {
    this.mutations.add(new MutationDetails(aMutationId().build(), "file", "desc",
        0, 0));
  }

}
