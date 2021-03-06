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

package org.apache.solr.common.cloud;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.Utils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with solr cluster properties
 *
 * Note that all methods on this class make calls to ZK on every invocation.  For
 * read-only eventually-consistent uses, clients should instead call
 * {@link ZkStateReader#getClusterProperty(String, Object)}
 */
public class ClusterProperties {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


  private final SolrZkClient client;

  /**
   * Creates a ClusterProperties object using a provided SolrZkClient
   */
  public ClusterProperties(SolrZkClient client) {
    this.client = client;
  }

  /**
   * Read the value of a cluster property, returning a default if it is not set
   * @param key           the property name or the full path to the property.
   * @param defaultValue  the default value
   * @param <T>           the type of the property
   * @return the property value
   * @throws IOException if there is an error reading the value from the cluster
   */
  @SuppressWarnings("unchecked")
  public <T> T getClusterProperty(String key, T defaultValue) throws IOException {
    T value = (T) Utils.getObjectByPath(getClusterProperties(), false, key);
    if (value == null)
      return defaultValue;
    return value;
  }

  /**
   * Return the cluster properties
   * @throws IOException if there is an error reading properties from the cluster
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getClusterProperties() throws IOException {
    try {
      return (Map<String, Object>) Utils.fromJSON(client.getData(ZkStateReader.CLUSTER_PROPS, null, new Stat(), true));
    } catch (KeeperException.NoNodeException e) {
      return Collections.emptyMap();
    } catch (KeeperException | InterruptedException e) {
      throw new IOException("Error reading cluster property", SolrZkClient.checkInterrupted(e));
    }
  }

  public void setClusterProperties(Map<String, Object> properties) throws IOException, KeeperException, InterruptedException {
    client.atomicUpdate(ZkStateReader.CLUSTER_PROPS, zkData -> {
      if (zkData == null) return Utils.toJSON(properties);
      Map<String, Object> zkJson = (Map<String, Object>) Utils.fromJSON(zkData);
      boolean modified = Utils.mergeJson(zkJson, properties);
      return modified ? Utils.toJSON(zkJson) : null;
    });
  }

  /**
   * This method sets a cluster property.
   *
   * @param propertyName  The property name to be set.
   * @param propertyValue The value of the property.
   * @throws IOException if there is an error writing data to the cluster
   */
  @SuppressWarnings("unchecked")
  public void setClusterProperty(String propertyName, String propertyValue) throws IOException {

    if (!ZkStateReader.KNOWN_CLUSTER_PROPS.contains(propertyName)) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Not a known cluster property " + propertyName);
    }

    for (; ; ) {
      Stat s = new Stat();
      try {
        if (client.exists(ZkStateReader.CLUSTER_PROPS, true)) {
          Map properties = (Map) Utils.fromJSON(client.getData(ZkStateReader.CLUSTER_PROPS, null, s, true));
          if (propertyValue == null) {
            //Don't update ZK unless absolutely necessary.
            if (properties.get(propertyName) != null) {
              properties.remove(propertyName);
              client.setData(ZkStateReader.CLUSTER_PROPS, Utils.toJSON(properties), s.getVersion(), true);
            }
          } else {
            //Don't update ZK unless absolutely necessary.
            if (!propertyValue.equals(properties.get(propertyName))) {
              properties.put(propertyName, propertyValue);
              client.setData(ZkStateReader.CLUSTER_PROPS, Utils.toJSON(properties), s.getVersion(), true);
            }
          }
        } else {
          Map properties = new LinkedHashMap();
          properties.put(propertyName, propertyValue);
          client.create(ZkStateReader.CLUSTER_PROPS, Utils.toJSON(properties), CreateMode.PERSISTENT, true);
        }
      } catch (KeeperException.BadVersionException | KeeperException.NodeExistsException e) {
        //race condition
        continue;
      } catch (InterruptedException | KeeperException e) {
        throw new IOException("Error setting cluster property", SolrZkClient.checkInterrupted(e));
      }
      break;
    }
  }
}
