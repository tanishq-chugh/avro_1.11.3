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
package org.apache.avro.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileConstants;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryData;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestDataFileRepairTool {

  @TempDir
  public static File DIR;

  private static final Schema SCHEMA = Schema.create(Schema.Type.STRING);
  private static File corruptBlockFile;
  private static File corruptRecordFile;

  private File repairedFile;

  @BeforeAll
  public static void writeCorruptFile() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    long pos;
    // Write a data file
    try (DataFileWriter<Utf8> w = new DataFileWriter<>(new GenericDatumWriter<>(SCHEMA))) {
      w.create(SCHEMA, baos);
      w.append(new Utf8("apple"));
      w.append(new Utf8("banana"));
      w.append(new Utf8("celery"));
      w.sync();
      w.append(new Utf8("date"));
      w.append(new Utf8("endive"));
      w.append(new Utf8("fig"));
      pos = w.sync();
      w.append(new Utf8("guava"));
      w.append(new Utf8("hazelnut"));
    }

    byte[] original = baos.toByteArray();

    // Corrupt the second block by inserting some zero bytes before the sync marker
    int corruptPosition = (int) pos - DataFileConstants.SYNC_SIZE;
    int corruptedBytes = 3;
    byte[] corrupted = new byte[original.length + corruptedBytes];
    System.arraycopy(original, 0, corrupted, 0, corruptPosition);
    System.arraycopy(original, corruptPosition, corrupted, corruptPosition + corruptedBytes,
        original.length - corruptPosition);

    corruptBlockFile = new File(DIR, "corruptBlock.avro");
    corruptBlockFile.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(corruptBlockFile)) {
      out.write(corrupted);
    }

    // Corrupt the "endive" record by changing the length of the string to be
    // negative
    corruptPosition = (int) pos - DataFileConstants.SYNC_SIZE - (1 + "fig".length() + 1 + "endive".length());
    corrupted = new byte[original.length];
    System.arraycopy(original, 0, corrupted, 0, original.length);
    BinaryData.encodeLong(-1, corrupted, corruptPosition);

    corruptRecordFile = new File(DIR, "corruptRecord.avro");
    corruptRecordFile.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(corruptRecordFile)) {
      out.write(corrupted);
    }
  }

  @BeforeEach
  public void setUp() {
    repairedFile = new File(DIR, "repaired.avro");
  }

  private String run(Tool tool, String... args) throws Exception {
    return run(tool, null, args);
  }

  private String run(Tool tool, InputStream stdin, String... args) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream stdout = new PrintStream(out);
    tool.run(stdin, stdout, System.err, Arrays.asList(args));
    return out.toString("UTF-8").replace("\r", "");
  }

  @Test
  void reportCorruptBlock() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "report", corruptBlockFile.getPath());
    assertTrue(output.contains("Number of blocks: 2 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 5 Number of corrupt records: 0"), output);
  }

  @Test
  void reportCorruptRecord() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "report", corruptRecordFile.getPath());
    assertTrue(output.contains("Number of blocks: 3 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 8 Number of corrupt records: 2"), output);
  }

  @Test
  void repairAllCorruptBlock() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "all", corruptBlockFile.getPath(), repairedFile.getPath());
    assertTrue(output.contains("Number of blocks: 2 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 5 Number of corrupt records: 0"), output);
    checkFileContains(repairedFile, "apple", "banana", "celery", "guava", "hazelnut");
  }

  @Test
  void repairAllCorruptRecord() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "all", corruptRecordFile.getPath(), repairedFile.getPath());
    assertTrue(output.contains("Number of blocks: 3 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 8 Number of corrupt records: 2"), output);
    checkFileContains(repairedFile, "apple", "banana", "celery", "date", "guava", "hazelnut");
  }

  @Test
  void repairPriorCorruptBlock() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "prior", corruptBlockFile.getPath(), repairedFile.getPath());
    assertTrue(output.contains("Number of blocks: 2 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 5 Number of corrupt records: 0"), output);
    checkFileContains(repairedFile, "apple", "banana", "celery");
  }

  @Test
  void repairPriorCorruptRecord() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "prior", corruptRecordFile.getPath(), repairedFile.getPath());
    assertTrue(output.contains("Number of blocks: 3 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 8 Number of corrupt records: 2"), output);
    checkFileContains(repairedFile, "apple", "banana", "celery", "date");
  }

  @Test
  void repairAfterCorruptBlock() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "after", corruptBlockFile.getPath(), repairedFile.getPath());
    assertTrue(output.contains("Number of blocks: 2 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 5 Number of corrupt records: 0"), output);
    checkFileContains(repairedFile, "guava", "hazelnut");
  }

  @Test
  void repairAfterCorruptRecord() throws Exception {
    String output = run(new DataFileRepairTool(), "-o", "after", corruptRecordFile.getPath(), repairedFile.getPath());
    assertTrue(output.contains("Number of blocks: 3 Number of corrupt blocks: 1"), output);
    assertTrue(output.contains("Number of records: 8 Number of corrupt records: 2"), output);
    checkFileContains(repairedFile, "guava", "hazelnut");
  }

  private void checkFileContains(File repairedFile, String... lines) throws IOException {
    DataFileReader<Object> r = new DataFileReader<>(repairedFile, new GenericDatumReader<>(SCHEMA));
    for (String line : lines) {
      assertEquals(line, r.next().toString());
    }
    assertFalse(r.hasNext());
    r.close();
  }

}
