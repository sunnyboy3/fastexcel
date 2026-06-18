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
 * 描述要为单个 Sheet 获取的数据切片。
 */
public final class DataRequest {

    private final long offset;
    private final int limit;
    private final int sheetIndex;

    DataRequest(long offset, int limit, int sheetIndex) {
        this.offset = offset;
        this.limit = limit;
        this.sheetIndex = sheetIndex;
    }

    /**
     * 从零开始的行偏移量。
     */
    public long getOffset() {
        return offset;
    }

    /**
     * 最大返回行数。
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 导出过程中从零开始的 Sheet 索引。
     */
    public int getSheetIndex() {
        return sheetIndex;
    }
}
