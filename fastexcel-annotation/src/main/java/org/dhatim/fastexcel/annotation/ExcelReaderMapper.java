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

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 将 Excel {@link Row 行} 映射到标注了 {@link ExcelProperty} /
 * {@link ExcelIgnore} 的 Java Bean 类实例。
 *
 * <p>对于大文件，建议使用 {@link #stream(Sheet)}，它保留底层读取器的流式行为，
 * 而不是一次性将所有行加载到内存中。</p>
 *
 * <pre>{@code
 * ExcelReaderMapper<Person> mapper = new ExcelReaderMapper<>(Person.class);
 * try (ReadableWorkbook wb = new ReadableWorkbook(file);
 *      Stream<Person> stream = mapper.stream(wb.getFirstSheet())) {
 *     stream.forEach(person -> ...);
 * }
 * }</pre>
 *
 * @param <T> Bean 类型
 */
public class ExcelReaderMapper<T> {

    private final Class<T> beanType;
    private final Constructor<T> constructor;
    private final FieldMapping[] fieldMappings;
    private final List<FieldMapping> fieldMappingsList;
    private final boolean needsHeaderResolution;

    /**
     * 为给定的 Bean 类创建映射器。
     * Bean 类必须具有无参构造器。
     *
     * @param beanType Bean 类
     * @throws ExcelMappingException 如果不存在无参构造器
     */
    public ExcelReaderMapper(Class<T> beanType) {
        this.beanType = Objects.requireNonNull(beanType);
        this.constructor = resolveNoArgConstructor(beanType);
        List<FieldMapping> resolved = FieldMapping.resolve(beanType);
        this.fieldMappings = resolved.toArray(new FieldMapping[0]);
        this.fieldMappingsList = resolved;
        this.needsHeaderResolution = resolved.stream()
                .anyMatch(m -> m.hasHeaderName && !m.hasExplicitIndex);
    }

    /**
     * 将单个 {@link Row} 映射为新的 Bean 实例。
     * 所有基于表头名称的映射必须已解析完成（通过
     * {@link #resolveHeaderRow(Row)} 或调用 {@link #map(List)}）。
     *
     * @param row 要映射的行
     * @return 新的 Bean 实例
     * @throws ExcelMappingException 如果映射失败
     */
    public T map(Row row) {
        try {
            T instance = constructor.newInstance();
            int rowCellCount = row.getCellCount();
            FieldMapping[] mappings = this.fieldMappings;
            for (int i = 0, len = mappings.length; i < len; i++) {
                FieldMapping mapping = mappings[i];
                int ci = mapping.columnIndex;
                Cell cell = ci < rowCellCount ? row.getCell(ci) : null;
                Object value = mapping.cellReader.read(cell);
                // 对基本类型字段，null（空单元格）会让 Field.set 抛
                // IllegalArgumentException；跳过则保留字段默认值（0 / false）。
                if (value != null) {
                    mapping.field.set(instance, value);
                }
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new ExcelMappingException(
                    "Cannot map row " + row.getRowNum() + " to "
                            + beanType.getName(), e);
        }
    }

    /**
     * 将行列表映射为 Bean 列表。如果存在基于表头名称的映射且尚未解析，
     * 则第一行将作为表头行被消费。
     *
     * @param rows 行列表
     * @return Bean 实例列表（如果消费了表头行，则不包括表头行）
     */
    public List<T> map(List<Row> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        int startIndex = 0;
        if (needsHeaderResolution && !headerResolved) {
            resolveHeaderRow(rows.get(0));
            startIndex = 1;
        }
        List<T> result = new ArrayList<T>(rows.size() - startIndex);
        for (int i = startIndex; i < rows.size(); i++) {
            result.add(map(rows.get(i)));
        }
        return result;
    }

    /**
     * 从 {@link Sheet} 中读取所有行并将其映射为 Bean。
     * <p>
     * <b>注意：</b>此方法会先将所有行加载到 {@code List} 中。
     * 对于大工作表，请改用 {@link #stream(Sheet)}，它保留底层读取器的流式行为。
     *
     * @param sheet 要读取的工作表
     * @return Bean 实例列表（如果消费了表头行，则不包括表头行）
     * @throws IOException 如果读取失败
     */
    public List<T> map(Sheet sheet) throws IOException {
        try (Stream<Row> rows = sheet.openStream()) {
            List<Row> allRows = new ArrayList<Row>();
            rows.forEach(allRows::add);
            return map(allRows);
        }
    }

    /**
     * 将 {@link Sheet} 中的行以流式方式映射为 Bean。
     * <p>
     * 此方法保留底层 fastexcel 读取器的内存高效流式行为——
     * 行从 ZIP XML 流中逐行解析并即时映射。
     * <p>
     * 返回的流<b>必须关闭</b>（使用 try-with-resources），
     * 以释放底层的 XML 解析器和 ZIP 条目。
     *
     * <pre>{@code
     * try (Stream<Person> stream = mapper.stream(sheet)) {
     *     stream.filter(p -> p.getAge() > 18).forEach(...);
     * }
     * }</pre>
     *
     * @param sheet 要读取的工作表
     * @return 可关闭的 Bean 实例流
     * @throws IOException 如果打开流失败
     */
    public Stream<T> stream(Sheet sheet) throws IOException {
        Stream<Row> rowStream = sheet.openStream();
        Spliterator<Row> rowSpliterator = rowStream.spliterator();

        // Resolve header if needed — consume the header row from the iterator
        if (needsHeaderResolution && !headerResolved) {
            Row[] holder = new Row[1];
            boolean hasHeader = rowSpliterator.tryAdvance(row -> holder[0] = row);
            if (hasHeader) {
                resolveHeaderRow(holder[0]);
            }
        }

        Spliterator<T> beanSpliterator = new Spliterators.AbstractSpliterator<T>(
                rowSpliterator.estimateSize(),
                rowSpliterator.characteristics() & ~Spliterator.DISTINCT) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                return rowSpliterator.tryAdvance(row -> action.accept(map(row)));
            }
        };

        Stream<T> stream = StreamSupport.stream(beanSpliterator, false);
        stream.onClose(rowStream::close);
        return stream;
    }

    // --- header resolution state (volatile for thread-safety) ---
    private volatile boolean headerResolved = false;

    /**
     * 从表头行解析基于表头名称映射的列。
     * 当需要控制将哪一行用作表头，或希望一次性解析表头后
     * 在循环中调用 {@link #map(Row)} 时，显式调用此方法。
     *
     * @param headerRow 表头行
     * @throws ExcelMappingException 如果未找到所需的表头名称
     */
    public synchronized void resolveHeaderRow(Row headerRow) {
        if (headerResolved) {
            return;
        }
        Map<String, Integer> headerMap = new HashMap<String, Integer>();
        for (int i = 0; i < headerRow.getCellCount(); i++) {
            String text = headerRow.getCellText(i).trim();
            if (!text.isEmpty()) {
                headerMap.put(text, i);
            }
        }
        for (FieldMapping m : fieldMappings) {
            if (m.hasHeaderName && !m.hasExplicitIndex) {
                Integer resolved = headerMap.get(m.headerName);
                if (resolved == null) {
                    throw new ExcelMappingException(
                            "Header '" + m.headerName + "' not found in header row "
                                    + headerRow.getRowNum());
                }
                m.columnIndex = resolved;
            }
        }
        headerResolved = true;
    }

    /**
     * 返回此映射器是否需要表头行来进行基于名称的列解析。
     *
     * @return 如果需要解析表头则返回 true
     */
    public boolean needsHeaderRow() {
        return needsHeaderResolution && !headerResolved;
    }

    /**
     * 返回已解析的字段映射（用于自省）。
     *
     * @return 不可修改的字段映射列表
     */
    public List<FieldMapping> getFieldMappings() {
        return Collections.unmodifiableList(fieldMappingsList);
    }

    private static <T> Constructor<T> resolveNoArgConstructor(Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            throw new ExcelMappingException(
                    "Class " + type.getName()
                            + " must have a no-arg constructor", e);
        }
    }
}
