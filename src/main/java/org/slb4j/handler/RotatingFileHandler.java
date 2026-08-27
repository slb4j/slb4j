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
import org.slb4j.LogLevel;
import org.slb4j.SLB4J;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A log handler that writes log entries to a file.
 * It supports log rotation triggered by file size, number of entries, or time.
 */
public final class RotatingFileHandler extends AbstractFileHandler {

    /**
     * Enum representing the index strategy used for log file rotation.
     *
     * This strategy determines how backup files are indexed during
     * rotation in the {@code RotatingFileHandler}. The values correspond to
     * the min and max index strategies supported by Log4j2.
     *
     * - {@code USE_MAX}: Use the highest available index (e.g., rotate
     *   to the maximum index position allowed by the configuration).
     * - {@code USE_MIN}: Use the lowest available index (e.g., rotate
     *   by overwriting the oldest backup file first).
     */
    public enum IndexStrategy {
        /**
         * The latest archive file has the highest index. Existing archives are
         * never remained, but old file are removed once the maximum number of
         * archive files is exceeded.
         */
        USE_MAX,
        /**
         * The oldest archive file has the lowest index. Existing archives are
         * never removed, but old files are overwritten when the maximum number
         * of archive files is exceeded. Existing archive files are renamed
         * (shifted up) when rotation occurs.
         */
        USE_MIN
    }

    private static final Pattern BACKUP_INDEX_PATTERN = Pattern.compile("%i(?:\\{(\\d+)\\})?");
    private static final Pattern BACKUP_DATE_PATTERN = Pattern.compile("%d(?:\\{([^}]+)\\})?");

    private final boolean isFileNameProvided;
    private final IndexStrategy indexStrategy;
    private final String fileName;
    private final String fileNamePattern;
    private final IntFunction<String> indexFormatter;
    private final Supplier<String> dateSupplier;
    private final boolean append;

    private int backupIndex = 1;
    private Path logFile;

    private Writer writer;
    private @Nullable FileChannel channel;

    private long nextRotationTime = -1;

    private long maxFileSize = -1;
    private @Nullable ChronoUnit rotationTimeUnit;
    private int maxBackupIndex = 1;

    /**
     * Constructs a new FileHandler.
     *
     * @param name            the name of the handler
     * @param fileName        the fileName (including path) to the log file as a string
     * @param fileNamePattern the file name pattern
     * @param append          if true, then bytes will be written to the end of the file rather than the beginning
     * @param indexStrategy   the index strategy
     * @throws IOException if the file cannot be opened
     */
    public RotatingFileHandler(String name, String fileName, String fileNamePattern, boolean append, IndexStrategy indexStrategy) throws IOException {
        super(name);

        if (fileName.isEmpty() && fileNamePattern.isEmpty()) {
            throw new IllegalStateException("At least one of fileName and fileNamePattern must not be empty.");
        }

        this.indexFormatter = getIndexFormatter(fileNamePattern);
        this.dateSupplier = getDateSupplier(fileNamePattern);
        this.isFileNameProvided = !fileName.isEmpty();
        this.indexStrategy = indexStrategy;
        this.fileName = fileName.isEmpty() ? getFileName(fileNamePattern, 1) : fileName;
        this.fileNamePattern = fileNamePattern.isEmpty() ? fileName + ".%i" : fileNamePattern;
        this.append = append;
        this.rotationTimeUnit = java.time.temporal.ChronoUnit.DAYS;

        if (isFileNameProvided) {
            this.logFile = Paths.get(this.fileName);
        } else {
            this.backupIndex = 1;
            this.logFile = Paths.get(getFileName(fileNamePattern, backupIndex));
        }

        Files.createDirectories(Objects.requireNonNull(logFile.getParent(), "FileHandler path must specify a valid directory"));

        this.writer = openFile();
    }

