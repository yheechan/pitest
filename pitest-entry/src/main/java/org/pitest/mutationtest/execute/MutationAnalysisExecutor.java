package org.pitest.mutationtest.execute;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationMetaData;
import org.pitest.mutationtest.MutationResultInterceptor;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.build.MutationAnalysisUnit;
import org.pitest.util.Log;
import org.pitest.util.Unchecked;

public class MutationAnalysisExecutor {

  private static final Logger                LOG = Log.getLogger();

  private final List<MutationResultListener> listeners;
  private final ThreadPoolExecutor           executor;
  private final boolean                       isSaveMutantBytecode;

  private final MutationResultInterceptor resultInterceptor;

  public MutationAnalysisExecutor(int numberOfThreads, MutationResultInterceptor interceptor,
      List<MutationResultListener> listeners) {
    this(numberOfThreads, interceptor, listeners, false);
  }

  public MutationAnalysisExecutor(int numberOfThreads, MutationResultInterceptor interceptor,
      List<MutationResultListener> listeners, boolean isSaveMutantBytecode) {
    this.resultInterceptor = interceptor;
    this.listeners = listeners;
    this.isSaveMutantBytecode = isSaveMutantBytecode;
    this.executor = new ThreadPoolExecutor(numberOfThreads, numberOfThreads,
        10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
        Executors.defaultThreadFactory());
  }

  // entry point for mutation testing
  public void run(final List<MutationAnalysisUnit> testUnits) {

    LOG.fine("Running " + testUnits.size() + " units");

    // Skip listeners entirely when only saving bytecode
    if (!this.isSaveMutantBytecode) {
      signalRunStartToAllListeners();
    }

    final List<Future<MutationMetaData>> results = new ArrayList<>(
        testUnits.size());

    for (final MutationAnalysisUnit unit : testUnits) {
      results.add(this.executor.submit(unit));
    }

    this.executor.shutdown();

    try {
      if (this.isSaveMutantBytecode) {
        // For bytecode-only mode, just wait for completion without processing results
        processResultsForBytecodeOnly(results);
      } else {
        // Standard processing with listeners
        processResult(results);
      }
    } catch (final InterruptedException | ExecutionException e) {
      throw Unchecked.translateCheckedException(e);
    }

    // Skip listeners entirely when only saving bytecode
    if (!this.isSaveMutantBytecode) {
      signalRunEndToAllListeners();
    }

  }

  private void processResultsForBytecodeOnly(List<Future<MutationMetaData>> results)
          throws InterruptedException, ExecutionException {
    // For bytecode-only mode, we just need to wait for all tasks to complete
    // We don't need to process the results through listeners
    for (Future<MutationMetaData> f : results) {
      f.get(); // Wait for completion, but discard results
    }
    
    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine("Completed " + results.size() + " bytecode generation tasks");
    }
  }

  private void processResult(List<Future<MutationMetaData>> results)
          throws InterruptedException, ExecutionException {
    for (Future<MutationMetaData> f : results) {
      MutationMetaData metaData = f.get();
      for (ClassMutationResults cr : resultInterceptor.modify(metaData.toClassResults())) {
        for (MutationResultListener listener : this.listeners) {
          listener.handleMutationResult(cr);
        }
      }
    }

    // handle any results held back from processing. Only known
    // use case here is inlined code consolidation.
    for (ClassMutationResults each : resultInterceptor.remaining()) {
      for (MutationResultListener listener : this.listeners) {
        listener.handleMutationResult(each);
      }
    }

  }

  private void signalRunStartToAllListeners() {
    this.listeners.forEach(MutationResultListener::runStart);
  }

  private void signalRunEndToAllListeners() {
    this.listeners.forEach(MutationResultListener::runEnd);
  }

}
