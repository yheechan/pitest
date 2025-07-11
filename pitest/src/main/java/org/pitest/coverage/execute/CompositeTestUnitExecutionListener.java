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
package org.pitest.coverage.execute;

import org.pitest.testapi.Description;
import org.pitest.testapi.TestUnitExecutionListener;

/**
 * Composite test unit execution listener that forwards events to multiple listeners.
 * This allows us to register both coverage collection and detailed test result collection
 * during test discovery/execution.
 */
public class CompositeTestUnitExecutionListener implements TestUnitExecutionListener {
    
    private final TestUnitExecutionListener[] listeners;
    
    public CompositeTestUnitExecutionListener(TestUnitExecutionListener... listeners) {
        this.listeners = listeners != null ? listeners : new TestUnitExecutionListener[0];
    }
    
    @Override
    public void executionStarted(Description description) {
        for (TestUnitExecutionListener listener : listeners) {
            try {
                listener.executionStarted(description);
            } catch (Exception e) {
                // Continue with other listeners even if one fails
                System.err.println("Error in test execution listener: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void executionStarted(Description description, boolean suppressParallelWarning) {
        for (TestUnitExecutionListener listener : listeners) {
            try {
                listener.executionStarted(description, suppressParallelWarning);
            } catch (Exception e) {
                // Continue with other listeners even if one fails
                System.err.println("Error in test execution listener: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void executionFinished(Description description, boolean passed, Throwable error) {
        for (TestUnitExecutionListener listener : listeners) {
            try {
                listener.executionFinished(description, passed, error);
            } catch (Exception e) {
                // Continue with other listeners even if one fails
                System.err.println("Error in test execution listener: " + e.getMessage());
            }
        }
    }
}
