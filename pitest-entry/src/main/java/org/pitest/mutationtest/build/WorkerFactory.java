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
  private final int                   numberOfThreads;
  private final int                   mutationUnitSize;

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
      final java.util.Map testCaseMetadata,
      final int numberOfThreads,
      final int mutationUnitSize) {
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
    this.numberOfThreads = numberOfThreads;
    this.mutationUnitSize = mutationUnitSize;
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
      
      Log.getLogger().info("WorkerFactory: Applying optimized memory allocation for mutation testing workers");
      
      // Calculate optimal memory allocation for workers based on system resources and thread count
      MemoryAllocation memAlloc = calculateOptimalMemoryAllocation();
      
      Log.getLogger().info("Optimized memory allocation strategy for 32GB system:");
      Log.getLogger().info("  Total system memory: " + memAlloc.systemMemoryMB + "MB");
      Log.getLogger().info("  Main process memory: " + memAlloc.mainProcessMemoryMB + "MB");
      Log.getLogger().info("  Reserved for OS: " + memAlloc.reservedForOSMB + "MB");
      Log.getLogger().info("  Available for workers: " + memAlloc.availableForWorkersMB + "MB");
      Log.getLogger().info("  Thread count: " + memAlloc.threadCount);
      Log.getLogger().info("  Worker memory per thread: " + memAlloc.workerMemoryMB + "MB (realistic allocation)");
      Log.getLogger().info("  Total worker memory: " + (memAlloc.workerMemoryMB * memAlloc.threadCount) + "MB");
      
      // Filter out any existing memory settings to avoid conflicts
      final java.util.List<String> filteredArgs = existingArgs.stream()
          .filter(opt -> !opt.startsWith("-Xmx") && !opt.startsWith("-Xms"))
          .collect(java.util.stream.Collectors.toList());
      
      // Add calculated memory settings for workers
      final java.util.List<String> memoryOptimizedArgs = new java.util.ArrayList<>(filteredArgs);
      memoryOptimizedArgs.addAll(memAlloc.toJvmArgs());
      
      Log.getLogger().info("Applied realistic memory optimizations for workers based on actual PIT requirements");
      Log.getLogger().info("Worker JVM settings: "
                         + String.join(" ", memoryOptimizedArgs.stream()
                                         .filter(arg -> arg.startsWith("-X") || arg.startsWith("-XX:"))
                                         .toArray(String[]::new)));
      
      // Safety checks and recommendations
      memAlloc.logSafetyWarnings();
      
      // Create new LaunchOptions with the optimized memory settings
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
  
  /**
   * Calculate optimal memory allocation for workers based on system resources and thread count
   */
  private MemoryAllocation calculateOptimalMemoryAllocation() {
    Runtime sysRuntime = Runtime.getRuntime();
    long mainProcessMemoryMB = sysRuntime.maxMemory() / (1024 * 1024);
    
    // For 32GB systems, use more precise calculations
    long systemMemoryMB = 32768; // Assume 32GB system (can be made configurable)
    long reservedForOSMB = 4096; // Reserve 4GB for OS and other processes
    long availableForPitMB = systemMemoryMB - reservedForOSMB; // 28GB available
    long availableForWorkersMB = availableForPitMB - mainProcessMemoryMB;
    
    // Calculate worker memory based on thread count and available memory
    int threadCount = this.numberOfThreads;
    long workerMemoryMB;
    
    // Thread-based allocation strategy for 32GB systems - OPTIMIZED for realistic needs
    if (threadCount == 1) {
      // Single thread: generous allocation but not excessive
      workerMemoryMB = Math.min(2048, availableForWorkersMB); // Max 2GB for single worker (was 8GB)
    } else if (threadCount <= 4) {
      // Low thread count (2-4): efficient allocation
      workerMemoryMB = Math.max(1024, availableForWorkersMB / threadCount);
      workerMemoryMB = Math.min(workerMemoryMB, 1536); // Max 1.5GB per worker (was 6GB)
    } else if (threadCount <= 8) {
      // Medium thread count (5-8): balanced allocation
      workerMemoryMB = Math.max(768, availableForWorkersMB / threadCount);
      workerMemoryMB = Math.min(workerMemoryMB, 1024); // Max 1GB per worker (was 4GB)
    } else if (threadCount <= 16) {
      // High thread count (9-16): efficient allocation
      workerMemoryMB = Math.max(512, availableForWorkersMB / threadCount);
      workerMemoryMB = Math.min(workerMemoryMB, 768); // Max 768MB per worker (was 2GB)
    } else {
      // Very high thread count (17+): minimal but sufficient allocation
      workerMemoryMB = Math.max(512, availableForWorkersMB / threadCount);
      workerMemoryMB = Math.min(workerMemoryMB, 768); // Max 768MB per worker (was 1.5GB)
    }
    
    // Additional optimization for mutation unit size 1 (very small batches)
    if (this.mutationUnitSize == 1 && threadCount > 8) {
      // For unit size 1 with many threads, even smaller allocation is sufficient
      workerMemoryMB = Math.max(512, workerMemoryMB);
      Log.getLogger().info("Mutation unit size 1 detected - using optimized memory for small batch processing");
    }
    
    return new MemoryAllocation(systemMemoryMB, mainProcessMemoryMB, reservedForOSMB, 
                               availableForWorkersMB, threadCount, workerMemoryMB);
  }
  
  /**
   * Inner class to encapsulate memory allocation calculations and provide consistent behavior
   */
  private static class MemoryAllocation {
    final long systemMemoryMB;
    final long mainProcessMemoryMB;
    final long reservedForOSMB;
    final long availableForWorkersMB;
    final int threadCount;
    final long workerMemoryMB;
    
    MemoryAllocation(long systemMemoryMB, long mainProcessMemoryMB, long reservedForOSMB,
                    long availableForWorkersMB, int threadCount, long workerMemoryMB) {
      this.systemMemoryMB = systemMemoryMB;
      this.mainProcessMemoryMB = mainProcessMemoryMB;
      this.reservedForOSMB = reservedForOSMB;
      this.availableForWorkersMB = availableForWorkersMB;
      this.threadCount = threadCount;
      this.workerMemoryMB = workerMemoryMB;
    }
    
    java.util.List<String> toJvmArgs() {
      java.util.List<String> args = new java.util.ArrayList<>();
      args.add("-Xmx" + workerMemoryMB + "m");   // Calculated worker heap
      args.add("-Xms" + (workerMemoryMB / 4) + "m");   // Start with 25% of max heap
      args.add("-XX:+UseG1GC");
      args.add("-XX:MaxGCPauseMillis=100"); // More frequent GC to prevent large pauses
      args.add("-XX:G1HeapRegionSize=16m"); // Smaller regions for better memory management
      args.add("-XX:+UseStringDeduplication"); // Reduce memory for bit sequences
      args.add("-XX:StringDeduplicationAgeThreshold=1");
      args.add("-XX:MaxMetaspaceSize=512m"); // Strict metaspace limit
      args.add("-XX:+DisableExplicitGC"); // Prevent manual GC calls that might cause issues
      args.add("-XX:+UseCompressedOops"); // Reduce pointer overhead
      args.add("-XX:+UseCompressedClassPointers");
      args.add("-XX:G1MixedGCCountTarget=8"); // More aggressive mixed GC
      args.add("-XX:G1HeapWastePercent=5"); // Lower waste threshold
      args.add("-XX:G1MixedGCLiveThresholdPercent=25"); // More aggressive cleanup
      args.add("-XX:G1OldCSetRegionThresholdPercent=5"); // Smaller old gen collection sets
      return args;
    }
    
    void logSafetyWarnings() {
      // Safety check: warn if total memory usage might be too high
      long totalMemoryUsageMB = mainProcessMemoryMB + (workerMemoryMB * threadCount);
      if (totalMemoryUsageMB > systemMemoryMB * 0.9) {
        Log.getLogger().severe("CRITICAL: Total memory usage (" + totalMemoryUsageMB + "MB) would exceed "
                              + "90% of system memory (" + systemMemoryMB + "MB)");
        Log.getLogger().severe("This configuration will likely cause system instability or OOM killer activation");
        
        // Provide specific recommendations
        if (threadCount > 8) {
          Log.getLogger().severe("RECOMMENDATION: For " + threadCount + " threads on 32GB system:");
          Log.getLogger().severe("  - Use main process -Xmx16g (instead of " + (mainProcessMemoryMB / 1024) + "g)");
          Log.getLogger().severe("  - This will allow workers to use ~1GB each");
          Log.getLogger().severe("  - Or reduce threads to 4-8 for better memory per worker");
        }
      } else {
        Log.getLogger().info("Memory allocation looks good: " + totalMemoryUsageMB + "MB total ("
                            + ((totalMemoryUsageMB * 100) / systemMemoryMB) + "% of system memory)");
      }
      
      // Provide optimization recommendations for high thread counts
      if (threadCount > 8 && systemMemoryMB <= 32768) { // 32GB or less
        Log.getLogger().warning("High thread count (" + threadCount + ") on 32GB system detected");
        Log.getLogger().warning("Each worker will get only " + workerMemoryMB + "MB - this may cause frequent OOM errors");
        Log.getLogger().warning("Consider reducing threads to 4-8 for more stable execution");
      }
      
      // Log current memory status
      Runtime runtime = Runtime.getRuntime();
      long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
      long totalMemory = runtime.totalMemory() / (1024 * 1024);
      long freeMemory = runtime.freeMemory() / (1024 * 1024);
      long usedMemory = totalMemory - freeMemory;
      
      Log.getLogger().info("Main process memory status - Max: " + maxMemory + "MB, Used: " + usedMemory + "MB, Free: " + freeMemory + "MB");
      
      if (usedMemory > maxMemory * 0.8) {
        Log.getLogger().warning("Main process is already using " + ((usedMemory * 100) / maxMemory)
                               + "% of available memory. Worker processes may face memory pressure.");
      }
    }
  }
}
