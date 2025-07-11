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
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.build;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.pitest.classinfo.ClassName;
import org.pitest.classpath.CodeSource;
import org.pitest.coverage.TestInfo;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationMetaData;
import org.pitest.mutationtest.MutationStatusMap;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.execute.MutationTestProcess;
import org.pitest.util.ExitCode;
import org.pitest.util.Log;

public class MutationTestUnit implements MutationAnalysisUnit {

  private static final Logger               LOG = Log.getLogger();

  private final Collection<MutationDetails> availableMutations;
  private final WorkerFactory               workerFactory;
  private final CodeSource                  codeSource;


  public MutationTestUnit(Collection<MutationDetails> availableMutations, WorkerFactory workerFactory, CodeSource codeSource) {
    this.availableMutations = availableMutations;
    this.workerFactory = workerFactory;
    this.codeSource = codeSource;
  }

  @Override
  public MutationMetaData call() throws Exception {
    final MutationStatusMap mutations = new MutationStatusMap();

    mutations.setStatusForMutations(this.availableMutations,
        DetectionStatus.NOT_STARTED);

    mutations.markUncoveredMutations();

    runTestsInSeperateProcess(mutations);

    return reportResults(mutations);
  }

  @Override
  public int priority() {
    return this.availableMutations.size();
  }

  @Override
  public Collection<MutationDetails> mutants() {
    return availableMutations;
  }

  private void runTestsInSeperateProcess(final MutationStatusMap mutations)
      throws IOException, InterruptedException {
    while (mutations.hasUnrunMutations()) {
      runTestInSeperateProcessForMutationRange(mutations);
    }
  }

  private void runTestInSeperateProcessForMutationRange(
      final MutationStatusMap mutations) throws IOException,
      InterruptedException {

    final Collection<MutationDetails> remainingMutations = mutations
        .getUnrunMutations();
    final MutationTestProcess worker = this.workerFactory.createWorker(
        remainingMutations, testClassesFor(remainingMutations));
    worker.start();

    setFirstMutationToStatusOfStartedInCaseMinionFailsAtBoot(mutations,
        remainingMutations);

    final ExitCode exitCode = waitForMinionToDie(worker);
    worker.results(mutations);

    correctResultForProcessExitCode(mutations, exitCode);
  }

  private Set<ClassName> testClassesFor(Collection<MutationDetails> remainingMutations) {
    // In research mode, use all available test classes to ensure complete coverage
    if (this.workerFactory.isFullMatrixResearchMode()) {
      return this.codeSource.getTestClassNames();
    }
    
    // Default behavior: only use covering tests
    return remainingMutations.stream()
            .flatMap(m -> m.getTestsInOrder().stream().map(TestInfo.toDefiningClassName()))
            .collect(Collectors.toSet());
  }

  private static ExitCode waitForMinionToDie(final MutationTestProcess worker) {
    final ExitCode exitCode = worker.waitToDie();
    LOG.fine("Exit code was - " + exitCode);
    if (exitCode == ExitCode.MINION_DIED) {
      LOG.severe("Minion did not start or died during analysis. This may indicate an issue in your environment such as insufficient memory.");
    }
    return exitCode;
  }

  private static void setFirstMutationToStatusOfStartedInCaseMinionFailsAtBoot(
      final MutationStatusMap mutations,
      final Collection<MutationDetails> remainingMutations) {
    mutations.setStatusForMutation(remainingMutations.iterator().next(),
        DetectionStatus.STARTED);
  }

  private static void correctResultForProcessExitCode(
      final MutationStatusMap mutations, final ExitCode exitCode) {

    if (!exitCode.isOk()) {
      final Collection<MutationDetails> unfinishedRuns = mutations
          .getUnfinishedRuns();
      final DetectionStatus status = DetectionStatus
          .getForErrorExitCode(exitCode);
      LOG.warning("Minion exited abnormally due to " + status);
      LOG.fine("Setting " + unfinishedRuns.size() + " unfinished runs to "
          + status + " state");
      mutations.setStatusForMutations(unfinishedRuns, status);

    } else {
      LOG.fine("Minion exited ok");
    }

  }

  private static MutationMetaData reportResults(final MutationStatusMap mutationsMap) {
    return new MutationMetaData(mutationsMap.createMutationResults());
  }



}
