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

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.EngineArguments;
import org.pitest.mutationtest.TimeoutLengthStrategy;
import org.pitest.mutationtest.config.TestPluginArguments;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.util.Verbosity;

public class MinionArguments implements Serializable {

  private static final long serialVersionUID = 1L;

  final Collection<MutationDetails> mutations;
  final Collection<ClassName>       testClasses;
  final String                      engine;
  final EngineArguments             engineArgs;
  final TimeoutLengthStrategy       timeoutStrategy;
  final Verbosity verbosity;
  final boolean                     fullMutationMatrix;
  final boolean                     fullMatrixResearchMode;
  final TestPluginArguments         pitConfig;
  final String                      reportDir;
  final Map<String, TestCaseMetadata> testCaseMetadata;

  public MinionArguments(final Collection<MutationDetails> mutations,
      final Collection<ClassName> tests, final String engine, final EngineArguments engineArgs,
      final TimeoutLengthStrategy timeoutStrategy, final Verbosity verbosity, final boolean fullMutationMatrix,
      final boolean fullMatrixResearchMode, final TestPluginArguments pitConfig, final String reportDir,
      final Map<String, TestCaseMetadata> testCaseMetadata) {
    this.mutations = mutations;
    this.testClasses = tests;
    this.engine = engine;
    this.engineArgs = engineArgs;
    this.timeoutStrategy = timeoutStrategy;
    this.verbosity = verbosity;
    this.fullMutationMatrix = fullMutationMatrix;
    this.fullMatrixResearchMode = fullMatrixResearchMode;
    this.pitConfig = pitConfig;
    this.reportDir = reportDir;
    this.testCaseMetadata = testCaseMetadata;
  }

  public Verbosity verbosity() {
    return this.verbosity;
  }

}
