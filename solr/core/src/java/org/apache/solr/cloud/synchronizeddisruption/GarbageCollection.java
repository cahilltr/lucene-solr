/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.cloud.synchronizeddisruption;

import java.lang.invoke.MethodHandles;
import java.text.ParseException;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Garbage Collection implementation of a {@link SynchronizedDisruption}*/
public class GarbageCollection extends SynchronizedDisruption {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public GarbageCollection(ScheduledExecutorService executorService, String cronExpression) throws ParseException {
    super(executorService, cronExpression);
  }

  @Override
  void runDisruption() {
    log.info("Running System GC");
    System.gc();
  }
}