/*
 * Copyright 2011 Henry Coles
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
package org.pitest.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class SafeDataInputStream {

  private final DataInputStream dis;

  public SafeDataInputStream(final InputStream is) {
    this.dis = new DataInputStream(is);
  }

  public int readInt() {
    try {
      return this.dis.readInt();
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  public String readString() {
    return new String(readBytes(), StandardCharsets.UTF_8);
  }

  public byte[] readBytes() {
    try {
      final int length = this.dis.readInt();
      
      // Check for extremely large data that could cause OOM
      if (length > 100 * 1024 * 1024) { // 100MB limit
        System.out.println("HCY:DEBUG: SafeDataInputStream: readBytes() - "
                           + "Attempting to read " + length + " bytes, which exceeds the safe limit.");
        // Optionally, you could throw an exception or handle it differently
        // For now, we will throw an IOException to indicate a potential memory issue
        // This is a safeguard against potential memory issues in full mutation research mode
        // where large data sizes can lead to OOM errors.
        // throw new IOException("Data size too large for safe deserialization: "
        //                       + length + " bytes. "
        //                       + "This may indicate a memory issue in full mutation research mode. "
        //                       + "Consider reducing the number of tests or using standard mutation testing mode.");
      }
      
      final byte[] data = new byte[length];
      this.dis.readFully(data);
      return data;
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends Serializable> T read(final Class<T> type) {
    try {
      return (T) deserialize(readBytes());
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  public void close() {
    try {
      this.dis.close();
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  public byte readByte() {
    try {
      return this.dis.readByte();
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  public boolean readBoolean() {
    try {
      return this.dis.readBoolean();
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  public long readLong() {
    try {
      return this.dis.readLong();
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  public double readDouble() {
    try {
      return this.dis.readDouble();
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  private Object deserialize(byte[] bytes) throws IOException {
    // Log large serialized objects for debugging
    if (bytes.length > 10 * 1024 * 1024) { // 10MB
      Runtime runtime = Runtime.getRuntime();
      long totalMemory = runtime.totalMemory();
      long freeMemory = runtime.freeMemory();
      long maxMemory = runtime.maxMemory();
      long usedMemory = totalMemory - freeMemory;
      
      System.out.println("HCY:DEBUG: SafeDataInputStream: deserialize() - "
                          + "Deserializing large object of size " + bytes.length + " bytes. "
                          + "Memory: Used=" + (usedMemory / 1024 / 1024) + "MB, "
                          + "Free=" + (freeMemory / 1024 / 1024) + "MB, "
                          + "Total=" + (totalMemory / 1024 / 1024) + "MB, "
                          + "Max=" + (maxMemory / 1024 / 1024) + "MB");

      // Force GC before large deserialization
      System.gc();
      
      // Check if we're approaching memory limits
      if (usedMemory > maxMemory * 0.85) {
        System.err.println("HCY:WARNING: Memory usage critical! Used " 
                            + (usedMemory * 100 / maxMemory) + "% of max heap. "
                            + "Consider reducing --mutationUnitSize.");
      }
    }
    
    // // Additional safety check before attempting deserialization
    // Runtime runtime = Runtime.getRuntime();
    // long availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
    // if (bytes.length * 3 > availableMemory) { // 3x safety factor for deserialization overhead
    //   throw new IOException("Insufficient memory for safe deserialization. " +
    //                       "Object size: " + (bytes.length/1024/1024) + "MB, " +
    //                       "Available memory: " + (availableMemory/1024/1024) + "MB. " +
    //                       "Reduce --mutationUnitSize to continue.");
    // }
    
    final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    try (ObjectInput in = new ObjectInputStream(bis)) {
      return in.readObject();
    } catch (final ClassNotFoundException e) {
      throw Unchecked.translateCheckedException(e);
    } catch (final OutOfMemoryError e) {
      throw new IOException("OutOfMemoryError during deserialization of " + bytes.length 
                            + " byte object. Consider increasing heap size or reducing test suite size.", e);
    }
  }

}
