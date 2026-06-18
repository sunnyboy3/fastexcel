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
 * 在批量导出过程中生成 Sheet 名称。
 */
@FunctionalInterface
public interface SheetNamingStrategy {

    /**
     * 构建 Sheet 名称。
     *
     * @param prefix     来自 {@link ExportOptions} 中配置的前缀
     * @param sheetIndex 从零开始的 Sheet 索引
     * @param sheetCount Sheet 总数
     * @return 非 null 的 Sheet 名称
     */
    String name(String prefix, int sheetIndex, int sheetCount);

    /**
     * 默认策略：{@code prefix + "_" + (sheetIndex + 1)}。
     */
    static SheetNamingStrategy numberedSuffix() {
        return (prefix, sheetIndex, sheetCount) ->
                prefix + "_" + (sheetIndex + 1);
    }
}
