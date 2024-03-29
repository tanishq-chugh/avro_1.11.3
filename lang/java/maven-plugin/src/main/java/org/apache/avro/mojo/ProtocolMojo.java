/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avro.mojo;

import org.apache.avro.Protocol;

import java.io.File;
import java.io.IOException;

/**
 * Generate Java classes and interfaces from Avro protocol files (.avpr)
 *
 * @goal protocol
 * @phase generate-sources
 * @requiresDependencyResolution runtime
 * @threadSafe
 */
public class ProtocolMojo extends AbstractAvroMojo {
  /**
   * A set of Ant-like inclusion patterns used to select files from the source
   * directory for processing. By default, the pattern <code>**&#47;*.avpr</code>
   * is used to select grammar files.
   *
   * @parameter
   */
  private String[] includes = new String[] { "**/*.avpr" };

  /**
   * A set of Ant-like inclusion patterns used to select files from the source
   * directory for processing. By default, the pattern <code>**&#47;*.avpr</code>
   * is used to select grammar files.
   *
   * @parameter
   */
  private String[] testIncludes = new String[] { "**/*.avpr" };

  @Override
  protected void doCompile(String filename, File sourceDirectory, File outputDirectory) throws IOException {
    final File src = new File(sourceDirectory, filename);
    final Protocol protocol = Protocol.parse(src);
    doCompile(src, protocol, outputDirectory);
  }

  @Override
  protected String[] getIncludes() {
    return includes;
  }

  @Override
  protected String[] getTestIncludes() {
    return testIncludes;
  }
}
