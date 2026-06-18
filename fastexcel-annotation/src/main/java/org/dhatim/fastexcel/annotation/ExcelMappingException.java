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

/**
 * 当 Excel 到 Bean 的映射失败时抛出的异常。
 * 继承自 {@link RuntimeException}，因此调用方不必强制捕获。
 */
public class ExcelMappingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExcelMappingException(String message) {
        super(message);
    }

    public ExcelMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
