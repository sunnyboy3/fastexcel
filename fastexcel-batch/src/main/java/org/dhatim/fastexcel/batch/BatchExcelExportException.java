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

/**
 * 当批量导出失败时抛出 — 通常是因为 {@link SheetDataProvider}
 * 在获取某一 Sheet 数据时抛出了异常。
 */
public class BatchExcelExportException extends RuntimeException {

    /** @param message 错误描述 */
    public BatchExcelExportException(String message) {
        super(message);
    }

    /** @param message 错误描述
     *  @param cause   根因 */
    public BatchExcelExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
