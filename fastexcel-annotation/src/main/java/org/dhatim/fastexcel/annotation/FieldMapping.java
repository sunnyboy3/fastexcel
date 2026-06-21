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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private static final ConcurrentMap<Class<?>, List<FieldMapping>> CACHE =
            new ConcurrentHashMap<>();

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
     * 解析给定 Bean 类的字段映射，结果会被全局缓存。
     * 由于 {@code columnIndex} 可能在表头解析时被修改，
     * 每次调用返回缓存的浅拷贝（仅复制 columnIndex 数组）。
     */
    static List<FieldMapping> resolve(Class<?> beanType) {
        List<FieldMapping> cached = CACHE.computeIfAbsent(beanType, FieldMapping::doResolve);
        // 浅拷贝：仅复制 columnIndex，复用所有其他字段
        return shallowCopy(cached);
    }

    static void clearCache() {
        CACHE.clear();
    }

    /**
     * 浅拷贝：仅复制 columnIndex 值到新的 FieldMapping 实例，
     * 避免修改缓存中的原始对象。复用 CellReader/CellWriter/Field 引用。
     */
    private static List<FieldMapping> shallowCopy(List<FieldMapping> source) {
        List<FieldMapping> copy = new ArrayList<>(source.size());
        for (FieldMapping m : source) {
            copy.add(new FieldMapping(m.field, m.columnIndex, m.headerName,
                    m.hasExplicitIndex, m.hasHeaderName, m.cellReader, m.cellWriter));
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * 一次性解析给定 Bean 类的字段映射。
     * <ul>
     *   <li>跳过 static、transient 以及 {@code @ExcelIgnore} 字段。</li>
     *   <li>预绑定 {@code CellReader} / {@code CellWriter}，
     *       避免每行重复进行类型分发。</li>
     *   <li>对每个映射字段调用 {@code setAccessible(true)}。</li>
     *   <li>父类字段优先（在子类字段之前）。</li>
     * </ul>
     */
    private static List<FieldMapping> doResolve(Class<?> beanType) {
        List<FieldMapping> mappings = new ArrayList<>();
        int autoIndex = 0;

        for (Field field : resolveFields(beanType)) {
            int mod = field.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
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
            // 自增索引始终跟随最后一个已分配的列号，使得显式 index
            // 不会与后续自增字段串列（例如 explicit index=5 后自增字段从 6 开始）。
            autoIndex = Math.max(autoIndex, columnIndex) + 1;

            Class<?> type = field.getType();
            TypeConverter.CellReader reader = TypeConverter.readerFor(type, field);
            TypeConverter.CellWriter writer = TypeConverter.writerFor(type, field);

            mappings.add(new FieldMapping(field, columnIndex, headerName,
                    hasExplicitIndex, hasHeaderName, reader, writer));
        }
        return Collections.unmodifiableList(mappings);
    }

    /**
     * 从类层次结构中收集所有声明的字段（父类优先），O(n) 时间。
     * 使用递归 + 后序收集，避免 ArrayList 头部插入的 O(n²) 偏移。
     */
    private static List<Field> resolveFields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        collectFields(type, result);
        return result;
    }

    private static void collectFields(Class<?> type, List<Field> result) {
        if (type == null || type == Object.class) {
            return;
        }
        // 先递归父类，再添加子类字段（父类优先）
        collectFields(type.getSuperclass(), result);
        Field[] declared = type.getDeclaredFields();
        // 批量添加比逐个 addAll 更高效
        result.addAll(Arrays.asList(declared));
    }
}