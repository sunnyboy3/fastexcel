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
 * <p>使用 {@link #builder(int)} 来构建实例。</p>
 */
public final class ExportOptions {

    private final int totalRows;
    private final int sheetSize;
    private final int threadPoolSize;
    private final int queueCapacity;
    private final int flushInterval;
    private final String sheetNamePrefix;

    private ExportOptions(Builder builder) {
        this.totalRows = builder.totalRows;
        this.sheetSize = builder.sheetSize;
        this.threadPoolSize = builder.threadPoolSize;
        this.queueCapacity = builder.queueCapacity;
        this.flushInterval = builder.flushInterval;
        this.sheetNamePrefix = builder.sheetNamePrefix;
    }

    public static Builder builder(int totalRows) {
        return new Builder(totalRows);
    }

    public int getTotalRows()       { return totalRows; }
    public int getSheetSize()       { return sheetSize; }
    public int getThreadPoolSize()  { return threadPoolSize; }
    public int getQueueCapacity()   { return queueCapacity; }
    public int getFlushInterval()   { return flushInterval; }
    public String getSheetNamePrefix() { return sheetNamePrefix; }

    public static final class Builder {
        private final int totalRows;
        private int sheetSize = 1000;
        private int threadPoolSize = 1;
        private int queueCapacity = 2;
        private int flushInterval = 0;
        private String sheetNamePrefix = "Sheet";

        Builder(int totalRows) {
            if (totalRows <= 0) {
                throw new IllegalArgumentException("totalRows must be > 0, was " + totalRows);
            }
            this.totalRows = totalRows;
        }

        /** 每个 Sheet 的行数。必须 &gt; 0。 */
        public Builder sheetSize(int sheetSize) {
            if (sheetSize <= 0) {
                throw new IllegalArgumentException("sheetSize must be > 0, was " + sheetSize);
            }
            this.sheetSize = sheetSize;
            return this;
        }

        /** 并发获取 Sheet 数据的线程数。默认为 1。 */
        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        /** 供给写入线程的工作队列容量。必须 &gt; 0。 */
        public Builder queueCapacity(int queueCapacity) {
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException("queueCapacity must be > 0, was " + queueCapacity);
            }
            this.queueCapacity = queueCapacity;
            return this;
        }

        /** 每隔 N 行将工作表刷新到输出流（0 表示不进行中间刷新）。 */
        public Builder flushInterval(int flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }

        /** Sheet 名称前缀；后缀 {@code _1, _2, ...} 会自动追加。 */
        public Builder sheetNamePrefix(String sheetNamePrefix) {
            this.sheetNamePrefix = sheetNamePrefix;
            return this;
        }

        public ExportOptions build() {
            return new ExportOptions(this);
        }
    }
}
