/*
 * Copyright 2016 Dhatim.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dhatim.fastexcel.batch;

/**
 * 批量 Excel 导出的配置。
 *
 * <p>使用 {@link #builder(int)} 或 {@link #unbounded()} 来构建实例。</p>
 *
 * <p>线程池配置说明：
 * <ul>
 *   <li>{@code threadPoolSize} — 并发获取 Sheet 数据的工作线程数</li>
 *   <li>{@code queueCapacity} — 预取深度：已获取但尚未写入的 Sheet 数</li>
 *   <li>{@code idleThreadTimeoutSec} — 内部线程池中空闲核心线程的超时时间（秒）</li>
 * </ul>
 * {@code threadPoolSize} 决定并发度，{@code queueCapacity} 控制背压。
 * 对于 I/O 密集型数据源（如数据库），threadPoolSize 可以设为 4-8；
 * 对于 CPU 密集型则设为核心数。</p>
 */
public final class ExportOptions {

    private final int totalRows;
    private final boolean bounded;
    private final int sheetSize;
    private final int threadPoolSize;
    private final int queueCapacity;
    private final int flushInterval;
    private final String sheetNamePrefix;
    private final long fetchTimeoutMs;
    private final long idleThreadTimeoutSec;
    private final boolean failFast;

    private ExportOptions(Builder builder) {
        this.totalRows = builder.totalRows;
        this.bounded = builder.bounded;
        this.sheetSize = builder.sheetSize;
        this.threadPoolSize = builder.threadPoolSize;
        this.queueCapacity = builder.queueCapacity;
        this.flushInterval = builder.flushInterval;
        this.sheetNamePrefix = builder.sheetNamePrefix;
        this.fetchTimeoutMs = builder.fetchTimeoutMs;
        this.idleThreadTimeoutSec = builder.idleThreadTimeoutSec;
        this.failFast = builder.failFast;
    }

    /**
     * 为已知总行数的导出创建构建器。
     *
     * @param totalRows 总行数，必须 &gt; 0
     */
    public static Builder builder(int totalRows) {
        return new Builder(totalRows);
    }

    /**
     * 为<b>未知总行数</b>的流式导出创建构建器。
     * <p>导出会持续按页获取数据，直到某次 fetch 返回的行数少于
     * {@code sheetSize}（或为空），以此判定数据已耗尽。适用于游标、
     * 不便预先 count 的数据源。
     */
    public static Builder unbounded() {
        return new Builder();
    }

    /** 总行数；流式（unbounded）模式下返回 -1。 */
    public int getTotalRows()           { return totalRows; }
    /** 是否为已知总行数模式。 */
    public boolean isBounded()          { return bounded; }
    public int getSheetSize()           { return sheetSize; }
    public int getThreadPoolSize()      { return threadPoolSize; }
    public int getQueueCapacity()       { return queueCapacity; }
    public int getFlushInterval()       { return flushInterval; }
    public String getSheetNamePrefix()  { return sheetNamePrefix; }
    /** 获取 fetch 超时时间（毫秒），0 表示无限等待。 */
    public long getFetchTimeoutMs()     { return fetchTimeoutMs; }
    /** 内部线程池空闲线程超时时间（秒）。 */
    public long getIdleThreadTimeoutSec() { return idleThreadTimeoutSec; }
    /** 是否在首次 fetch 失败时立即中止（而非继续处理已排队的数据）。 */
    public boolean isFailFast()         { return failFast; }

    public static final class Builder {
        private final int totalRows;
        private final boolean bounded;
        private int sheetSize = 1000;
        private int threadPoolSize = 1;
        private int queueCapacity = 2;
        private int flushInterval = 0;
        private String sheetNamePrefix = "Sheet";
        private long fetchTimeoutMs = 0;
        private long idleThreadTimeoutSec = 60;
        private boolean failFast = true;

        Builder(int totalRows) {
            if (totalRows <= 0) {
                throw new IllegalArgumentException(
                        "totalRows must be > 0, was " + totalRows);
            }
            this.totalRows = totalRows;
            this.bounded = true;
        }

        Builder() {
            this.totalRows = -1;
            this.bounded = false;
        }

        /** 每个 Sheet 的行数。必须 &gt; 0。 */
        public Builder sheetSize(int sheetSize) {
            if (sheetSize <= 0) {
                throw new IllegalArgumentException(
                        "sheetSize must be > 0, was " + sheetSize);
            }
            this.sheetSize = sheetSize;
            return this;
        }

        /** 并发获取 Sheet 数据的线程数。默认为 1。 */
        public Builder threadPoolSize(int threadPoolSize) {
            if (threadPoolSize < 0) {
                throw new IllegalArgumentException(
                        "threadPoolSize must be >= 0, was " + threadPoolSize);
            }
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        /** 预取队列容量（已获取但尚未写入的 Sheet 数）。必须 &gt; 0。 */
        public Builder queueCapacity(int queueCapacity) {
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException(
                        "queueCapacity must be > 0, was " + queueCapacity);
            }
            this.queueCapacity = queueCapacity;
            return this;
        }

        /** 每隔 N 行将工作表刷新到输出流（0 表示不进行中间刷新）。 */
        public Builder flushInterval(int flushInterval) {
            if (flushInterval < 0) {
                throw new IllegalArgumentException(
                        "flushInterval must be >= 0, was " + flushInterval);
            }
            this.flushInterval = flushInterval;
            return this;
        }

        /** Sheet 名称前缀；后缀 {@code _1, _2, ...} 会自动追加。 */
        public Builder sheetNamePrefix(String sheetNamePrefix) {
            this.sheetNamePrefix = sheetNamePrefix;
            return this;
        }

        /**
         * 每次 fetch 的超时时间（毫秒），0 表示无限等待（默认）。
         * 超时会抛出 {@link BatchExcelExportException}。
         */
        public Builder fetchTimeoutMs(long fetchTimeoutMs) {
            if (fetchTimeoutMs < 0) {
                throw new IllegalArgumentException(
                        "fetchTimeoutMs must be >= 0, was " + fetchTimeoutMs);
            }
            this.fetchTimeoutMs = fetchTimeoutMs;
            return this;
        }

        /**
         * 内部线程池的空闲核心线程超时时间（秒）。
         * 默认为 60 秒。当使用内部线程池时生效。
         */
        public Builder idleThreadTimeoutSec(long idleThreadTimeoutSec) {
            if (idleThreadTimeoutSec < 0) {
                throw new IllegalArgumentException(
                        "idleThreadTimeoutSec must be >= 0, was "
                                + idleThreadTimeoutSec);
            }
            this.idleThreadTimeoutSec = idleThreadTimeoutSec;
            return this;
        }

        /**
         * 是否在首次 fetch 失败时立即中止（默认 true）。
         * 设为 false 则会继续处理已排队的数据再抛出异常。
         */
        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public ExportOptions build() {
            return new ExportOptions(this);
        }
    }
}