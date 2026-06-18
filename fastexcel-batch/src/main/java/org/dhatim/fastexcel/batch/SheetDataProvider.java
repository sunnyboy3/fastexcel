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

import java.util.List;

/**
 * 批量导出的分页数据源。
 *
 * @param <T> 行数据类型
 * @param <P> 从导出调用传入的参数类型
 */
@FunctionalInterface
public interface SheetDataProvider<T, P> {

    /**
     * 获取一个数据切片。
     *
     * @param params  调用者提供的参数（例如过滤器条件）
     * @param request 偏移量、限制条数和 Sheet 索引
     * @return 该 Sheet 的数据行
     * @throws Exception 发生错误时 — 批量导出器会将其包装为
     *         {@link BatchExcelExportException}
     */
    List<T> fetch(P params, DataRequest request) throws Exception;
}
