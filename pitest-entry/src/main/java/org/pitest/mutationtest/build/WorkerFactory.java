package org.pitest.mutationtest.build;

import org.pitest.classinfo.ClassName;
import org.pitest.functional.prelude.Prelude;
import org.pitest.mutationtest.EngineArguments;
import org.pitest.mutationtest.MutationConfig;
import org.pitest.mutationtest.TimeoutLengthStrategy;
import org.pitest.mutationtest.config.TestPluginArguments;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.execute.MinionArguments;
import org.pitest.mutationtest.execute.MutationTestProcess;
import org.pitest.process.ProcessArgs;
import org.pitest.util.Log;
import org.pitest.util.SocketFinder;
import org.pitest.util.Verbosity;

import java.io.File;
import java.util.Collection;
import java.util.function.Consumer;

import static org.pitest.functional.prelude.Prelude.printlnWith;

public class WorkerFactory {

  private final String                classPath;
  private final File                  baseDir;
  private final TestPluginArguments   pitConfig;
  private final TimeoutLengthStrategy timeoutStrategy;
  private final Verbosity             verbosity;
  private final boolean               fullMutationMatrix;
  private final boolean               fullMatrixResearchMode;
  private final MutationConfig        config;
  private final EngineArguments       args;
  private final String                reportDir;
  private final java.util.Map<String, org.pitest.mutationtest.execute.TestCaseMetadata> testCaseMetadata;

  @SuppressWarnings("unchecked")
  public WorkerFactory(final File baseDir,
      final TestPluginArguments pitConfig,
      final MutationConfig mutationConfig,
      final EngineArguments args,
      final TimeoutLengthStrategy timeoutStrategy,
      final Verbosity verbosity,
      final boolean fullMutationMatrix,
      final boolean fullMatrixResearchMode,
      final String classPath,
      final String reportDir,
      final java.util.Map testCaseMetadata) {
    this.pitConfig = pitConfig;
    this.timeoutStrategy = timeoutStrategy;
    this.verbosity = verbosity;
    this.fullMutationMatrix = fullMutationMatrix;
    this.fullMatrixResearchMode = fullMatrixResearchMode;
    this.classPath = classPath;
    this.baseDir = baseDir;
    this.config = mutationConfig;
    this.args = args;
    this.reportDir = reportDir;
    this.testCaseMetadata = testCaseMetadata != null 
        ? new java.util.HashMap<String, org.pitest.mutationtest.execute.TestCaseMetadata>(testCaseMetadata) 
        : new java.util.HashMap<String, org.pitest.mutationtest.execute.TestCaseMetadata>();
  }

  public MutationTestProcess createWorker(
      final Collection<MutationDetails> remainingMutations,
      final Collection<ClassName> testClasses) {
    
    // Use the test case metadata that was passed in during construction
    final MinionArguments fileArgs = new MinionArguments(remainingMutations,
        testClasses, this.config.getEngine().getName(), this.args, this.timeoutStrategy,
        Log.verbosity(), this.fullMutationMatrix, this.fullMatrixResearchMode, this.pitConfig, this.reportDir,
        this.testCaseMetadata);

    ProcessArgs args = ProcessArgs.withClassPath(this.classPath)
        .andBaseDir(this.baseDir).andStdout(captureStdOutIfVerbose())
        .andStderr(captureStdErrIfVerbose());
    
    // Add special memory handling for full matrix research mode
    if (this.fullMatrixResearchMode) {
      // Get existing JVM args and filter out memory settings
      final java.util.List<String> existingArgs = this.config.getLaunchOptions().getChildJVMArgs();
      final java.util.List<String> filteredArgs = existingArgs.stream()
          .filter(opt -> !opt.startsWith("-Xmx") && !opt.startsWith("-Xms"))
          .collect(java.util.stream.Collectors.toList());
      
      // Add memory-optimized settings for large test suites
      final java.util.List<String> memoryOptimizedArgs = new java.util.ArrayList<>(filteredArgs);
      memoryOptimizedArgs.addAll(java.util.Arrays.asList(
          "-Xmx16g",  // Increase max heap to 16GB for large test suites
          "-Xms4g",  // Increase initial heap to 4GB
          "-XX:+UseG1GC",  // Use G1 GC for better large heap performance
          "-XX:MaxGCPauseMillis=200",  // Limit GC pause times
          "-XX:G1HeapRegionSize=32m",  // Larger heap regions for large objects
          "-XX:+UnlockExperimentalVMOptions",
          "-XX:+UseStringDeduplication", // Reduce memory for duplicate strings
          "-XX:StringDeduplicationAgeThreshold=1"
      ));
      
      // Create new LaunchOptions with optimized memory settings
      final org.pitest.process.LaunchOptions optimizedLaunchOptions = 
          new org.pitest.process.LaunchOptions(
              this.config.getLaunchOptions().getJavaAgentFinder(),
              this.config.getLaunchOptions().getJavaExecutableLocator(),
              memoryOptimizedArgs,
              this.config.getLaunchOptions().getEnvironmentVariables()
          );
      
      args = args.andLaunchOptions(optimizedLaunchOptions);
    } else {
      args = args.andLaunchOptions(this.config.getLaunchOptions());
    }

    final SocketFinder sf = new SocketFinder();
    return new MutationTestProcess(
        sf.getNextAvailableServerSocket(), args, fileArgs);
  }

  private Consumer<String> captureStdOutIfVerbose() {
    if (this.verbosity.showMinionOutput()) {
      return printlnWith("stdout ");
    } else {
      return Prelude.noSideEffect(String.class);
    }
  }

  private Consumer<String> captureStdErrIfVerbose() {
    if (this.verbosity.showMinionOutput()) {
      return printlnWith("stderr ");
    } else {
      return Prelude.noSideEffect(String.class);
    }
  }

  public boolean isFullMatrixResearchMode() {
    return this.fullMatrixResearchMode;
  }
}
