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
 * 在批量导出过程中接收进度更新。
 */
public interface ProgressListener {

    /** 在每个 Sheet 开始写入之前调用。{@code sheetCount} 在流式模式下为 -1（未知）。 */
    default void onSheetStarted(int sheetIndex, int sheetCount) {
    }

    /** 在每个 Sheet 完全写入之后调用。{@code sheetCount} 在流式模式下为 -1（未知）。 */
    void onSheetCompleted(int sheetIndex, int sheetCount, int rowsInSheet, int totalWritten);

    /** 在导出完成时调用（无论成功或失败）。 */
    default void onExportFinished(boolean success, int totalWritten) {
    }
}
