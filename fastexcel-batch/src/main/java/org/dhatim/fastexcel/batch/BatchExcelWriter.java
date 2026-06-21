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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import java.util.function.Predicate;

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
        long sheetCount = bounded
                ? (totalRows + (long) sheetSize - 1) / sheetSize
                : -1L;

        // 使用独立的线程池，确保核心线程超时后可回收
        int concurrency = Math.max(1, options.getThreadPoolSize());
        int prefetch = Math.max(1, options.getQueueCapacity());
        int flushInterval = options.getFlushInterval();
        long fetchTimeoutMs = options.getFetchTimeoutMs();

        boolean ownExecutor = (externalExecutor == null);
        ExecutorService executor = ownExecutor ? createExecutor(concurrency) : externalExecutor;

        // 有界队列：按 sheet 顺序提交，poll 保持写入顺序，
        // 同时后台并发获取。队列容量控制背压。
        LinkedBlockingQueue<Future<List<T>>> fetchQueue =
                new LinkedBlockingQueue<>(prefetch);
        // 使用 Deque 追踪 pending futures（仅在取消时需要）
        Deque<Future<List<T>>> pending = new ArrayDeque<>();

        try (Workbook wb = new Workbook(outputStream, "fastexcel-batch", "1.0")) {
            customizer.beforeSheets(wb);

            // ── 管线预填充 ────────────────────────────────────────────
            int nextSheet = 0;
            for (int i = 0; i < prefetch && isMoreSheets(sheetCount, nextSheet);
                 i++, nextSheet++) {
                Future<List<T>> f = submit(executor, params, sheetSize,
                        nextSheet, totalRows, bounded);
                if (f == null) break; // limit <= 0, nothing to fetch
                pending.addLast(f);
                fetchQueue.add(f);
            }

            List<SheetExportResult> sheetResults = new ArrayList<>();
            int writtenRows = 0;
            Throwable failure = null;
            boolean wasCancelled = false;
            boolean dataExhausted = false;

            for (int s = 0; isMoreSheets(sheetCount, s); s++) {
                // ── 取消检查 ───────────────────────────────────────────
                if (cancelled != null && cancelled.getAsBoolean()) {
                    wasCancelled = true;
                    break;
                }

                // ── 获取下一批数据 ─────────────────────────────────────
                Future<List<T>> future = fetchQueue.poll();
                if (future == null) {
                    break; // 管线已排空
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

                // ── 流式模式：空数据 / 短批次检测 ──────────────────────
                boolean lastBatch = false;
                if (!bounded) {
                    if (rows.isEmpty()) {
                        break;
                    }
                    if (rows.size() < sheetSize) {
                        lastBatch = true;
                    }
                }

                // ── 数据转换 ───────────────────────────────────────────
                if (dataTransformer != null) {
                    rows = dataTransformer.apply(rows);
                }

                // ── 管线补充：写入当前 Sheet 的同时提交下一次 fetch ────
                if (isMoreSheets(sheetCount, nextSheet) && !lastBatch) {
                    Future<List<T>> f = submit(executor, params, sheetSize,
                            nextSheet, totalRows, bounded);
                    if (f != null) {
                        pending.addLast(f);
                        fetchQueue.add(f);
                    }
                    nextSheet++;
                }

                // ── 进度通知：Sheet 开始 ───────────────────────────────
                if (progress != null) {
                    progress.onSheetStarted(s,
                            bounded ? (int) sheetCount : -1);
                }

                // ── 写入 Sheet ─────────────────────────────────────────
                String sheetName = naming.name(options.getSheetNamePrefix(),
                        s, bounded ? (int) sheetCount : -1);
                Worksheet ws = wb.newWorksheet(sheetName);
                rowWriter.writeHeader(ws);

                int rowIdx = 1;
                int prepareThreshold = options.getPrepareParallelThreshold();

                if (rowWriter.supportsParallelPrepare()
                        && rows.size() >= prepareThreshold
                        && concurrency > 1) {
                    // ══ Phase 1: 并行准备（CPU 密集，多线程）═══════════
                    int prepThreads = options.getPrepareThreads() > 0
                            ? Math.min(options.getPrepareThreads(), concurrency)
                            : concurrency;
                    int chunkSize = Math.max(1,
                            (rows.size() + prepThreads - 1) / prepThreads);
                    int actualChunks = (rows.size() + chunkSize - 1) / chunkSize;
                    List<Future<Object[][]>> prepFutures =
                            new ArrayList<>(actualChunks);

                    for (int start = 0; start < rows.size(); start += chunkSize) {
                        final int sIdx = start;
                        final int end = Math.min(start + chunkSize, rows.size());
                        // 通过 subList 创建视图避免数据拷贝，多线程只读安全
                        final List<T> slice = rows.subList(sIdx, end);
                        prepFutures.add(executor.submit(() -> {
                            Object[][] chunk = new Object[slice.size()][];
                            for (int j = 0, jlen = slice.size(); j < jlen; j++) {
                                chunk[j] = rowWriter.prepare(slice.get(j));
                            }
                            return chunk;
                        }));
                    }

                    // ══ Phase 2: 串行写入（I/O，单线程）═══════════════
                    for (int ci = 0; ci < prepFutures.size(); ci++) {
                        Object[][] chunk;
                        try {
                            chunk = prepFutures.get(ci).get();
                        } catch (InterruptedException e) {
                            // 取消剩余未完成的 futures
                            for (int cj = ci + 1; cj < prepFutures.size(); cj++) {
                                prepFutures.get(cj).cancel(true);
                            }
                            Thread.currentThread().interrupt();
                            throw new BatchExcelExportException(
                                    "Row preparation interrupted for sheet " + s, e);
                        } catch (ExecutionException e) {
                            for (int cj = ci + 1; cj < prepFutures.size(); cj++) {
                                prepFutures.get(cj).cancel(true);
                            }
                            Throwable cause = e.getCause();
                            throw new BatchExcelExportException(
                                    "Row preparation failed for sheet " + s, cause);
                        }
                        for (Object[] prep : chunk) {
                            rowWriter.writePreparedRow(ws, rowIdx++, prep);
                            writtenRows++;
                            if (flushInterval > 0 && rowIdx % flushInterval == 0) {
                                ws.flush();
                            }
                        }
                    }
                } else {
                    // 退化路径：串行写入（原有逻辑）
                    for (int i = 0, len = rows.size(); i < len; i++) {
                        T row = rows.get(i);
                        rowWriter.writeRow(ws, rowIdx, row);
                        rowIdx++;
                        writtenRows++;
                        if (flushInterval > 0 && rowIdx % flushInterval == 0) {
                            ws.flush();
                        }
                    }
                }
                // 确保最后一批数据写入：在 sheet 关闭前 flush
                if (flushInterval > 0 && (rowIdx - 1) % flushInterval != 0) {
                    ws.flush();
                }
                ws.close();

                // ── 记录结果 ───────────────────────────────────────────
                String dataPath = dataPathFn != null
                        ? dataPathFn.apply(params,
                                new DataRequest((long) s * sheetSize,
                                        rows.size(), s))
                        : null;
                sheetResults.add(new SheetExportResult(dataPath, rows.size()));

                // ── 进度通知：Sheet 完成 ───────────────────────────────
                if (progress != null) {
                    progress.onSheetCompleted(s,
                            bounded ? (int) sheetCount : -1,
                            rows.size(), writtenRows);
                }

                if (lastBatch) {
                    dataExhausted = true;
                    break;
                }
            }

            // ── 清理 pending futures ───────────────────────────────────
            for (Future<List<T>> f : pending) {
                f.cancel(true);
            }

            customizer.afterSheets(wb);

            // ── 结果处理 ───────────────────────────────────────────────
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

    /**
     * 检查是否还有更多 Sheet 需要处理。
     * sheetCount &lt; 0 表示流式模式（未知总数）。
     */
    private static boolean isMoreSheets(long sheetCount, int sheetIndex) {
        return sheetCount < 0 || sheetIndex < sheetCount;
    }

    /**
     * 提交异步 fetch 任务。返回 null 表示无需提交（limit &lt;= 0）。
     */
    private Future<List<T>> submit(ExecutorService executor, P params,
                                    int sheetSize, int sheetIndex,
                                    int totalRows, boolean bounded) {
        int limit = bounded
                ? (int) Math.min(sheetSize,
                        totalRows - (long) sheetIndex * sheetSize)
                : sheetSize;
        // 当 limit <= 0 时不提交无效的 fetch
        if (limit <= 0) {
            return null;
        }
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
                                + " (offset=" + offset
                                + ", limit=" + limit + ")", e);
            }
        });
    }

    private ExecutorService createExecutor(int nThreads) {
        long idleSec = options.getIdleThreadTimeoutSec();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                nThreads, nThreads,
                idleSec, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "fastexcel-batch");
                    t.setDaemon(true);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}