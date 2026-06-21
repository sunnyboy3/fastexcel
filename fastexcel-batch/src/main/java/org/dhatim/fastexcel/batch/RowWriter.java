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
 * <p>从 1.1 开始，该接口支持 <b>prepare-write 分离模式</b>
 * 以实现同 Sheet 内的多线程并行处理：
 * <ul>
 *   <li>{@link #prepare(Object)} — 在多线程中并行执行（纯 CPU，无 I/O），
 *       预计算每行的列值数组</li>
 *   <li>{@link #writePreparedRow(Worksheet, int, Object[])} —
 *       在主线程中串行调用，将预计算的值写入 Worksheet（仅 I/O）</li>
 * </ul>
 *
 * <p>默认实现保持向后兼容：{@code prepare()} 返回 {@code null}，
 * {@code supportsParallelPrepare()} 返回 {@code false}。</p>
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

    // ── prepare-write 分离（并行支持）──────────────────────────────

    /**
     * 预计算单行的列值数组（纯 CPU，无 I/O）。
     * 此方法可在多个线程中并行调用，不涉及 {@link Worksheet} 操作。
     *
     * <p>默认返回 {@code null}，表示不支持预计算。
     * 当返回非 null 时，调用方可通过 {@link #writePreparedRow} 跳过反射开销。</p>
     *
     * @param row 行数据
     * @return 列值数组，{@code null} 表示退化到 {@link #writeRow}
     */
    default Object[] prepare(T row) {
        return null;
    }

    /**
     * 写入已预计算的行数据，跳过反射取值步骤。
     * 仅由主线程在持有 Worksheet 时调用。
     *
     * <p>默认实现退化到 {@link #writeRow(Worksheet, int, Object)}。</p>
     *
     * @param worksheet 目标工作表
     * @param rowIndex  Sheet 内从零开始的行索引
     * @param prepared  由 {@link #prepare(Object)} 返回的列值数组
     */
    default void writePreparedRow(Worksheet worksheet, int rowIndex,
                                   Object[] prepared) {
        @SuppressWarnings("unchecked")
        T row = (T) prepared[0];
        writeRow(worksheet, rowIndex, row);
    }

    /**
     * 是否参与同 Sheet 并行准备。
     * 当且仅当 {@link #prepare(Object)} 和 {@link #writePreparedRow}
     * 正确实现时返回 {@code true}。
     *
     * @return {@code true} 表示支持并行准备
     */
    default boolean supportsParallelPrepare() {
        return false;
    }
}
