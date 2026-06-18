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
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * 使用并发获取-写入流水线将分页数据导出到多 Sheet 的 Excel 工作簿：
 * 在写入当前 Sheet 的同时，后台并发获取下一个 Sheet 的数据。
 *
 * <p>用法示例：
 * <pre>{@code
 * ExportOptions opts = ExportOptions.builder(100_000)
 *     .sheetSize(10_000)
 *     .threadPoolSize(4)
 *     .sheetNamePrefix("Data")
 *     .build();
 *
 * BatchExcelWriter<MyBean, String> writer = new BatchExcelWriter<>(
 *     opts,
 *     (params, req) -> dao.fetch(req.getOffset(), req.getLimit()),
 *     RowWriters.annotation(MyBean.class),
 *     (params, req) -> "/api/data?offset=" + req.getOffset(),
 *     SheetNamingStrategy.numberedSuffix(),
 *     wb -> wb.properties().setTitle("Export"),
 *     (idx, count, rows, total) -> log.info("Sheet {}/{} done", idx + 1, count));
 *
 * ExportResult result = writer.export(outputStream, "filterValue");
 * }</pre>
 *
 * @param <T> 行数据类型
 * @param <P> 传递给数据提供者的参数类型
 */
public class BatchExcelWriter<T, P> {

    private final ExportOptions options;
    private final SheetDataProvider<T, P> provider;
    private final RowWriter<T> rowWriter;
    private final BiFunction<P, DataRequest, String> dataPathFn;
    private final SheetNamingStrategy naming;
    private final WorkbookCustomizer customizer;
    private final ProgressListener progress;

    // ── constructors ──────────────────────────────────────────────────

    public BatchExcelWriter(ExportOptions options,
                            SheetDataProvider<T, P> provider,
                            RowWriter<T> rowWriter) {
        this(options, provider, rowWriter, null, null, null, null);
    }

    public BatchExcelWriter(ExportOptions options,
                            SheetDataProvider<T, P> provider,
                            RowWriter<T> rowWriter,
                            BiFunction<P, DataRequest, String> dataPathFn) {
        this(options, provider, rowWriter, dataPathFn, null, null, null);
    }

    /**
     * 完整构造器。
     *
     * @param options     导出配置
     * @param provider    分页数据源
     * @param rowWriter   将行数据（以及可选的表头）写入到 Sheet
     * @param dataPathFn  为每个 Sheet 生成数据源标识符（可为 null）
     * @param naming      Sheet 命名策略（默认：数字后缀）
     * @param customizer  工作簿生命周期钩子（可为 null）
     * @param progress    进度监听器（可为 null）
     */
    public BatchExcelWriter(ExportOptions options,
                            SheetDataProvider<T, P> provider,
                            RowWriter<T> rowWriter,
                            BiFunction<P, DataRequest, String> dataPathFn,
                            SheetNamingStrategy naming,
                            WorkbookCustomizer customizer,
                            ProgressListener progress) {
        this.options = Objects.requireNonNull(options);
        this.provider = Objects.requireNonNull(provider);
        this.rowWriter = Objects.requireNonNull(rowWriter);
        this.dataPathFn = dataPathFn;
        this.naming = naming != null ? naming : SheetNamingStrategy.numberedSuffix();
        this.customizer = customizer != null ? customizer : WorkbookCustomizer.none();
        this.progress = progress;
    }

    // ── public API ────────────────────────────────────────────────────

    /**
     * 通过 {@link WorkbookSink} 执行批量导出，自动管理输出流的打开和关闭。
     *
     * @param sink   输出目标（文件、内存、云存储等）
     * @param params 传递给数据提供者的参数值
     * @return 导出汇总结果
     * @see WorkbookSink
     */
    public ExportResult export(WorkbookSink sink, P params) {
        try (OutputStream os = sink.open()) {
            return export(os, params);
        } catch (IOException e) {
            throw new BatchExcelExportException("无法打开输出流", e);
        }
    }

