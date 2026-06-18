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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将字段标记为映射到 Excel 列。
 *
 * <p>当设置了 {@code index}（非负数）时，字段直接映射到该从零开始的列索引。
 * 当设置了 {@code value}（非空字符串）时，字段通过匹配表头行文本进行映射。
 * {@code index} 的优先级高于 {@code value}。</p>
 *
 * <p>当两者均未指定时，字段按声明顺序映射（未标注注解和未被忽略的字段共享同一次序）。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelProperty {

    /**
     * 用于匹配第一行的表头名称。
     * @return 表头名称，默认为空字符串（未设置）
     */
    String value() default "";

    /**
     * 从零开始的列索引。优先级高于 {@link #value()}。
     * @return 列索引，默认为 -1（未设置）
     */
    int index() default -1;
}
