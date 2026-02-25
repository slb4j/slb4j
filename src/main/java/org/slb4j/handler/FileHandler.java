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
package org.slb4j.handler;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public final class FileHandler extends AbstractFileHandler {

    private final Path path;
    private final boolean append;
    private final FileChannel channel;
    private final Writer writer;

    /**
     * Constructs a new FileHandler.
     *
     * @param name   the name of the handler
     * @param path   the path to the log file
     * @param append if true, then bytes will be written to the end of the file rather than the beginning
     * @throws IOException if the file cannot be opened
     */
    public FileHandler(String name, Path path, boolean append) throws IOException {
        super(name);
        this.path = path;
        this.append = append;

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        this.channel = FileChannel.open(path, append ? OPTIONS_APPEND : OPTIONS_CREATE);
        this.writer = Channels.newWriter(channel, StandardCharsets.UTF_8);
        writeLayoutHeader();
    }

    @Override
    protected Writer writer() {
        return writer;
    }

    /**
     * Gets the path to the log file.
     * @return the path to the log file
     */
    public Path getPath() {
        return path;
    }

    /**
     * Gets whether to append to the log file.
     * @return true if appending, false otherwise
     */
    public boolean isAppend() {
        return append;
    }
    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof FileHandler other)) return false;
        return append == other.append && name().equals(other.name()) && path.equals(other.path) && getFilter().equals(other.getFilter()) && getLayout().equals(other.getLayout());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name(), path, append, getFilter(), getLayout());
    }
}
