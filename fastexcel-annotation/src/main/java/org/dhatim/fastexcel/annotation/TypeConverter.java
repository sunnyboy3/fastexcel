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
import java.util.Date;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    static CellReader readerFor(Class<?> targetType) {
        if (targetType == String.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                return cell.getText();
            };
        }
        if (targetType == BigDecimal.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                CellType ct = cell.getType();
                if (ct == CellType.NUMBER || ct == CellType.FORMULA) return cell.asNumber();
                throw new ExcelMappingException("Cannot convert cell type " + ct + " to BigDecimal");
            };
        }
        if (targetType == Integer.class || targetType == int.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                BigDecimal n = requireNumeric(cell);
                return n != null ? Integer.valueOf(n.intValue()) : null;
            };
        }
        if (targetType == Long.class || targetType == long.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                BigDecimal n = requireNumeric(cell);
                return n != null ? Long.valueOf(n.longValue()) : null;
            };
        }
        if (targetType == Double.class || targetType == double.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                BigDecimal n = requireNumeric(cell);
                return n != null ? Double.valueOf(n.doubleValue()) : null;
            };
        }
        if (targetType == Float.class || targetType == float.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                BigDecimal n = requireNumeric(cell);
                return n != null ? Float.valueOf(n.floatValue()) : null;
            };
        }
        if (targetType == Short.class || targetType == short.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                BigDecimal n = requireNumeric(cell);
                return n != null ? Short.valueOf(n.shortValue()) : null;
            };
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                BigDecimal n = requireNumeric(cell);
                return n != null ? Byte.valueOf(n.byteValue()) : null;
            };
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return cell -> {
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
        }
        if (targetType == Date.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                LocalDateTime ldt = cell.asDate();
                return ldt != null ? new Date(ldt.atZone(SYSTEM_ZONE).toInstant().toEpochMilli()) : null;
            };
        }
        if (targetType == LocalDateTime.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                return cell.asDate();
            };
        }
        if (targetType == LocalDate.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                LocalDateTime ldt = cell.asDate();
                return ldt != null ? ldt.toLocalDate() : null;
            };
        }
        if (targetType == java.time.ZonedDateTime.class) {
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                LocalDateTime ldt = cell.asDate();
                return ldt != null ? ldt.atZone(SYSTEM_ZONE) : null;
            };
        }
        // Enum support
        if (targetType.isEnum()) {
            Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
            return cell -> {
                if (cell == null || cell.getType() == CellType.EMPTY) return null;
                String text = cell.getText();
                if (text == null || text.isEmpty()) return null;
                return Enum.valueOf(enumType, text.trim());
            };
        }
        throw new ExcelMappingException("Unsupported field type: " + targetType.getName()
                + ". Use @ExcelConverter to provide a custom converter.");
    }

    static CellWriter writerFor(Class<?> targetType) {
        if (targetType == String.class) {
            return (ws, r, c, v) -> ws.value(r, c, (String) v);
        }
        if (targetType == BigDecimal.class) {
            return (ws, r, c, v) -> ws.value(r, c, (Number) v);
        }
        if (targetType == Double.class || targetType == double.class
                || targetType == Float.class || targetType == float.class
                || targetType == Long.class || targetType == long.class
                || targetType == Integer.class || targetType == int.class
                || targetType == Short.class || targetType == short.class
                || targetType == Byte.class || targetType == byte.class) {
            return (ws, r, c, v) -> ws.value(r, c, (Number) v);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return (ws, r, c, v) -> ws.value(r, c, (Boolean) v);
        }
        if (targetType == Date.class) {
            return (ws, r, c, v) -> ws.value(r, c, (Date) v);
        }
        if (targetType == LocalDateTime.class) {
            return (ws, r, c, v) -> ws.value(r, c, (LocalDateTime) v);
        }
        if (targetType == LocalDate.class) {
            return (ws, r, c, v) -> ws.value(r, c, (LocalDate) v);
        }
        if (targetType == java.time.ZonedDateTime.class) {
            return (ws, r, c, v) -> ws.value(r, c, (java.time.ZonedDateTime) v);
        }
        // Enum support — write as name()
        if (targetType.isEnum()) {
            return (ws, r, c, v) -> ws.value(r, c, ((Enum<?>) v).name());
        }
        // Fallback
        return (ws, r, c, v) -> ws.value(r, c, v.toString());
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
