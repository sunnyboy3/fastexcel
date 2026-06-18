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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 已解析的字段到列映射元数据的内部容器。
 * <p>
 * 携带预绑定的 {@link TypeConverter.CellReader} 和
 * {@link TypeConverter.CellWriter}，使得类型分发仅在构造时执行一次，
 * 而非在每一行/每个单元格重复执行。
 * <p>
 * 包内可见——非公开 API。
 */
final class FieldMapping {

    final Field field;
    final String headerName;
    final Class<?> fieldType;
    final boolean hasExplicitIndex;
    final boolean hasHeaderName;
    int columnIndex;

    final TypeConverter.CellReader cellReader;
    final TypeConverter.CellWriter cellWriter;

    FieldMapping(Field field, int columnIndex, String headerName,
                 boolean hasExplicitIndex, boolean hasHeaderName,
                 TypeConverter.CellReader cellReader,
                 TypeConverter.CellWriter cellWriter) {
        this.field = field;
        this.columnIndex = columnIndex;
        this.headerName = headerName;
        this.fieldType = field.getType();
        this.hasExplicitIndex = hasExplicitIndex;
        this.hasHeaderName = hasHeaderName;
        this.cellReader = cellReader;
        this.cellWriter = cellWriter;
    }

    /**
     * 一次性解析给定 Bean 类的字段映射。
     * <ul>
     *   <li>跳过 static、transient 以及 {@code @ExcelIgnore} 字段。</li>
     *   <li>预绑定 {@code CellReader} / {@code CellWriter}，
     *       避免每行重复进行类型分发。</li>
     *   <li>对每个映射字段调用 {@code setAccessible(true)}。</li>
     * </ul>
     */
    static List<FieldMapping> resolve(Class<?> beanType) {
        List<FieldMapping> mappings = new ArrayList<FieldMapping>();
        int autoIndex = 0;

        for (Field field : getAllFields(beanType)) {
            int mod = field.getModifiers();
            if (Modifier.isStatic(mod)) {
                continue;
            }
            if (Modifier.isTransient(mod)) {
                continue;
            }
            if (field.isAnnotationPresent(ExcelIgnore.class)) {
                continue;
            }

            // Make accessible once — not on every row
            field.setAccessible(true);

            ExcelProperty ep = field.getAnnotation(ExcelProperty.class);
            boolean hasExplicitIndex = false;
            boolean hasHeaderName = false;
            int columnIndex = -1;
            String headerName = null;

            if (ep != null) {
                if (ep.index() >= 0) {
                    columnIndex = ep.index();
                    hasExplicitIndex = true;
                }
                if (!ep.value().isEmpty()) {
                    headerName = ep.value();
                    hasHeaderName = true;
                }
            }
            if (!hasExplicitIndex) {
                columnIndex = autoIndex;
            }

            Class<?> type = field.getType();
            TypeConverter.CellReader reader = TypeConverter.readerFor(type);
            TypeConverter.CellWriter writer = TypeConverter.writerFor(type);

            mappings.add(new FieldMapping(field, columnIndex, headerName,
                    hasExplicitIndex, hasHeaderName, reader, writer));
            autoIndex++;
        }
        return Collections.unmodifiableList(mappings);
    }

    /**
     * 从类层次结构中收集所有声明的字段（父类优先）。
     */
    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(0, Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
}
