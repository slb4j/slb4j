/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j.support;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.LongAdder;

/**
 * An output stream that counts the number of bytes written to it.
 * This class extends {@link FilterOutputStream} and provides functionality
 * to keep track of the total number of bytes written through the stream.
 */
public class CountingOutputStream extends FilterOutputStream {
    private final LongAdder byteCounter;

    /**
     * Constructs a {@code CountingOutputStream} that wraps the given output stream
     * and counts the number of bytes written.
     *
     * @param out        the underlying output stream to be wrapped
     * @param byteCounter the {@code LongAdder} used to count the total number of bytes written
     */
    public CountingOutputStream(OutputStream out, LongAdder byteCounter) {
        super(out);
        this.byteCounter = byteCounter;
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
        byteCounter.add(b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        byteCounter.add(len);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        byteCounter.increment();
    }
}
