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
package org.pitest.junit;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runners.Parameterized;
import junit.framework.TestCase;
import org.pitest.functional.FCollection;
import org.pitest.junit.adapter.AdaptedJUnitTestUnit;
import org.pitest.reflection.IsAnnotatedWith;
import org.pitest.reflection.Reflection;
import org.pitest.testapi.TestGroupConfig;
import org.pitest.testapi.TestUnit;
import org.pitest.testapi.TestUnitExecutionListener;
import org.pitest.testapi.TestUnitFinder;
import org.pitest.util.IsolationUtils;

public class JUnitCustomRunnerTestUnitFinder implements TestUnitFinder {

  @SuppressWarnings("rawtypes")
  private static final Optional<Class> CLASS_RULE = findClassRuleClass();


  private final TestGroupConfig config;
  private final Collection<String> excludedRunners;
  private final Collection<String> includedTestMethods;

  JUnitCustomRunnerTestUnitFinder(TestGroupConfig config, final Collection<String> excludedRunners,
                                  final Collection<String> includedTestMethods) {
    Objects.requireNonNull(config);
    this.config = config;
    this.excludedRunners = excludedRunners;
    this.includedTestMethods = includedTestMethods;
  }

  @Override
  public List<TestUnit> findTestUnits(final Class<?> clazz, TestUnitExecutionListener unused) {

    final Runner runner = AdaptedJUnitTestUnit.createRunner(clazz);

    // Return empty list if any exclusion condition is met
    if (isExcluded(runner) || isNotARunnableTest(runner, clazz.getName()) || !isIncluded(clazz)) {
      return Collections.emptyList();
    }

    // // Handle JUnit 3 test classes specially to discover individual test methods
    // if (isJUnit3TestClass(clazz)) {
    //   return discoverJUnit3TestMethods(clazz);
    // }

    if (Filterable.class.isAssignableFrom(runner.getClass())
        && !shouldTreatAsOneUnit(clazz, runner)) {
      final List<TestUnit> filteredUnits = splitIntoFilteredUnits(runner.getDescription());
      return filterUnitsByMethod(filteredUnits);
    } else {
      return Collections.singletonList(new AdaptedJUnitTestUnit(
          clazz, Optional.empty()));
    }
  }

  private List<TestUnit> filterUnitsByMethod(List<TestUnit> filteredUnits) {
    if (this.includedTestMethods.isEmpty()) {
      return filteredUnits;
    }

    final List<TestUnit> units = new ArrayList<>();
    for (final TestUnit unit: filteredUnits) {
      if (this.includedTestMethods.contains(unit.getDescription().getName().split("\\(")[0])) {
        units.add(unit);
      }
    }
    return units;
  }

  private boolean isExcluded(Runner runner) {
    return this.excludedRunners.contains(runner.getClass().getName());
  }

  private boolean isIncluded(final Class<?> a) {
    return isIncludedCategory(a) && !isExcludedCategory(a);
  }


  private boolean isIncludedCategory(final Class<?> a) {
    final List<String> included = this.config.getIncludedGroups();
    return included.isEmpty() || !Collections.disjoint(included, getCategories(a));
  }

  private boolean isExcludedCategory(final Class<?> a) {
    final List<String> excluded = this.config.getExcludedGroups();
    return !excluded.isEmpty() && !Collections.disjoint(excluded, getCategories(a));
  }

  private List<String> getCategories(final Class<?> a) {
    final Category c = a.getAnnotation(Category.class);
    return Stream.of(c)
            .flatMap(toCategoryNames())
            .collect(Collectors.toList());
  }

  private Function<Category, Stream<String>> toCategoryNames() {
    return a -> {
      if (a == null) {
        return Stream.empty();
      }
      return Stream.of(a.value())
              .map(Class::getName);
    };
  }

  private boolean isNotARunnableTest(final Runner runner,
      final String className) {
    try {
      return (runner == null)
          || runner.getClass().isAssignableFrom(ErrorReportingRunner.class)
          || isParameterizedTest(runner)
          || isAJUnitThreeErrorOrWarning(runner)
          || isJUnitThreeSuiteMethodNotForOwnClass(runner, className);
    } catch (final RuntimeException ex) {
      // some runners (looking at you spock) can throw a runtime exception
      // when the getDescription method is called
      return true;
    }
  }

  private boolean isAJUnitThreeErrorOrWarning(final Runner runner) {
    return !runner.getDescription().getChildren().isEmpty()
        && runner.getDescription().getChildren().get(0).getClassName()
            .startsWith("junit.framework.TestSuite");
  }

  private boolean shouldTreatAsOneUnit(final Class<?> clazz, final Runner runner) {
    final Set<Method> methods = Reflection.allMethods(clazz);
    return runnerCannotBeSplit(runner)
        || hasAnnotation(methods, BeforeClass.class)
        || hasAnnotation(methods, AfterClass.class)
        || hasClassRuleAnnotations(clazz, methods);
  }

  private boolean hasClassRuleAnnotations(final Class<?> clazz,
      final Set<Method> methods) {
    return CLASS_RULE.filter(aClass -> hasAnnotation(methods, aClass)
            || hasAnnotation(Reflection.publicFields(clazz), aClass)).isPresent();
  }

