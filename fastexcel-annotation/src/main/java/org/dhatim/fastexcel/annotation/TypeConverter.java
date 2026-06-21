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
import org.dhatim.fastexcel.reader.CellType;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Excel {@link Cell} 值与 Java Bean 字段类型之间的双向类型转换器。
 * <p>
 * 热路径方法通过 {@link #readerFor(Class, Field)} 和 {@link #writerFor(Class, Field)}
 * 暴露为预绑定的函数式接口，使得类型分发仅在构造时执行一次，
 * 而非在每个单元格重复执行。
 * <p>
 * 包内可见——非公开 API。
 */
final class TypeConverter {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private static final ConcurrentMap<Class<? extends Converter<?>>, Converter<?>> CONVERTER_CACHE =
            new ConcurrentHashMap<>();

    /** 枚举常量缓存：避免每次调用 Enum.valueOf() 的反射开销。 */
    private static final ConcurrentMap<Class<? extends Enum<?>>, Map<String, Enum<?>>> ENUM_CACHE =
            new ConcurrentHashMap<>();

    /**
     * 从 {@link Cell} 中提取类型化值的读取函数，
     * 当单元格为 null 或为空时返回 {@code null}。
     */
    interface CellReader {
        Object read(Cell cell);
    }

    /**
     * 将字段值写入 {@link Worksheet} 中指定行和列的写入函数。
     */
    interface CellWriter {
        void write(Worksheet worksheet, int row, int col, Object value);
    }

    // ── Map-based dispatch: O(1) lookup vs O(n) if-else chain ─────────

    private static final Map<Class<?>, CellReader> READER_MAP;
    private static final Map<Class<?>, CellWriter> WRITER_MAP;

    static {
        Map<Class<?>, CellReader> r = new HashMap<>(32);
        Map<Class<?>, CellWriter> w = new HashMap<>(32);

        // ── Readers ───────────────────────────────────────────────

        CellReader stringReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            return cell.getText();
        };
        r.put(String.class, stringReader);

        CellReader bigDecimalReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            CellType ct = cell.getType();
            if (ct == CellType.NUMBER || ct == CellType.FORMULA) return cell.asNumber();
            throw new ExcelMappingException("Cannot convert cell type " + ct + " to BigDecimal");
        };
        r.put(BigDecimal.class, bigDecimalReader);

        CellReader intReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            BigDecimal n = requireNumeric(cell);
            return n != null ? Integer.valueOf(n.intValue()) : null;
        };
        r.put(Integer.class, intReader);
        r.put(int.class, intReader);

        CellReader longReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            BigDecimal n = requireNumeric(cell);
            return n != null ? Long.valueOf(n.longValue()) : null;
        };
        r.put(Long.class, longReader);
        r.put(long.class, longReader);

        CellReader doubleReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            BigDecimal n = requireNumeric(cell);
            return n != null ? Double.valueOf(n.doubleValue()) : null;
        };
        r.put(Double.class, doubleReader);
        r.put(double.class, doubleReader);

        CellReader floatReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            BigDecimal n = requireNumeric(cell);
            return n != null ? Float.valueOf(n.floatValue()) : null;
        };
        r.put(Float.class, floatReader);
        r.put(float.class, floatReader);

        CellReader shortReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            BigDecimal n = requireNumeric(cell);
            return n != null ? Short.valueOf(n.shortValue()) : null;
        };
        r.put(Short.class, shortReader);
        r.put(short.class, shortReader);

        CellReader byteReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            BigDecimal n = requireNumeric(cell);
            return n != null ? Byte.valueOf(n.byteValue()) : null;
        };
        r.put(Byte.class, byteReader);
        r.put(byte.class, byteReader);

        CellReader booleanReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            CellType ct = cell.getType();
            if (ct == CellType.NUMBER) {
                return cell.asNumber().compareTo(BigDecimal.ZERO) != 0;
            }
            if (ct == CellType.BOOLEAN || ct == CellType.FORMULA) {
                return cell.asBoolean();
            }
            throw new ExcelMappingException("Cannot convert cell type " + ct + " to Boolean");
        };
        r.put(Boolean.class, booleanReader);
        r.put(boolean.class, booleanReader);

        CellReader dateReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            LocalDateTime ldt = cell.asDate();
            return ldt != null ? new Date(ldt.atZone(SYSTEM_ZONE).toInstant().toEpochMilli()) : null;
        };
        r.put(Date.class, dateReader);

        CellReader localDateTimeReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            return cell.asDate();
        };
        r.put(LocalDateTime.class, localDateTimeReader);

        CellReader localDateReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            LocalDateTime ldt = cell.asDate();
            return ldt != null ? ldt.toLocalDate() : null;
        };
        r.put(LocalDate.class, localDateReader);

        CellReader zonedDateTimeReader = cell -> {
            if (cell == null || cell.getType() == CellType.EMPTY) return null;
            LocalDateTime ldt = cell.asDate();
            return ldt != null ? ldt.atZone(SYSTEM_ZONE) : null;
        };
        r.put(java.time.ZonedDateTime.class, zonedDateTimeReader);

        READER_MAP = Collections.unmodifiableMap(r);

        // ── Writers ───────────────────────────────────────────────

        CellWriter stringWriter = (ws, row, col, v) -> ws.value(row, col, (String) v);
        w.put(String.class, stringWriter);

        CellWriter numberWriter = (ws, row, col, v) -> ws.value(row, col, (Number) v);
        w.put(BigDecimal.class, numberWriter);
        w.put(Double.class, numberWriter);
        w.put(double.class, numberWriter);
        w.put(Float.class, numberWriter);
        w.put(float.class, numberWriter);
        w.put(Long.class, numberWriter);
        w.put(long.class, numberWriter);
        w.put(Integer.class, numberWriter);
        w.put(int.class, numberWriter);
        w.put(Short.class, numberWriter);
        w.put(short.class, numberWriter);
        w.put(Byte.class, numberWriter);
        w.put(byte.class, numberWriter);

        CellWriter booleanWriter = (ws, row, col, v) -> ws.value(row, col, (Boolean) v);
        w.put(Boolean.class, booleanWriter);
        w.put(boolean.class, booleanWriter);

        CellWriter dateWriter = (ws, row, col, v) -> ws.value(row, col, (Date) v);
        w.put(Date.class, dateWriter);

        CellWriter localDateTimeWriter = (ws, row, col, v) -> ws.value(row, col, (LocalDateTime) v);
        w.put(LocalDateTime.class, localDateTimeWriter);

        CellWriter localDateWriter = (ws, row, col, v) -> ws.value(row, col, (LocalDate) v);
        w.put(LocalDate.class, localDateWriter);

        CellWriter zonedDateTimeWriter = (ws, row, col, v) -> ws.value(row, col, (java.time.ZonedDateTime) v);
        w.put(java.time.ZonedDateTime.class, zonedDateTimeWriter);

        WRITER_MAP = Collections.unmodifiableMap(w);
    }

    private TypeConverter() {
        // utility class
    }

    // ── custom converter support ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Converter<Object> resolveCustomConverter(Field field) {
        ExcelConverter ann = field.getAnnotation(ExcelConverter.class);
        if (ann == null) {
            return null;
        }
        Class<? extends Converter<?>> converterClass = ann.value();
        Converter<?> converter = CONVERTER_CACHE.computeIfAbsent(converterClass, clz -> {
            try {
                return clz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new ExcelMappingException(
                        "Cannot instantiate converter: " + clz.getName(), e);
            }
        });
        return (Converter<Object>) converter;
    }

    // ── pre-bound factory (hot-path elimination) ──────────────────────

    static CellReader readerFor(Class<?> targetType, Field field) {
        Converter<Object> custom = resolveCustomConverter(field);
        if (custom != null) {
            return custom::read;
        }
        Converter<Object> global = ConverterRegistry.lookup(targetType);
        if (global != null) {
            return global::read;
        }
        return readerFor(targetType);
    }

    static CellWriter writerFor(Class<?> targetType, Field field) {
        Converter<Object> custom = resolveCustomConverter(field);
        if (custom != null) {
            return (ws, r, c, v) -> custom.write(ws, r, c, v);
        }
        Converter<Object> global = ConverterRegistry.lookup(targetType);
        if (global != null) {
            return (ws, r, c, v) -> global.write(ws, r, c, v);
        }
        return writerFor(targetType);
    }

    /**
     * 内置类型的 CellReader 查找（O(1) 基于 Map 分发）。
     * Enum 类型通过缓存的 name-to-constant 映射查找，避免重复反射。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static CellReader readerFor(Class<?> targetType) {
        CellReader builtin = READER_MAP.get(targetType);
        if (builtin != null) {
            return builtin;
        }
        // Enum support with caching
        if (targetType.isEnum()) {
            Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
            Map<String, Enum<?>> enumMap = getEnumCache(enumType);
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                String text = cell.getText();
                if (text == null || text.isEmpty()) return null;
                text = text.trim();
                Enum<?> value = enumMap.get(text);
                if (value == null) {
                    throw new ExcelMappingException(
                            "No enum constant " + enumType.getName() + "." + text);
                }
                return value;
            };
        }
        throw new ExcelMappingException("Unsupported field type: " + targetType.getName()
                + ". Use @ExcelConverter to provide a custom converter.");
    }

    /**
     * 内置类型的 CellWriter 查找（O(1) 基于 Map 分发）。
     */
    static CellWriter writerFor(Class<?> targetType) {
        CellWriter builtin = WRITER_MAP.get(targetType);
        if (builtin != null) {
            return builtin;
        }
        // Enum support — write as name()
        if (targetType.isEnum()) {
            return (ws, r, c, v) -> ws.value(r, c, ((Enum<?>) v).name());
        }
        // Fallback: toString()
        return (ws, r, c, v) -> ws.value(r, c, v.toString());
    }

    // ── enum cache ────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Enum<?>> getEnumCache(Class<? extends Enum> enumType) {
        return ENUM_CACHE.computeIfAbsent((Class) enumType, type -> {
            Enum[] constants = type.getEnumConstants();
            Map<String, Enum<?>> map = new HashMap<>(constants.length * 2);
            for (Enum<?> e : constants) {
                map.put(e.name(), e);
            }
            return Collections.unmodifiableMap(map);
        });
    }

    /**
     * 清空枚举缓存（主要用于测试）。
     */
    static void clearEnumCache() {
        ENUM_CACHE.clear();
    }

    private static BigDecimal requireNumeric(Cell cell) {
        CellType cellType = cell.getType();
        if (cellType == CellType.NUMBER || cellType == CellType.FORMULA) {
            return cell.asNumber();
        }
        throw new ExcelMappingException(
                "Expected NUMBER cell but got " + cellType
                        + " at " + cell.getAddress());
    }
}