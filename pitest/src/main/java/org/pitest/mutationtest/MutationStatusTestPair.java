/*
 * Copyright 2011 Henry Coles
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
package org.pitest.mutationtest;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.pitest.mutationtest.execute.DetailedMutationTestResult;

public final class MutationStatusTestPair implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int             numberOfTestsRun;
  private final DetectionStatus status;
  private final List<String>    killingTests;
  private final List<String>    succeedingTests;
  private final List<String>    coveringTests;
  private final List<DetailedMutationTestResult> detailedResults;

  @Deprecated
  // for backwards compatibility. Remove at next major release
  public static MutationStatusTestPair notAnalysed(int testsRun, DetectionStatus status) {
    return notAnalysed(testsRun, status, Collections.emptyList());
  }

  public static MutationStatusTestPair notAnalysed(int testsRun, DetectionStatus status, List<String> coveringTests) {
    return new MutationStatusTestPair(testsRun, status, Collections.emptyList(), Collections.emptyList(), coveringTests, Collections.emptyList());
  }

  public MutationStatusTestPair(final int numberOfTestsRun,
      final DetectionStatus status, final String killingTest) {
    this(numberOfTestsRun, status, killingTestToList(killingTest),
      Collections.emptyList(),killingTestToList(killingTest), Collections.emptyList());
  }

  // for backwards compatibility. Remove at next major release
  @Deprecated
  public MutationStatusTestPair(final int numberOfTestsRun,
                                final DetectionStatus status, final List<String> killingTests,
                                final List<String> succeedingTests) {
    this(numberOfTestsRun, status, killingTests, succeedingTests, Collections.emptyList(), Collections.emptyList());
  }

  public MutationStatusTestPair(final int numberOfTestsRun,
      final DetectionStatus status, final List<String> killingTests,
      final List<String> succeedingTests, final List<String> coveringTests) {
    this(numberOfTestsRun, status, killingTests, succeedingTests, coveringTests, Collections.emptyList());
  }

  public MutationStatusTestPair(final int numberOfTestsRun,
      final DetectionStatus status, final List<String> killingTests,
      final List<String> succeedingTests, final List<String> coveringTests,
      final List<DetailedMutationTestResult> detailedResults) {
    this.status = status;
    this.killingTests = killingTests;
    this.succeedingTests = succeedingTests;
    this.numberOfTestsRun = numberOfTestsRun;
    this.coveringTests = coveringTests;
    this.detailedResults = detailedResults != null ? detailedResults : Collections.emptyList();
  }
  
  private static List<String> killingTestToList(String killingTest) {
    if (killingTest == null) {
      return Collections.emptyList();
    }
    
    return Collections.singletonList(killingTest);
  }

  public DetectionStatus getStatus() {
    return this.status;
  }

  /**
   * Get the killing test.
   * If the full mutation matrix is enabled, the first test will be returned.
   */
  public Optional<String> getKillingTest() {
    if (this.killingTests.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(this.killingTests.get(0));
  }

  /** Get all killing tests.
   *  If the full mutation matrix is not enabled, this will only be the first killing test. 
   */
  public List<String> getKillingTests() {
    return killingTests;
  }

  /** Get all succeeding tests.
   *  If the full mutation matrix is not enabled, this list will be empty. 
   */
  public List<String> getSucceedingTests() {
    return succeedingTests;
  }

  public List<String> getCoveringTests() {
    return coveringTests;
  }

  public List<DetailedMutationTestResult> getDetailedResults() {
    return detailedResults;
  }

  public int getNumberOfTestsRun() {
    return this.numberOfTestsRun;
  }

  @Override
  public String toString() {
    if (this.killingTests.isEmpty()) {
      return this.status.name();
    } else {
      return this.status.name() + " by " + this.killingTests;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(numberOfTestsRun, status, killingTests, succeedingTests, coveringTests, detailedResults);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final MutationStatusTestPair other = (MutationStatusTestPair) obj;
    return numberOfTestsRun == other.numberOfTestsRun
            && status == other.status
            && Objects.equals(killingTests, other.killingTests)
            && Objects.equals(succeedingTests, other.succeedingTests)
            && Objects.equals(coveringTests, other.coveringTests)
            && Objects.equals(detailedResults, other.detailedResults);
  }
}
