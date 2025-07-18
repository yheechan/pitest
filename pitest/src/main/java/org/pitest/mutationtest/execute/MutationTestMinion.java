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

import org.pitest.boot.HotSwapAgent;
import org.pitest.classinfo.CachingByteArraySource;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.mutationtest.EngineArguments;
import org.pitest.mutationtest.environment.ResetEnvironment;
import org.pitest.mutationtest.config.ClientPluginServices;
import org.pitest.mutationtest.config.MinionSettings;
import org.pitest.mutationtest.config.TestPluginArguments;
import org.pitest.mutationtest.engine.MutationEngine;
import org.pitest.mutationtest.environment.TransformationPlugin;
import org.pitest.testapi.Configuration;
import org.pitest.testapi.TestUnit;
import org.pitest.testapi.execute.FindTestUnits;
import org.pitest.util.ExitCode;
import org.pitest.util.IsolationUtils;
import org.pitest.util.Log;
import org.pitest.util.SafeDataInputStream;

import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.management.MemoryNotificationInfo;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MutationTestMinion {

  private static final Logger       LOG = Log.getLogger();

  // We maintain a small cache to avoid reading byte code off disk more than once
  // Size is arbitrary but assumed to be large enough to cover likely max number of inner classes
  private static final int CACHE_SIZE = 12;

  private final SafeDataInputStream dis;
  private final Reporter            reporter;
  private final MinionSettings      plugins;

  public MutationTestMinion(MinionSettings plugins, final SafeDataInputStream dis,
      final Reporter reporter) {
    this.dis = dis;
    this.reporter = reporter;
    this.plugins = plugins;
  }

  public void run() {
    try {

      final MinionArguments paramsFromParent = this.dis
          .read(MinionArguments.class);

      configureVerbosity(paramsFromParent);

      final ClassLoader loader = IsolationUtils.getContextClassLoader();

      final ClassByteArraySource byteSource = new CachingByteArraySource(new ClassloaderByteArraySource(
          loader), CACHE_SIZE);

      final HotSwap hotswap = new HotSwap();

      final MutationEngine engine = createEngine(paramsFromParent.engine, paramsFromParent.engineArgs);

      final ResetEnvironment reset = this.plugins.createReset();

      final MutationTestWorker worker = new MutationTestWorker(hotswap,
          engine.createMutator(byteSource), loader, reset, paramsFromParent.fullMutationMatrix, paramsFromParent.fullMatrixResearchMode, paramsFromParent.reportDir, paramsFromParent.testCaseMetadata);

      final List<TestUnit> tests = findTestsForTestClasses(loader,
          paramsFromParent.testClasses, createTestPlugin(paramsFromParent.pitConfig));

      worker.run(paramsFromParent.mutations, this.reporter,
          new TimeOutDecoratedTestSource(paramsFromParent.timeoutStrategy,
              tests, this.reporter));

      this.reporter.done(ExitCode.OK);

      // rudely kill the vm in case it is kept alive
      // by threads launched by client
      System.exit(0);

    } catch (final Throwable ex) {
      ex.printStackTrace(System.out);
      LOG.log(Level.WARNING, "Error during mutation test", ex);
      this.reporter.done(ExitCode.UNKNOWN_ERROR);
    }

  }

  private void configureVerbosity(MinionArguments paramsFromParent) {
    Log.setVerbose(paramsFromParent.verbosity());
    if (!paramsFromParent.verbosity().showMinionOutput()) {
      Log.disable();
    }
  }

  private MutationEngine createEngine(String engine, EngineArguments args) {
    return this.plugins.createEngine(engine).createEngine(args);
  }

  private Configuration createTestPlugin(TestPluginArguments pitConfig) {
    return this.plugins.getTestFrameworkPlugin(pitConfig, ClassloaderByteArraySource.fromContext());
  }

  public static void main(final String[] args) {
    LOG.fine(() -> "minion started");

    enableTransformations();
    HotSwapAgent.addTransformer(new CatchNewClassLoadersTransformer());

    final int port = Integer.parseInt(args[0]);

    Socket s = null;
    try {
      s = new Socket("localhost", port);
      // if we can't read/write in 20 seconds, something is badly wrong
      s.setSoTimeout(20000);
      final SafeDataInputStream dis = new SafeDataInputStream(
          s.getInputStream());

      final Reporter reporter = new DefaultReporter(s.getOutputStream());
      addMemoryWatchDog(reporter);
      final ClientPluginServices plugins = ClientPluginServices.makeForContextLoader();
      final MinionSettings factory = new MinionSettings(plugins);
      final MutationTestMinion instance = new MutationTestMinion(factory, dis, reporter);
      instance.run();
    } catch (final Throwable ex) {
      ex.printStackTrace(System.out);
      LOG.log(Level.WARNING, "Error during mutation test", ex);
    } finally {
      if (s != null) {
        safelyCloseSocket(s);
      }
    }

  }

  private static List<TestUnit> findTestsForTestClasses(
      final ClassLoader loader, final Collection<ClassName> testClasses,
      final Configuration pitConfig) {

    final Collection<Class<?>> tcs = testClasses.stream()
            .flatMap(ClassName.nameToClass(loader))
            .collect(Collectors.toList());
    final FindTestUnits finder = new FindTestUnits(pitConfig);
    return finder.findTestUnitsForAllSuppliedClasses(tcs);
  }

  private static void enableTransformations() {
    ClientPluginServices plugins = ClientPluginServices.makeForContextLoader();
    for (TransformationPlugin each : plugins.findTransformations()) {
      ClassFileTransformer transformer = each.makeMutationTransformer();
      if (transformer != null) {
        HotSwapAgent.addTransformer(transformer);
      }
    }
  }

  private static void safelyCloseSocket(final Socket s) {
    if (s != null) {
      try {
        s.close();
      } catch (final IOException e) {
        LOG.log(Level.WARNING, "Couldn't close socket", e);
      }
    }
  }

  private static void addMemoryWatchDog(final Reporter r) {
    final NotificationListener listener = (notification, handback) -> {
    final String type = notification.getType();
    if (type.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
    final CompositeData cd = (CompositeData) notification.getUserData();
    final MemoryNotificationInfo memInfo = MemoryNotificationInfo
        .from(cd);
    CommandLineMessage.report(memInfo.getPoolName()
        + " has exceeded the shutdown threshold : " + memInfo.getCount()
        + " times.\n" + memInfo.getUsage());

    r.done(ExitCode.OUT_OF_MEMORY);

    } else {
      LOG.warning("Unknown notification: " + notification);
    }
   };

    MemoryWatchdog.addWatchDogToAllPools(90, listener);

  }

}
