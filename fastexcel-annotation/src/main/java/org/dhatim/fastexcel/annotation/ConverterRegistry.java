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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按字段类型注册的全局 {@link Converter} 注册表。
 *
 * <p>不同于逐字段标注的 {@link ExcelConverter}，这里注册的转换器对整个
 * JVM 内所有该类型的字段生效，无需在每个字段上重复标注。适合为值对象、
 * 业务枚举或第三方类型一次性提供读写规则。</p>
 *
 * <p>解析优先级：字段上的 {@link ExcelConverter}（最高） &gt; 本注册表 &gt;
 * 内置类型分支。</p>
 *
 * <pre>{@code
 * ConverterRegistry.register(Money.class, new MoneyConverter());
 * // 之后所有 Money 类型字段自动使用 MoneyConverter，无需 @ExcelConverter
 * }</pre>
 *
 * <p>本类线程安全。注册通常在应用启动阶段完成。</p>
 */
public final class ConverterRegistry {

    private static final ConcurrentMap<Class<?>, Converter<?>> REGISTRY =
            new ConcurrentHashMap<>();

    private ConverterRegistry() {
    }

    /**
     * 为给定类型注册全局转换器。
     *
     * @param type      字段类型
     * @param converter 该类型的转换器
     * @param <T>       字段类型
     */
    public static <T> void register(Class<T> type, Converter<T> converter) {
        REGISTRY.put(Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(converter, "converter"));
    }

    /**
     * 移除给定类型的全局转换器。
     *
     * @param type 字段类型
     * @return 被移除的转换器，若无则为 {@code null}
     */
    public static Converter<?> unregister(Class<?> type) {
        return REGISTRY.remove(type);
    }

    /**
     * 查询给定类型的全局转换器。
     *
     * @param type 字段类型
     * @return 已注册的转换器，若无则为 {@code null}
     */
    @SuppressWarnings("unchecked")
    static Converter<Object> lookup(Class<?> type) {
        return (Converter<Object>) REGISTRY.get(type);
    }

    /**
     * 清空所有注册（主要用于测试）。
     */
    public static void clear() {
        REGISTRY.clear();
    }
}
