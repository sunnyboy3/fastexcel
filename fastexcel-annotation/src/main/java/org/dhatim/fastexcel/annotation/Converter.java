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
package org.dhatim.fastexcel.annotation;

import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;

/**
 * 自定义类型转换器接口，允许用户扩展 Excel 读写时的类型转换逻辑。
 *
 * <p>实现类必须提供无参构造器。</p>
 *
 * <pre>{@code
 * public class StatusConverter implements Converter<Status> {
 *     public Status read(Cell cell) {
 *         return Status.valueOf(cell.getText());
 *     }
 *     public void write(Worksheet ws, int row, int col, Status value) {
 *         ws.value(row, col, value.name());
 *     }
 * }
 * }</pre>
 *
 * @param <T> 字段类型
 */
public interface Converter<T> {

    /**
     * 将 Excel 单元格值转换为 Java 对象。
     *
     * @param cell 单元格（可能为 null）
     * @return 转换后的值，null 表示空值
     */
    T read(Cell cell);

    /**
     * 将 Java 对象写入 Excel 单元格。
     *
     * @param worksheet 工作表
     * @param row       行索引
     * @param col       列索引
     * @param value     字段值（非 null）
     */
    void write(Worksheet worksheet, int row, int col, T value);
}
