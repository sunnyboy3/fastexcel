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

import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.annotation.ExcelWriterMapper;

/**
 * {@link RowWriter} 实现的工厂方法。
 */
public final class RowWriters {

    private RowWriters() {
    }

    /**
     * 创建一个使用 {@link ExcelWriterMapper} 来写入注解 Bean 的
     * {@link RowWriter}。每行通过映射器的单 Bean {@code writeRow}
     * 方法写入，以避免每行的列表分配开销。
     *
     * @param beanType 带有注解的 Bean 类
     * @param <T>      Bean 类型
     * @return 由 {@code ExcelWriterMapper} 支持的 RowWriter
     */
    public static <T> RowWriter<T> annotation(Class<T> beanType) {
        ExcelWriterMapper<T> mapper = new ExcelWriterMapper<T>(beanType);
        return new RowWriter<T>() {
            @Override
            public void writeHeader(Worksheet worksheet) {
                mapper.writeHeaderRow(worksheet, 0);
            }

            @Override
            public void writeRow(Worksheet worksheet, int rowIndex, T row) {
                mapper.writeRow(worksheet, rowIndex, row);
            }
        };
    }

    /**
     * 创建一个委托给给定原始写入器的 {@link RowWriter}。
     *
     * @param writer 原始行写入器
     * @param <T>    行数据类型
     * @return 相同的写入器实例
     */
    public static <T> RowWriter<T> raw(RowWriter<T> writer) {
        return writer;
    }
}
