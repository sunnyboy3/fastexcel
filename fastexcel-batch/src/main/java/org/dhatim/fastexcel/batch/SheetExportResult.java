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
 * 批量导出中单个 Sheet 的结果元数据。
 */
public final class SheetExportResult {

    private final String dataPath;
    private final int rowCount;

    SheetExportResult(String dataPath, int rowCount) {
        this.dataPath = dataPath;
        this.rowCount = rowCount;
    }

    /** 该 Sheet 的数据源路径，未设置时为 {@code null}。 */
    public String getDataPath() { return dataPath; }

    /** 已写入的数据行数（不含表头）。 */
    public int getRowCount() { return rowCount; }
}
