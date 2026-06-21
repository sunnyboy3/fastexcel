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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 将标注了注解的 Java Bean 列表写入 Excel {@link Worksheet}。
 *
 * <pre>{@code
 * ExcelWriterMapper<Person> mapper = new ExcelWriterMapper<>(Person.class);
 * try (Workbook wb = new Workbook(os, "App", "1.0")) {
 *     Worksheet ws = wb.newWorksheet("People");
 *     mapper.write(ws, people, 0, true); // 包含表头行
 * }
 * }</pre>
 *
 * @param <T> Bean 类型
 */
public class ExcelWriterMapper<T> {

    private final Class<T> beanType;
    private final FieldMapping[] fieldMappings;
    private final List<FieldMapping> fieldMappingsList;

    /**
     * 为给定的 Bean 类创建写入映射器。
     *
     * @param beanType Bean 类
     */
    public ExcelWriterMapper(Class<T> beanType) {
        this.beanType = Objects.requireNonNull(beanType);
        List<FieldMapping> resolved = FieldMapping.resolve(beanType);
        this.fieldMappings = resolved.toArray(new FieldMapping[0]);
        this.fieldMappingsList = resolved;
    }

    /**
     * 从第 0 行开始写入 Bean，不包含表头行。
     *
     * @param worksheet 目标工作表
     * @param beans     要写入的 Bean 列表
     */
    public void write(Worksheet worksheet, List<T> beans) {
        write(worksheet, beans, 0, false);
    }

    /**
     * 写入 Bean，可控制起始行和表头行为。
     *
     * @param worksheet     目标工作表
     * @param beans         要写入的 Bean 列表
     * @param startRow      从零开始的起始行索引
     * @param includeHeader 如果为 true，则先写入表头行
     */
    public void write(Worksheet worksheet, List<T> beans,
                      int startRow, boolean includeHeader) {
        int row = startRow;
        if (includeHeader) {
            writeHeaderRow(worksheet, row);
            row++;
        }
        FieldMapping[] mappings = this.fieldMappings;
        for (int i = 0, len = beans.size(); i < len; i++) {
            writeSingleBean(worksheet, row, beans.get(i), mappings);
            row++;
        }
    }

    /**
     * 在指定行写入单个 Bean，不带表头，也不分配列表包装。
     * 适合热路径调用。
     *
     * @param worksheet 目标工作表
     * @param rowNum    从零开始的行索引
     * @param bean      要写入的 Bean
     */
    public void writeRow(Worksheet worksheet, int rowNum, T bean) {
        writeSingleBean(worksheet, rowNum, bean, this.fieldMappings);
    }

    /**
     * 写入包含列名的表头行。
     * 优先使用 {@code @ExcelProperty} 的值，
     * 其次回退到字段名称。
     *
     * @param worksheet 目标工作表
     * @param rowNum    表头行的从零开始行索引
     */
    public void writeHeaderRow(Worksheet worksheet, int rowNum) {
        FieldMapping[] mappings = this.fieldMappings;
        for (int i = 0, len = mappings.length; i < len; i++) {
            FieldMapping m = mappings[i];
            String header = m.hasHeaderName ? m.headerName : m.field.getName();
            worksheet.value(rowNum, m.columnIndex, header);
        }
    }

    /**
     * 返回已解析的字段映射。
     *
     * @return 不可修改的字段映射列表
     */
    public List<FieldMapping> getFieldMappings() {
        return Collections.unmodifiableList(fieldMappingsList);
    }

    // ── prepare-write 分离（并行支持）───────────────────────────────

    /**
     * 预计算单行的列值数组——仅执行反射取值，不涉及 {@link Worksheet} 写入。
     * 此方法不包含任何 I/O 操作，线程安全，可在多个线程中并行调用。
     *
     * @param bean 要预计算的行数据
     * @return 列值数组，长度等于映射字段数；{@code values[i]} 可能为 null
     * @throws ExcelMappingException 如果字段访问失败
     */
    public Object[] prepareRow(T bean) {
        FieldMapping[] mappings = this.fieldMappings;
        Object[] values = new Object[mappings.length];
        try {
            for (int i = 0, len = mappings.length; i < len; i++) {
                values[i] = mappings[i].field.get(bean);
            }
        } catch (IllegalAccessException e) {
            throw new ExcelMappingException(
                    "Cannot read field value from " + bean.getClass().getName(), e);
        }
        return values;
    }

    /**
     * 将预计算的行数据写入 Worksheet——跳过反射取值步骤，直接写入。
     * 必须由<b>单一线程</b>在持有 Worksheet 时调用（Worksheet 非线程安全）。
     *
     * @param worksheet 目标工作表
     * @param rowNum    从零开始的行索引
     * @param values    由 {@link #prepareRow(Object)} 返回的列值数组
     */
    public void writePreparedRow(Worksheet worksheet, int rowNum, Object[] values) {
        FieldMapping[] mappings = this.fieldMappings;
        for (int i = 0, len = mappings.length; i < len; i++) {
            Object value = values[i];
            if (value != null) {
                mappings[i].cellWriter.write(worksheet, rowNum,
                        mappings[i].columnIndex, value);
            }
        }
    }

    /** 返回映射的列数（即 {@link #prepareRow} 返回的数组长度）。 */
    public int columnCount() {
        return fieldMappings.length;
    }

    private static <T> void writeSingleBean(Worksheet worksheet, int rowNum,
                                             T bean, FieldMapping[] mappings) {
        try {
            for (int i = 0, len = mappings.length; i < len; i++) {
                FieldMapping mapping = mappings[i];
                Object value = mapping.field.get(bean);
                if (value != null) {
                    mapping.cellWriter.write(worksheet, rowNum,
                            mapping.columnIndex, value);
                }
            }
        } catch (IllegalAccessException e) {
            throw new ExcelMappingException(
                    "Cannot read field value from " + bean.getClass().getName(), e);
        }
    }
}
