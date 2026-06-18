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

import org.dhatim.fastexcel.Workbook;

/**
 * 用于在批量导出生命周期前后自定义 {@link Workbook} 的钩子。
 */
public interface WorkbookCustomizer {

    /** 在 {@link Workbook} 创建之后、任何 Sheet 写入之前调用。 */
    default void beforeSheets(Workbook workbook) {
    }

    /** 在所有 Sheet 写入完成之后、工作簿关闭之前调用。 */
    default void afterSheets(Workbook workbook) {
    }

    /** 空实现的自定义器。 */
    static WorkbookCustomizer none() {
        return new WorkbookCustomizer() {};
    }
}
