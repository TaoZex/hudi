/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.hadoop;

import org.apache.hudi.common.minicluster.HdfsTestService;
import org.apache.hudi.common.model.HoodieAvroPayload;
import org.apache.hudi.common.model.HoodieTableType;
import org.apache.hudi.common.table.HoodieTableConfig;
import org.apache.hudi.common.table.HoodieTableMetaClient;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class InputPathHandlerTest {

  // Incremental Table
  public static final String RAW_TRIPS_TEST_NAME = "raw_trips";
  public static final String MODEL_TRIPS_TEST_NAME = "model_trips";

  // snapshot Table
  public static final String ETL_TRIPS_TEST_NAME = "etl_trips";

  // non Hoodie table
  public static final String TRIPS_STATS_TEST_NAME = "trips_stats";

  private static MiniDFSCluster dfsCluster;
  private static DistributedFileSystem dfs;
  private static HdfsTestService hdfsTestService;
  private static InputPathHandler inputPathHandler;
  private static String basePathTable1 = null;
  private static String basePathTable2 = null;
  private static String basePathTable3 = null;
  private static String basePathTable4 = null; // non hoodie Path
  private static List<String> incrementalTables;
  private static List<Path> incrementalPaths;
  private static List<Path> snapshotPaths;
  private static List<Path> nonHoodiePaths;
  private static List<Path> inputPaths;

  @BeforeClass
  public static void setUpDFS() throws IOException {
    // Need to closeAll to clear FileSystem.Cache, required because DFS and LocalFS used in the
    // same JVM
    FileSystem.closeAll();
    if (hdfsTestService == null) {
      hdfsTestService = new HdfsTestService();
      dfsCluster = hdfsTestService.start(true);
      // Create a temp folder as the base path
      dfs = dfsCluster.getFileSystem();
    }
    inputPaths = new ArrayList<>();
    incrementalPaths = new ArrayList<>();
    snapshotPaths = new ArrayList<>();
    nonHoodiePaths = new ArrayList<>();
    initTables();
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    if (hdfsTestService != null) {
      hdfsTestService.stop();
      dfsCluster.shutdown();
      dfsCluster = null;
      dfs = null;
      hdfsTestService = null;
    }
    // Need to closeAll to clear FileSystem.Cache, required because DFS and LocalFS used in the
    // same JVM
    FileSystem.closeAll();
  }

  static void initTables() throws IOException {
    // Create a temp folder as the base path
    TemporaryFolder parentFolder = new TemporaryFolder();
    parentFolder.create();
    basePathTable1 = parentFolder.newFolder(RAW_TRIPS_TEST_NAME).getAbsolutePath();
    basePathTable2 = parentFolder.newFolder(MODEL_TRIPS_TEST_NAME).getAbsolutePath();
    basePathTable3 = parentFolder.newFolder(ETL_TRIPS_TEST_NAME).getAbsolutePath();
    basePathTable4 = parentFolder.newFolder(TRIPS_STATS_TEST_NAME).getAbsolutePath();

    dfs.mkdirs(new Path(basePathTable1));
    initTableType(dfs.getConf(), basePathTable1, RAW_TRIPS_TEST_NAME, HoodieTableType.MERGE_ON_READ);
    incrementalPaths.addAll(generatePartitions(dfs, basePathTable1));

    dfs.mkdirs(new Path(basePathTable2));
    initTableType(dfs.getConf(), basePathTable2, MODEL_TRIPS_TEST_NAME, HoodieTableType.MERGE_ON_READ);
    incrementalPaths.addAll(generatePartitions(dfs, basePathTable2));

    dfs.mkdirs(new Path(basePathTable3));
    initTableType(dfs.getConf(), basePathTable3, ETL_TRIPS_TEST_NAME, HoodieTableType.COPY_ON_WRITE);
    snapshotPaths.addAll(generatePartitions(dfs, basePathTable3));

    dfs.mkdirs(new Path(basePathTable4));
    nonHoodiePaths.addAll(generatePartitions(dfs, basePathTable4));

    inputPaths.addAll(incrementalPaths);
    inputPaths.addAll(snapshotPaths);
    inputPaths.addAll(nonHoodiePaths);

    incrementalTables = new ArrayList<>();
    incrementalTables.add(RAW_TRIPS_TEST_NAME);
    incrementalTables.add(MODEL_TRIPS_TEST_NAME);
  }

  static HoodieTableMetaClient initTableType(Configuration hadoopConf, String basePath,
                                             String tableName, HoodieTableType tableType) throws IOException {
    Properties properties = new Properties();
    properties.setProperty(HoodieTableConfig.HOODIE_TABLE_NAME_PROP_NAME, tableName);
    properties.setProperty(HoodieTableConfig.HOODIE_TABLE_TYPE_PROP_NAME, tableType.name());
    properties.setProperty(HoodieTableConfig.HOODIE_PAYLOAD_CLASS_PROP_NAME, HoodieAvroPayload.class.getName());
    return HoodieTableMetaClient.initTableAndGetMetaClient(hadoopConf, basePath, properties);
  }

  static List<Path> generatePartitions(DistributedFileSystem dfs, String basePath)
      throws IOException {
    List<Path> paths = new ArrayList<>();
    paths.add(new Path(basePath + Path.SEPARATOR + "2019/05/21"));
    paths.add(new Path(basePath + Path.SEPARATOR + "2019/05/22"));
    paths.add(new Path(basePath + Path.SEPARATOR + "2019/05/23"));
    paths.add(new Path(basePath + Path.SEPARATOR + "2019/05/24"));
    paths.add(new Path(basePath + Path.SEPARATOR + "2019/05/25"));
    for (Path path: paths) {
      dfs.mkdirs(path);
    }
    return paths;
  }

  @Test
  public void testInputPathHandler() throws IOException {
    inputPathHandler = new InputPathHandler(dfs.getConf(), inputPaths.toArray(
        new Path[0]), incrementalTables);
    List<Path> actualPaths = inputPathHandler.getGroupedIncrementalPaths().values().stream()
        .flatMap(List::stream).collect(Collectors.toList());
    assertTrue(actualComparesToExpected(actualPaths, incrementalPaths));
    actualPaths = inputPathHandler.getSnapshotPaths();
    assertTrue(actualComparesToExpected(actualPaths, snapshotPaths));
    actualPaths = inputPathHandler.getNonHoodieInputPaths();
    assertTrue(actualComparesToExpected(actualPaths, nonHoodiePaths));
  }

  private boolean actualComparesToExpected(List<Path> actualPaths, List<Path> expectedPaths) {
    if (actualPaths.size() != expectedPaths.size()) {
      return false;
    }
    for (Path path: actualPaths) {
      if (!expectedPaths.contains(path)) {
        return false;
      }
    }
    return true;
  }
}
