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

/**
 * 将行数据（以及可选的表头行）写入 {@link Worksheet}。
 *
 * @param <T> 行数据类型
 */
public interface RowWriter<T> {

    /**
     * 在第 0 行写入表头行。
     * 默认实现为空。
     */
    default void writeHeader(Worksheet worksheet) {
        // 空实现
    }

    /**
     * 写入一行数据。
     *
     * @param worksheet 目标工作表
     * @param rowIndex  Sheet 内从零开始的行索引（表头之后）
     * @param row       行数据
     */
    void writeRow(Worksheet worksheet, int rowIndex, T row);
}