  private boolean hasAnnotation(final Set<? extends AccessibleObject> methods,
      final Class<? extends Annotation> annotation) {
    return FCollection.contains(methods, IsAnnotatedWith.instance(annotation));
  }

  private boolean isParameterizedTest(final Runner runner) {
    return Parameterized.class.isAssignableFrom(runner.getClass());
  }

  private boolean runnerCannotBeSplit(final Runner runner) {
    final String runnerName = runner.getClass().getName();
    return runnerName.equals("junitparams.JUnitParamsRunner")
        || runnerName.startsWith("org.spockframework.runtime.Sputnik")
        || runnerName.startsWith("com.insightfullogic.lambdabehave")
        || runnerName.startsWith("com.googlecode.yatspec")
        || runnerName.startsWith("com.google.gwtmockito.GwtMockitoTestRunner");
  }

  private boolean isJUnitThreeSuiteMethodNotForOwnClass(final Runner runner,
      final String className) {
    // use strings in case this hack blows up due to internal junit change
    if (!runner.getClass().getName().equals("org.junit.internal.runners.SuiteMethod")) {
      return false;
    }
    
    String descriptionClassName = runner.getDescription().getClassName();
    
    // If the description class name matches exactly, it's for this class
    if (descriptionClassName.equals(className)) {
      return false;
    }
    
    // If the description is a display name (like "ClassUtils Tests"), 
    // check if it could be a reasonable display name for this class
    if (descriptionClassName != null && !descriptionClassName.contains(".")) {
      // This looks like a display name, not a full class name, so likely for this class
      return false;
    }
    
    // Only reject if it's clearly a different class (has package name and doesn't match)
    return true;
  }

  private List<TestUnit> splitIntoFilteredUnits(final Description description) {
    return description.getChildren().stream()
        .filter(Description::isTest)
        .map(this::descriptionToTest)
        .collect(Collectors.toList());
  }

  private TestUnit descriptionToTest(final Description description) {

    Class<?> clazz = description.getTestClass();
    if (clazz == null) {
      clazz = IsolationUtils.convertForClassLoader(
          IsolationUtils.getContextClassLoader(), description.getClassName());
    }
    return new AdaptedJUnitTestUnit(clazz,
        Optional.ofNullable(createFilterFor(description)));
  }

  private Filter createFilterFor(final Description description) {
    return new DescriptionFilter(description.toString());
  }

  @SuppressWarnings("rawtypes")
  private static Optional<Class> findClassRuleClass() {
    try {
      return Optional.ofNullable(Class.forName("org.junit.ClassRule"));
    } catch (final ClassNotFoundException ex) {
      return Optional.empty();
    }
  }

  /**
   * Check if the class is a JUnit 3 test class (extends TestCase)
   * but doesn't use JUnit 4+ annotations like @RunWith
   */
  private boolean isJUnit3TestClass(final Class<?> clazz) {
    return TestCase.class.isAssignableFrom(clazz) 
           && !hasJUnit4Annotations(clazz);
  }

  /**
   * Check if the class has JUnit 4+ annotations that would override JUnit 3 behavior
   */
  private boolean hasJUnit4Annotations(final Class<?> clazz) {
    try {
      // Check for @RunWith annotation which indicates JUnit 4+ usage
      return clazz.getAnnotations().length > 0 
             && hasRunWithAnnotation(clazz);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Check for RunWith annotation specifically
   */
  private boolean hasRunWithAnnotation(final Class<?> clazz) {
    for (Annotation annotation : clazz.getAnnotations()) {
      if (annotation.annotationType().getName().equals("org.junit.runner.RunWith")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Discover individual test methods in JUnit 3 test classes
   */
  private List<TestUnit> discoverJUnit3TestMethods(final Class<?> clazz) {
    final List<TestUnit> testUnits = new ArrayList<>();
    
    // Get all methods from the class
    final Method[] methods = clazz.getMethods();
    
    for (final Method method : methods) {
      // JUnit 3 convention: public methods starting with "test" and taking no parameters
      if (isJUnit3TestMethod(method)) {
        final String methodName = method.getName();
        
        // Apply method filtering if specified
        if (this.includedTestMethods.isEmpty() 
            || this.includedTestMethods.contains(methodName)) {
          
          // Create a test unit for this specific method
          final AdaptedJUnitTestUnit testUnit = new AdaptedJUnitTestUnit(
              clazz, Optional.of(createMethodFilter(clazz, methodName)));
          testUnits.add(testUnit);
        }
      }
    }
    
    return testUnits;
  }

  /**
   * Check if a method is a JUnit 3 test method
   */
  private boolean isJUnit3TestMethod(final Method method) {
    return method.getName().startsWith("test")
           && method.getParameterCount() == 0
           && java.lang.reflect.Modifier.isPublic(method.getModifiers())
           && !java.lang.reflect.Modifier.isStatic(method.getModifiers());
  }

  /**
   * Create a filter for a specific test method
   */
  private Filter createMethodFilter(final Class<?> clazz, final String methodName) {
    return new DescriptionFilter(clazz.getName() + "." + methodName);
  }

}