    /**
     * 执行批量导出。
     *
     * @param outputStream 目标输出流
     * @param params       传递给数据提供者的参数值
     * @return 导出汇总结果
     */
    public ExportResult export(OutputStream outputStream, P params) {
        int totalRows = options.getTotalRows();
        int sheetSize = options.getSheetSize();
        int sheetCount = (totalRows + sheetSize - 1) / sheetSize;
        int concurrency = Math.max(1, options.getThreadPoolSize());
        int flushInterval = options.getFlushInterval();

        ExecutorService executor = createExecutor();
        // Bounded queue of futures — submitted in sheet order, so take()
        // preserves order while concurrent fetches happen in the background.
        LinkedBlockingQueue<Future<List<T>>> fetchQueue =
                new LinkedBlockingQueue<Future<List<T>>>(concurrency);
        List<Future<List<T>>> pending = new ArrayList<Future<List<T>>>();

        try (Workbook wb = new Workbook(outputStream, "fastexcel-batch", "1.0")) {
            customizer.beforeSheets(wb);

            // Fill the pipeline
            int nextSheet = 0;
            for (int i = 0; i < concurrency && nextSheet < sheetCount; i++, nextSheet++) {
                Future<List<T>> f = submit(executor, params, sheetSize, nextSheet, totalRows);
                pending.add(f);
                fetchQueue.add(f);
            }

            List<SheetExportResult> sheetResults =
                    new ArrayList<SheetExportResult>(sheetCount);
            int writtenRows = 0;
            Throwable failure = null;

            for (int s = 0; s < sheetCount; s++) {
                List<T> rows;
                try {
                    rows = fetchQueue.take().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure = e;
                    break;
                } catch (ExecutionException e) {
                    failure = e.getCause();
                    break;
                }

                // Replenish pipeline: submit next fetch while writing this sheet
                if (nextSheet < sheetCount) {
                    Future<List<T>> f = submit(executor, params, sheetSize,
                            nextSheet, totalRows);
                    pending.add(f);
                    fetchQueue.add(f);
                    nextSheet++;
                }

                // Write sheet
                String sheetName = naming.name(options.getSheetNamePrefix(),
                        s, sheetCount);
                Worksheet ws = wb.newWorksheet(sheetName);
                rowWriter.writeHeader(ws);

                int rowIdx = 1;
                for (T row : rows) {
                    rowWriter.writeRow(ws, rowIdx, row);
                    rowIdx++;
                    writtenRows++;
                    if (flushInterval > 0 && rowIdx % flushInterval == 0) {
                        ws.flush();
                    }
                }
                ws.close();

                String dataPath = dataPathFn != null
                        ? dataPathFn.apply(params,
                                new DataRequest((long) s * sheetSize,
                                        rows.size(), s))
                        : null;
                sheetResults.add(new SheetExportResult(dataPath, rows.size()));

                if (progress != null) {
                    progress.onSheetCompleted(s, sheetCount,
                            rows.size(), writtenRows);
                }
            }

            // Cancel pending futures on failure or completion
            for (Future<List<T>> f : pending) {
                f.cancel(true);
            }

            customizer.afterSheets(wb);

            if (failure != null) {
                if (progress != null) {
                    progress.onExportFinished(false, writtenRows);
                }
                throw new BatchExcelExportException(
                        "Failed to fetch data for sheet", failure);
            }
            if (progress != null) {
                progress.onExportFinished(true, writtenRows);
            }
            return new ExportResult(writtenRows, sheetResults);
        } catch (IOException e) {
            throw new BatchExcelExportException("I/O error during export", e);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 根据给定参数和 Sheet 索引构建数据路径。
     * 仅在提供了数据路径函数时可用。
     */
    public String getDataPath(P params, int sheetIndex) {
        if (dataPathFn == null) {
            return null;
        }
        long offset = (long) sheetIndex * options.getSheetSize();
        return dataPathFn.apply(params,
                new DataRequest(offset, 0, sheetIndex));
    }

    // ── internal ──────────────────────────────────────────────────────

    private Future<List<T>> submit(ExecutorService executor, P params,
                                    int sheetSize, int sheetIndex, int totalRows) {
        int limit = Math.min(sheetSize, totalRows - sheetIndex * sheetSize);
        long offset = (long) sheetIndex * sheetSize;
        DataRequest request = new DataRequest(offset, limit, sheetIndex);
        return executor.submit(() -> {
            try {
                return provider.fetch(params, request);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ExecutorService createExecutor() {
        int nThreads = Math.max(1, options.getThreadPoolSize());
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                nThreads, nThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                r -> {
                    Thread t = new Thread(r, "fastexcel-batch");
                    t.setDaemon(true);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
