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
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

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
 *     .fetchTimeoutMs(30_000)
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
    private final ExecutorService externalExecutor;
    private final Function<List<T>, List<T>> dataTransformer;

    // ── constructors ──────────────────────────────────────────────────

    public BatchExcelWriter(ExportOptions options,
                            SheetDataProvider<T, P> provider,
                            RowWriter<T> rowWriter) {
        this(options, provider, rowWriter, null, null, null, null, null, null);
    }

    public BatchExcelWriter(ExportOptions options,
                            SheetDataProvider<T, P> provider,
                            RowWriter<T> rowWriter,
                            BiFunction<P, DataRequest, String> dataPathFn) {
        this(options, provider, rowWriter, dataPathFn, null, null, null, null, null);
    }

    /**
     * 完整构造器（保持向后兼容）。
     */
    public BatchExcelWriter(ExportOptions options,
                            SheetDataProvider<T, P> provider,
                            RowWriter<T> rowWriter,
                            BiFunction<P, DataRequest, String> dataPathFn,
                            SheetNamingStrategy naming,
                            WorkbookCustomizer customizer,
                            ProgressListener progress) {
        this(options, provider, rowWriter, dataPathFn, naming, customizer, progress, null, null);
    }

    /**
     * 扩展构造器，支持自定义线程池和数据转换钩子。
     *
     * @param options          导出配置
     * @param provider         分页数据源
     * @param rowWriter        将行数据写入到 Sheet
     * @param dataPathFn       为每个 Sheet 生成数据源标识符（可为 null）
     * @param naming           Sheet 命名策略（默认：数字后缀）
     * @param customizer       工作簿生命周期钩子（可为 null）
     * @param progress         进度监听器（可为 null）
     * @param executor         自定义线程池（可为 null，使用内部线程池）
     * @param dataTransformer  数据转换钩子，在 fetch 和 write 之间执行（可为 null）
     */
    public BatchExcelWriter(ExportOptions options,
                            SheetDataProvider<T, P> provider,
                            RowWriter<T> rowWriter,
                            BiFunction<P, DataRequest, String> dataPathFn,
                            SheetNamingStrategy naming,
                            WorkbookCustomizer customizer,
                            ProgressListener progress,
                            ExecutorService executor,
                            Function<List<T>, List<T>> dataTransformer) {
        this.options = Objects.requireNonNull(options);
        this.provider = Objects.requireNonNull(provider);
        this.rowWriter = Objects.requireNonNull(rowWriter);
        this.dataPathFn = dataPathFn;
        this.naming = naming != null ? naming : SheetNamingStrategy.numberedSuffix();
        this.customizer = customizer != null ? customizer : WorkbookCustomizer.none();
        this.progress = progress;
        this.externalExecutor = executor;
        this.dataTransformer = dataTransformer;
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
        return export(sink, params, null);
    }

    /**
     * 通过 {@link WorkbookSink} 执行批量导出，支持协作式取消。
     *
     * @param sink      输出目标
     * @param params    传递给数据提供者的参数值
     * @param cancelled 取消信号；返回 true 时在下一个 Sheet 边界停止（可为 null）
     * @return 导出汇总结果（取消时为已写入的部分结果）
     */
    public ExportResult export(WorkbookSink sink, P params, BooleanSupplier cancelled) {
        try (OutputStream os = sink.open()) {
            return export(os, params, cancelled);
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
        return export(outputStream, params, null);
    }

    /**
     * 执行批量导出，支持协作式取消。
     * <p>
     * <b>注意：</b>失败或取消时，已写入的部分内容仍会被 finalize 到
     * {@code outputStream}（工作簿正常关闭）。失败时调用方应丢弃输出；
     * 取消时返回的 {@link ExportResult} 描述已成功写入的 Sheet。
     *
     * @param outputStream 目标输出流
     * @param params       传递给数据提供者的参数值
     * @param cancelled    取消信号；返回 true 时在下一个 Sheet 边界停止（可为 null）
     * @return 导出汇总结果（取消时为已写入的部分结果）
     */
    public ExportResult export(OutputStream outputStream, P params,
                               BooleanSupplier cancelled) {
        boolean bounded = options.isBounded();
        int totalRows = options.getTotalRows();
        int sheetSize = options.getSheetSize();
        int sheetCount = bounded
                ? (totalRows + sheetSize - 1) / sheetSize
                : -1;
        int concurrency = Math.max(1, options.getThreadPoolSize());
        // 预取深度独立于线程数：队列里最多堆放 prefetch 个待写 Sheet。
        int prefetch = Math.max(1, options.getQueueCapacity());
        int flushInterval = options.getFlushInterval();
        long fetchTimeoutMs = options.getFetchTimeoutMs();

        boolean ownExecutor = (externalExecutor == null);
        ExecutorService executor = ownExecutor ? createExecutor(concurrency) : externalExecutor;
        // Bounded queue of futures — submitted in sheet order, so polling
        // preserves order while concurrent fetches happen in the background.
        LinkedBlockingQueue<Future<List<T>>> fetchQueue =
                new LinkedBlockingQueue<Future<List<T>>>(prefetch);
        List<Future<List<T>>> pending = new ArrayList<Future<List<T>>>();

        try (Workbook wb = new Workbook(outputStream, "fastexcel-batch", "1.0")) {
            customizer.beforeSheets(wb);

            // Fill the pipeline up to prefetch depth.
            int nextSheet = 0;
            for (int i = 0; i < prefetch && (sheetCount < 0 || nextSheet < sheetCount);
                 i++, nextSheet++) {
                Future<List<T>> f = submit(executor, params, sheetSize, nextSheet, totalRows, bounded);
                pending.add(f);
                fetchQueue.add(f);
            }

            List<SheetExportResult> sheetResults = new ArrayList<SheetExportResult>();
            int writtenRows = 0;
            Throwable failure = null;
            boolean wasCancelled = false;

            for (int s = 0; sheetCount < 0 || s < sheetCount; s++) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    wasCancelled = true;
                    break;
                }
                Future<List<T>> future = fetchQueue.poll();
                if (future == null) {
                    // No more work queued (streaming pipeline drained).
                    break;
                }
                List<T> rows;
                try {
                    rows = fetchTimeoutMs > 0
                            ? future.get(fetchTimeoutMs, TimeUnit.MILLISECONDS)
                            : future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure = e;
                    break;
                } catch (ExecutionException e) {
                    failure = e.getCause();
                    break;
                } catch (TimeoutException e) {
                    failure = new BatchExcelExportException(
                            "Fetch timed out after " + fetchTimeoutMs
                                    + "ms for sheet " + s, e);
                    break;
                }

                // Streaming: an empty batch means data is exhausted — stop
                // without writing an empty sheet.
                boolean lastBatch = false;
                if (!bounded) {
                    if (rows.isEmpty()) {
                        break;
                    }
                    if (rows.size() < sheetSize) {
                        lastBatch = true; // short batch — no more data after this
                    }
                }

                // Apply data transformer if present
                if (dataTransformer != null) {
                    rows = dataTransformer.apply(rows);
                }

                // Replenish pipeline: submit next fetch while writing this sheet.
                // In streaming mode, stop submitting once we hit a short/last batch.
                if ((sheetCount < 0 || nextSheet < sheetCount) && !lastBatch) {
                    Future<List<T>> f = submit(executor, params, sheetSize,
                            nextSheet, totalRows, bounded);
                    pending.add(f);
                    fetchQueue.add(f);
                    nextSheet++;
                }

                if (progress != null) {
                    progress.onSheetStarted(s, sheetCount);
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

                if (lastBatch) {
                    break;
                }
            }

            // Cancel pending futures on failure, cancellation, or completion
            for (Future<List<T>> f : pending) {
                f.cancel(true);
            }

            customizer.afterSheets(wb);

            if (failure != null) {
                if (progress != null) {
                    progress.onExportFinished(false, writtenRows);
                }
                if (failure instanceof BatchExcelExportException) {
                    throw (BatchExcelExportException) failure;
                }
                throw new BatchExcelExportException(
                        "Failed to fetch data for sheet", failure);
            }
            if (progress != null) {
                progress.onExportFinished(!wasCancelled, writtenRows);
            }
            return new ExportResult(writtenRows, sheetResults);
        } catch (IOException e) {
            throw new BatchExcelExportException("I/O error during export", e);
        } finally {
            if (ownExecutor) {
                executor.shutdownNow();
            }
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
                                    int sheetSize, int sheetIndex, int totalRows,
                                    boolean bounded) {
        // In streaming mode totalRows is unknown (-1) — always request a full page.
        // Use long arithmetic to avoid int overflow when sheetIndex * sheetSize > Integer.MAX_VALUE.
        int limit = bounded
                ? (int) Math.min(sheetSize, totalRows - (long) sheetIndex * sheetSize)
                : sheetSize;
        long offset = (long) sheetIndex * sheetSize;
        DataRequest request = new DataRequest(offset, limit, sheetIndex);
        return executor.submit(() -> {
            try {
                return provider.fetch(params, request);
            } catch (BatchExcelExportException e) {
                throw e;
            } catch (Exception e) {
                throw new BatchExcelExportException(
                        "Failed to fetch data for sheet " + sheetIndex
                                + " (offset=" + offset + ", limit=" + limit + ")", e);
            }
        });
    }

    private ExecutorService createExecutor(int nThreads) {
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
