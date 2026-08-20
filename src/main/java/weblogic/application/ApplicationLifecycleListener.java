/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package weblogic.application;

/**
 * Quarkus Port Note: This is a WebLogic-specific abstract class.
 * In Quarkus, application lifecycle events are handled via CDI lifecycle annotations:
 * - @PostConstruct (runs after dependency injection)
 * - @PreDestroy (runs before bean destruction)
 * - Quarkus startup/shutdown events: io.quarkus.runtime.StartupEvent, io.quarkus.runtime.ShutdownEvent
 *
 * If legacy code depends on this class, implement using Quarkus lifecycle mechanisms instead.
 * For example, preStart() can be replaced with @javax.annotation.PostConstruct (jakarta.annotation.PostConstruct),
 * and postStop() can be replaced with @PreDestroy.
 */
public abstract class ApplicationLifecycleListener {

    public void postStart(ApplicationLifecycleEvent evt) {

    }

    public void postStop(ApplicationLifecycleEvent evt) {

    }

    public void preStart(ApplicationLifecycleEvent evt) {

    }

    public void preStop(ApplicationLifecycleEvent evt) {

    }
}
