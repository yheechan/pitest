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

package org.pitest.mutationtest.execute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.pitest.coverage.TestInfo;
import org.pitest.mutationtest.TimeoutLengthStrategy;
import org.pitest.testapi.TestUnit;

public class TimeOutDecoratedTestSource {

  private final Map<String, TestUnit> allTests = new HashMap<>();
  private final TimeoutLengthStrategy timeoutStrategy;
  private final Reporter              r;

  public TimeOutDecoratedTestSource(
      final TimeoutLengthStrategy timeoutStrategy,
      final List<TestUnit> allTests, final Reporter r) {
    this.timeoutStrategy = timeoutStrategy;
    mapTests(allTests);
    this.r = r;
  }

  private void mapTests(final List<TestUnit> tests) {
    for (final TestUnit each : tests) {
      this.allTests.put(each.getDescription().getQualifiedName(), each);
    }
  }

  public List<TestUnit> translateTests(final List<TestInfo> testsInOrder) {
    return testsInOrder.stream().flatMap(testToTestUnit()).collect(Collectors.toList());
  }

  private Function<TestInfo, Stream<TestUnit>> testToTestUnit() {
    return a -> {
      final TestUnit tu = TimeOutDecoratedTestSource.this.allTests.get(a
          .getName());
      if (tu != null) {
        return Stream
            .of(new MutationTimeoutDecorator(tu,
                new TimeOutSystemExitSideEffect(
                    TimeOutDecoratedTestSource.this.r),
                    TimeOutDecoratedTestSource.this.timeoutStrategy, a.getTime()));
      }
      return Stream.empty();
    };
  }

  /**
   * Get all available tests instead of just covering tests.
   * Used for research mode where we want to run all tests against all mutants.
   */
  public List<TestUnit> getAllTests() {
    return this.allTests.values().stream()
        .map(tu -> new MutationTimeoutDecorator(tu,
            new TimeOutSystemExitSideEffect(this.r),
            this.timeoutStrategy, 
            0)) // Use default timeout since we don't have test-specific timing
        .collect(Collectors.toList());
  }
}
