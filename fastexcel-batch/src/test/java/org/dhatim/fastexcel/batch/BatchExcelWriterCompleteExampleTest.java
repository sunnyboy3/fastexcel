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
import org.dhatim.fastexcel.annotation.ExcelIgnore;
import org.dhatim.fastexcel.annotation.ExcelProperty;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 完整示例：演示 {@link BatchExcelWriter} 结合
 * {@link WorkbookSink}、{@link WorkbookCustomizer}、
 * {@link ProgressListener}、{@link SheetNamingStrategy}
 * 的各种用法。
 */
public class BatchExcelWriterCompleteExampleTest {

    // ── 1. 定义数据 Bean ────────────────────────────────────────────

    public static class Order {
        @ExcelProperty(value = "订单号", index = 0)
        private String orderNo;

        @ExcelProperty(value = "客户名称", index = 1)
        private String customerName;

        @ExcelProperty(value = "金额", index = 2)
        private BigDecimal amount;

        @ExcelProperty(value = "下单日期", index = 3)
        private LocalDate orderDate;

        @ExcelIgnore
        private String internalId;

        public Order() {}

        public Order(String orderNo, String customerName,
                     BigDecimal amount, LocalDate orderDate) {
            this.orderNo = orderNo;
            this.customerName = customerName;
            this.amount = amount;
            this.orderDate = orderDate;
        }

        public String getOrderNo() { return orderNo; }
        public String getCustomerName() { return customerName; }
        public BigDecimal getAmount() { return amount; }
        public LocalDate getOrderDate() { return orderDate; }
    }

    // ── 2. 模拟数据源 ──────────────────────────────────────────────

    /**
     * 模拟分页查询——实际项目中替换为 DAO / 数据库查询。
     */
    static class MockOrderDao {
        private final int totalRows;

        MockOrderDao(int totalRows) { this.totalRows = totalRows; }

        List<Order> findByPage(long offset, int limit) {
            List<Order> list = new ArrayList<>(limit);
            long end = Math.min(offset + limit, totalRows);
            for (long i = offset; i < end; i++) {
                list.add(new Order(
                        "ORD-" + String.format("%06d", i),
                        "客户" + (i % 50),
                        new BigDecimal(String.format("%.2f", 100.0 + i * 13.7)),
                        LocalDate.of(2024, 1, 1).plusDays(i)));
            }
            return list;
        }
    }

    // ── 3. 导出到本地文件 ────────────────────────────────────────

    @Test
    void exportToLocalFile() throws Exception {
        MockOrderDao dao = new MockOrderDao(5000);

        ExportOptions options = ExportOptions.builder(5000)
                .sheetSize(2000)      // 每个 Sheet 2000 行
                .threadPoolSize(2)    // 2 个线程并发拉取数据
                .sheetNamePrefix("订单")
                .build();

        WorkbookCustomizer customizer = new WorkbookCustomizer() {
            @Override
            public void beforeSheets(Workbook wb) {
                wb.properties()
                        .setTitle("订单导出报表")
                        .setCategory("业务报表");
            }
        };

        BatchExcelWriter<Order, Void> writer = new BatchExcelWriter<>(
                options,
                (params, req) -> dao.findByPage(req.getOffset(), req.getLimit()),
                RowWriters.annotation(Order.class),
                null,                                // dataPathFn
                SheetNamingStrategy.numberedSuffix(),
                customizer,
                (sheetIdx, sheetCount, rowsInSheet, totalWritten) ->
                        System.out.printf("Sheet %d/%d: %d 行 (累计 %d)%n",
                                sheetIdx + 1, sheetCount, rowsInSheet, totalWritten));

        Path tempFile = Files.createTempFile("orders-", ".xlsx");
        try {
            ExportResult result = writer.export(
                    WorkbookSink.toPath(tempFile), null);

            assertThat(result.getTotalRows()).isEqualTo(5000);
            assertThat(result.getSheetCount()).isEqualTo(3); // ceil(5000/2000)
            assertThat(Files.size(tempFile)).isGreaterThan(0);

            System.out.println("导出完成: " + result.getTotalRows()
                    + " 行, " + result.getSheetCount() + " 个 Sheet");
            System.out.println("文件: " + tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ── 4. 导出到内存 ────────────────────────────────────────────

    @Test
    void exportToByteArray() throws Exception {
        MockOrderDao dao = new MockOrderDao(100);

        ExportOptions options = ExportOptions.builder(100)
                .sheetSize(50)
                .sheetNamePrefix("Data")
                .build();

        BatchExcelWriter<Order, Void> writer = new BatchExcelWriter<>(
                options,
                (params, req) -> dao.findByPage(req.getOffset(), req.getLimit()),
                RowWriters.annotation(Order.class));

        ExportResult result = writer.export(WorkbookSink.toByteArray(), null);

        assertThat(result.getTotalRows()).isEqualTo(100);
        assertThat(result.getSheetCount()).isEqualTo(2);
    }

    // ── 5. 导出并上传 OSS（模拟）─────────────────────────────────

    @Test
    void exportToCloudStorage() throws Exception {
        MockOrderDao dao = new MockOrderDao(200);

        ExportOptions options = ExportOptions.builder(200)
                .sheetSize(100)
                .threadPoolSize(1)
                .sheetNamePrefix("Report")
                .build();

        // 模拟云上传：写完后回调
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        WorkbookSink cloudSink = WorkbookSink.of(() -> {
            // 真实场景：返回 PipedOutputStream，另启线程上传
            // PipedOutputStream pos = new PipedOutputStream();
            // executor.submit(() -> ossClient.upload(bucket, key,
            //         new PipedInputStream(pos)));
            // return pos;
            return captured;
        });

        BatchExcelWriter<Order, Void> writer = new BatchExcelWriter<>(
                options,
                (params, req) -> dao.findByPage(req.getOffset(), req.getLimit()),
                RowWriters.annotation(Order.class));

        ExportResult result = writer.export(cloudSink, null);

        assertThat(result.getTotalRows()).isEqualTo(200);
        assertThat(captured.size()).isGreaterThan(0);
        System.out.println("云上传完成，文件大小: " + captured.size() + " bytes");
    }

    // ── 6. 原始模式（不用注解）───────────────────────────────────

    @Test
    void exportRawWithoutAnnotations() throws Exception {
        List<String[]> rawData = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rawData.add(new String[]{"行" + i, String.valueOf(i * 10)});
        }

        ExportOptions options = ExportOptions.builder(10)
                .sheetSize(5)
                .sheetNamePrefix("RawSheet")
                .build();

        // 不使用注解，直接控制单元格写入
        RowWriter<String[]> rawWriter = new RowWriter<String[]>() {
            @Override
            public void writeHeader(Worksheet worksheet) {
                worksheet.value(0, 0, "名称");
                worksheet.value(0, 1, "数值");
            }

            @Override
            public void writeRow(Worksheet worksheet, int rowIndex,
                                 String[] row) {
                worksheet.value(rowIndex, 0, row[0]);
                worksheet.value(rowIndex, 1, row[1]);
            }
        };

        BatchExcelWriter<String[], Void> writer = new BatchExcelWriter<>(
                options,
                (params, req) -> rawData.subList(
                        (int) req.getOffset(),
                        (int) (req.getOffset() + req.getLimit())),
                RowWriters.raw(rawWriter));

        ExportResult result = writer.export(WorkbookSink.toByteArray(), null);

        assertThat(result.getTotalRows()).isEqualTo(10);
        assertThat(result.getSheetCount()).isEqualTo(2);
    }
}