    /**
     * Parses the formatter format %i token.
     * Example: "app-%i{3}.log" returns 3.
     * Example: "app-%i.log" returns 0 (no padding).
     */
    private IntFunction<String> getIndexFormatter(String pattern) {
        // Regex explanation:
        // %i       : matches the literal %i
        // (?:      : starts a non-capturing group
        //   \{     : matches a literal {
        //   (\d+)  : capturing group 1: matches one or more digits
        //   \}     : matches a literal }
        // )?       : makes the entire {digits} block optional
        Matcher matcher = BACKUP_INDEX_PATTERN.matcher(pattern);

        if (matcher.find()) {
            String widthString = matcher.group(1);
            if (widthString != null) {
                String fmt = "%0" + Integer.parseInt(widthString) + "d";
                return fmt::formatted;
            }
        }

        // Default Log4j2 behavior: no leading zeros
        return Integer::toString;
    }

    private Supplier<String> getDateSupplier(String pattern) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
        try {
            Matcher matcher = BACKUP_DATE_PATTERN.matcher(pattern);
            if (matcher.find()) {
                String formatString = matcher.group(1);
                if (formatString != null) {
                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(formatString);
                    return () -> LocalDateTime.now(Clock.systemDefaultZone()).format(dateFormatter);
                }
            }
        } catch (Exception e) {
            SLB4J.logInternal(LogLevel.WARN, "Failed to parse date format string, using ISO_LOCAL_DATE: %s", e);
        }

        // Default Log4j2 behavior: no leading zeros
        return () -> LocalDateTime.now(Clock.systemDefaultZone()).format(dateTimeFormatter);
    }

    private final String getFileName(String pattern, int backupIndex) {
        String fn = BACKUP_INDEX_PATTERN.matcher(pattern).replaceFirst(indexFormatter.apply(backupIndex));
        fn = BACKUP_DATE_PATTERN.matcher(fn).replaceFirst(dateSupplier.get());
        return fn;
    }

    private void closeFile() throws IOException {
        synchronized (lock) {
            writer.close();
        }
    }

    private Writer openFile() throws IOException {
        synchronized (lock) {
            this.channel = FileChannel.open(logFile, append ? OPTIONS_APPEND : OPTIONS_CREATE);
            this.writer = Channels.newWriter(channel, StandardCharsets.UTF_8);

            writeLayoutHeader();
            updateNextRotationTime();

            return writer;
        }
    }

    private void updateNextRotationTime() {
        if (rotationTimeUnit != null) {
            nextRotationTime = Instant.now().truncatedTo(rotationTimeUnit).plus(1, rotationTimeUnit).toEpochMilli();
        } else {
            nextRotationTime = -1;
        }
    }

    /**
     * Gets the file name pattern for archived log files.
     *
     * @return the file name pattern
     */
    public @Nullable String getFileNamePattern() {
        synchronized (lock) {
            return fileNamePattern;
        }
    }

    /**
     * Sets the maximum file size before rotation.
     *
     * @param maxFileSize the maximum file size in bytes, or -1 for no limit
     */
    public void setMaxFileSize(long maxFileSize) {
        synchronized (lock) {
            this.maxFileSize = maxFileSize;
        }
    }

    /**
     * Sets the rotation time unit.
     *
     * @param rotationTimeUnit the time unit for rotation, or null for no time-based rotation
     */
    public void setRotationTimeUnit(@Nullable ChronoUnit rotationTimeUnit) {
        synchronized (lock) {
            this.rotationTimeUnit = rotationTimeUnit;
            updateNextRotationTime();
        }
    }

    /**
     * Sets the maximum number of backup files to keep.
     *
     * @param maxBackupIndex the maximum number of backup files
     */
    public void setMaxBackupIndex(int maxBackupIndex) {
        synchronized (lock) {
            this.maxBackupIndex = maxBackupIndex;
        }
    }

    @Override
    protected void checkRotation(long timestamp, int charsToWrite) throws IOException {
        if (channel == null) {
            return;
        }

        if (isTimeRotation(timestamp) || isSizeRotation(charsToWrite)) {
            try {
                closeFile();
                rotate();
            } catch (IOException e) {
                SLB4J.logInternal(LogLevel.WARN, "Rotation failed", e);
            } finally {
                openFile();
            }
        }
    }

    private boolean isSizeRotation(int charsToWrite) throws IOException {
        long position = channel == null ? 0 : channel.position();
        return maxFileSize > 0 && position != 0 && position + charsToWrite >= maxFileSize;
    }

    private boolean isTimeRotation(long timestamp) {
        return nextRotationTime != -1 && timestamp >= nextRotationTime;
    }

    private final void rotate() throws IOException {
        synchronized (lock) {
            Path currentPath = logFile;
            Path parent = currentPath.getParent();
            if (parent == null) return;

            if (maxBackupIndex <= 0) {
                if (isFileNameProvided) {
                    if (append) {
                        Files.write(currentPath, new byte[0], java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } else {
                    Files.write(currentPath, new byte[0], java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                }
                return;
            }

            if (indexStrategy == IndexStrategy.USE_MAX) {
                handleMaxStrategy(parent);
            } else {
                handleMinStrategy(parent);
            }
        }
    }

    private void handleMaxStrategy(Path folder) throws IOException {
        List<LogFileEntry> existingLogs = fetchExistingLogs(folder, dateSupplier.get());
        existingLogs.sort(Comparator.comparingInt(e -> e.index));

        if (!isFileNameProvided) {
            // Pattern-Only mode: writing directly to indexed files.
            // We are currently writing to the file with the highest index (if any exist).
            // Rotation means moving to the next index.
            int nextIndex = 1;
            if (!existingLogs.isEmpty()) {
                nextIndex = existingLogs.get(existingLogs.size() - 1).index + 1;
            }
            this.backupIndex = nextIndex;
            this.logFile = folder.resolve(getFileName(fileNamePattern, backupIndex));

            // Purge if we exceed maxBackupIndex. 
            // The total number of files we want to keep is maxBackupIndex + 1.
            // existingLogs contains files already on disk (including the one we just finished writing).
            if (existingLogs.size() >= maxBackupIndex + 1) {
                int toDelete = existingLogs.size() - maxBackupIndex;
                for (int i = 0; i < toDelete; i++) {
                    Files.deleteIfExists(existingLogs.get(i).path);
                }
            }
        } else {
            // Fixed-File mode: writing to a fixed fileName, rotating to indexed files.
            // Purge oldest if we reached maxBackupIndex.
            if (existingLogs.size() >= maxBackupIndex) {
                int toDelete = existingLogs.size() - maxBackupIndex + 1;
                for (int i = 0; i < toDelete; i++) {
                    Files.deleteIfExists(existingLogs.get(i).path);
                }
            }

            // Determine next index: highest + 1
            int nextIndex = 1;
            if (!existingLogs.isEmpty()) {
                nextIndex = existingLogs.get(existingLogs.size() - 1).index + 1;
            }

            Path targetPath = folder.resolve(getFileName(fileNamePattern, nextIndex));
            executeRotation(targetPath);
        }
    }

    private void handleMinStrategy(Path folder) throws IOException {
        // 1. Delete the absolute oldest (maxBackupIndex)
        Path maxFile = folder.resolve(getFileName(fileNamePattern, maxBackupIndex));
        Files.deleteIfExists(maxFile);

        // 2. Shift UP: 2->3, 1->2
        for (int i = maxBackupIndex - 1; i >= 1; i--) {
            Path source = folder.resolve(getFileName(fileNamePattern, i));
            if (Files.exists(source)) {
                Path target = folder.resolve(getFileName(fileNamePattern, i + 1));
                Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 3. Active file always goes to index 1
        Path targetPath = folder.resolve(getFileName(fileNamePattern, 1));
        executeRotation(targetPath);
    }

    private void executeRotation(Path archivePath) throws IOException {
        if (isFileNameProvided) {
            Path activePath = Paths.get(this.fileName);
            if (Files.exists(activePath)) {
                // Standard Log4j2: Rename active to the next available index
                Files.move(activePath, archivePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // Always reset logFile to the fixed name
            this.logFile = activePath;
        } else {
            // Pattern-only: Handled inside handleMax/Min to avoid double-stepping
            this.logFile = archivePath;
        }
    }

    private List<LogFileEntry> fetchExistingLogs(Path folder, String dateStr) throws IOException {
        List<LogFileEntry> existingLogs = new ArrayList<>();
        // Resolve the pattern's date part so we only match files for "today"
        String dateResolvedPattern = resolveDate(fileNamePattern, dateStr);

        // Create a regex where %i is a capturing group for digits.
        // We only use the filename part for matching against DirectoryStream entries.
        Path patternPath = Paths.get(dateResolvedPattern);
        String patternFileName = String.valueOf(patternPath.getFileName());

        Matcher matcher = BACKUP_INDEX_PATTERN.matcher(patternFileName);
        if (!matcher.find()) {
            return existingLogs;
        }

        String prefix = patternFileName.substring(0, matcher.start());
        String suffix = patternFileName.substring(matcher.end());
        String regex = Pattern.quote(prefix) + "(\\d+)" + Pattern.quote(suffix);
        Pattern indexMatcher = Pattern.compile(regex);

        if (!Files.exists(folder)) {
            return existingLogs;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                Matcher entryMatcher = indexMatcher.matcher(String.valueOf(entry.getFileName()));
                if (entryMatcher.matches()) {
                    int index = Integer.parseInt(entryMatcher.group(1));
                    existingLogs.add(new LogFileEntry(entry, index));
                }
            }
        }
        return existingLogs;
    }

    private String resolveDate(String pattern, String dateStr) {
        Matcher matcher = BACKUP_DATE_PATTERN.matcher(pattern);
        if (matcher.find()) {
            return matcher.replaceFirst(Pattern.quote(dateStr));
        }
        return pattern;
    }

    // Helper class to keep Path and Index paired together
    private static class LogFileEntry {
        Path path;
        int index;
        LogFileEntry(Path path, int index) { this.path = path; this.index = index; }
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
        return logFile;
    }

    /**
     * Gets whether to append to the log file.
     * @return true if appending, false otherwise
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * Gets the maximum file size before rotation.
     * @return the maximum file size in bytes, or -1 for no limit
     */
    public long getMaxFileSize() {
        synchronized (lock) {
            return maxFileSize;
        }
    }

    /**
     * Gets the rotation time unit.
     * @return the rotation time unit, or null for no time-based rotation
     */
    public @Nullable ChronoUnit getRotationTimeUnit() {
        synchronized (lock) {
            return rotationTimeUnit;
        }
    }

    /**
     * Gets the maximum number of backup files to keep.
     * @return the maximum number of backup files
     */
    public int getMaxBackupIndex() {
        synchronized (lock) {
            return maxBackupIndex;
        }
    }

    /**
     * Gets the index strategy used for rotation.
     * @return the index strategy
     */
    public IndexStrategy getIndexStrategy() {
        return indexStrategy;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RotatingFileHandler that = (RotatingFileHandler) o;
        return isFileNameProvided == that.isFileNameProvided 
                && append == that.append 
                && backupIndex == that.backupIndex 
                && nextRotationTime == that.nextRotationTime 
                && maxFileSize == that.maxFileSize 
                && maxBackupIndex == that.maxBackupIndex  
                && indexStrategy == that.indexStrategy
                && rotationTimeUnit == that.rotationTimeUnit
                && Objects.equals(fileName, that.fileName) 
                && Objects.equals(fileNamePattern, that.fileNamePattern) 
                && Objects.equals(indexFormatter, that.indexFormatter) 
                && Objects.equals(dateSupplier, that.dateSupplier) 
                && Objects.equals(logFile, that.logFile)
                && Objects.equals(writer, that.writer)
                && Objects.equals(channel, that.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                isFileNameProvided,
                indexStrategy,
                fileName,
                fileNamePattern,
                indexFormatter,
                dateSupplier,
                append,
                backupIndex,
                logFile,
                writer,
                channel,
                nextRotationTime,
                maxFileSize,
                rotationTimeUnit,
                maxBackupIndex
        );
    }
}
