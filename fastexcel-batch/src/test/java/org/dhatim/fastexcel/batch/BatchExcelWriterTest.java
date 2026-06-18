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
import org.dhatim.fastexcel.annotation.ExcelProperty;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BatchExcelWriterTest {

    public static class Person {
        @ExcelProperty(value = "Name", index = 0)
        private String name;

        @ExcelProperty(value = "Age", index = 1)
        private Integer age;

        public Person() {
        }

        Person(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void exportsAnnotatedBeansAcrossSheets() throws Exception {
        ExportOptions options = ExportOptions.builder(5)
                .sheetSize(2)
                .threadPoolSize(2)
                .queueCapacity(1)
                .sheetNamePrefix("People")
                .build();
        SheetDataProvider<Person, String> provider = (params, request) -> {
            List<Person> people = new ArrayList<Person>();
            for (int i = 0; i < request.getLimit(); i++) {
                int value = (int) request.getOffset() + i;
                people.add(new Person(params + value, 20 + value));
            }
            return people;
        };
        BatchExcelWriter<Person, String> writer =
                new BatchExcelWriter<Person, String>(options, provider,
                        RowWriters.annotation(Person.class),
                        (params, request) -> "/people?name=" + params
                                + "&offset=" + request.getOffset());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ExportResult result = writer.export(outputStream, "user-");

        assertThat(result.getTotalRows()).isEqualTo(5);
        assertThat(result.getSheetCount()).isEqualTo(3);
        assertThat(result.getSheets()).hasSize(3);
        assertThat(result.getSheets().get(1).getDataPath())
                .isEqualTo("/people?name=user-&offset=2");
        assertThat(writer.getDataPath("user-", 2))
                .isEqualTo("/people?name=user-&offset=4");

        try (ReadableWorkbook workbook = new ReadableWorkbook(
                new ByteArrayInputStream(outputStream.toByteArray()))) {
            List<Sheet> sheets = workbook.getSheets().collect(Collectors.toList());
            assertThat(sheets).hasSize(3);
            assertRows(sheets.get(0), "Name", "user-0", "user-1");
            assertRows(sheets.get(1), "Name", "user-2", "user-3");
            assertRows(sheets.get(2), "Name", "user-4");
        }
    }

    @Test
    void exportsRawRowsWithFlushAcrossSheets() throws Exception {
        ExportOptions options = ExportOptions.builder(4)
                .sheetSize(2)
                .threadPoolSize(1)
                .queueCapacity(1)
                .flushInterval(1)
                .sheetNamePrefix("Raw")
                .build();
        SheetDataProvider<Integer, Void> provider = (params, request) -> {
            List<Integer> rows = new ArrayList<Integer>();
            for (int i = 0; i < request.getLimit(); i++) {
                rows.add((int) request.getOffset() + i);
            }
            return rows;
        };
        RowWriter<Integer> rowWriter = new RowWriter<Integer>() {
            @Override
            public void writeHeader(Worksheet worksheet) {
                worksheet.value(0, 0, "Index");
            }

            @Override
            public void writeRow(Worksheet worksheet, int rowIndex,
                                 Integer row) {
                worksheet.value(rowIndex, 0, row);
            }
        };
        BatchExcelWriter<Integer, Void> writer =
                new BatchExcelWriter<Integer, Void>(options, provider,
                        RowWriters.raw(rowWriter));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ExportResult result = writer.export(outputStream, null);

        assertThat(result.getSheets()).extracting(SheetExportResult::getRowCount)
                .containsExactly(2, 2);
        try (ReadableWorkbook workbook = new ReadableWorkbook(
                new ByteArrayInputStream(outputStream.toByteArray()))) {
            List<Sheet> sheets = workbook.getSheets().collect(Collectors.toList());
            assertRows(sheets.get(0), "Index", "0", "1");
            assertRows(sheets.get(1), "Index", "2", "3");
        }
    }

    @Test
    void rejectsInvalidOptions() {
        assertThatThrownBy(() -> ExportOptions.builder(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalRows");
        assertThatThrownBy(() -> ExportOptions.builder(1)
                .sheetSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sheetSize");
        assertThatThrownBy(() -> ExportOptions.builder(1)
                .queueCapacity(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueCapacity");
    }

    @Test
    void propagatesFetchFailuresAndStopsSubmittingUnboundedWork() {
        ExportOptions options = ExportOptions.builder(10)
                .sheetSize(1)
                .threadPoolSize(1)
                .queueCapacity(1)
                .build();
        AtomicInteger calls = new AtomicInteger();
        SheetDataProvider<Integer, Void> provider = (params, request) -> {
            calls.incrementAndGet();
            if (request.getSheetIndex() == 0) {
                throw new IllegalStateException("boom");
            }
            List<Integer> rows = new ArrayList<Integer>();
            rows.add(request.getSheetIndex());
            return rows;
        };
        BatchExcelWriter<Integer, Void> writer =
                new BatchExcelWriter<Integer, Void>(options, provider,
                        RowWriters.raw(new RowWriter<Integer>() {
                            @Override
                            public void writeRow(Worksheet worksheet,
                                                 int rowIndex, Integer row) {
                                worksheet.value(rowIndex, 0, row);
                            }
                        }));

        assertThatThrownBy(() -> writer.export(new ByteArrayOutputStream(), null))
                .isInstanceOf(BatchExcelExportException.class)
                .hasMessageContaining("Failed to fetch");
        assertThat(calls.get()).isLessThanOrEqualTo(3);
    }

    private static void assertRows(Sheet sheet, String header,
                                   String... expectedValues) throws Exception {
        List<Row> rows = sheet.read();
        assertThat(rows.get(0).getCellText(0)).isEqualTo(header);
        for (int i = 0; i < expectedValues.length; i++) {
            assertThat(rows.get(i + 1).getCellText(0))
                    .isEqualTo(expectedValues[i]);
        }
    }
//    使用方法
//    private void downloadFile(Long employeeId, QuestionListIn questionListIn) throws IOException {
//        // 先查一次总数（仅取1条数据获取 total），避免硬编码
//        QuestionListIn countQuery = copyQuestionListIn(questionListIn);
//        countQuery.setPageNum(1);
//        countQuery.setPageSize(1);
//        int sheetSize = 20;
//
//        long totalRows = getQuestions(employeeId, countQuery).getTotal();
//        LOGGER.info("导出题目总数: {}", totalRows);
//
//        int dbPageSize = questionListIn.getPageSize();
//        ExportOptions options = ExportOptions.builder((int) totalRows)
//                .sheetSize(sheetSize)
//                .threadPoolSize(5)
//                .sheetNamePrefix("题目")
//                .build();
//
//        WorkbookCustomizer customizer = new WorkbookCustomizer() {
//            @Override
//            public void beforeSheets(Workbook wb) {
//                wb.properties()
//                        .setTitle("题目导出报表")
//                        .setCategory("业务报表");
//            }
//        };
//
//        BatchExcelWriter<QuestionExcel, Void> writer = new BatchExcelWriter<>(
//                options,
//                (params, req) -> {
//                    List<QuestionExcel> questionExcels = new ArrayList<>(req.getLimit());
//                    return questionExcels;
//                },
//                RowWriters.annotation(QuestionExcel.class),
//                null,                                // dataPathFn
//                SheetNamingStrategy.numberedSuffix(),
//                customizer,
//                (sheetIdx, sheetCount, rowsInSheet, totalWritten) ->
//                        LOGGER.info("Sheet {}/{}: {} 行 (累计 {})",
//                                sheetIdx + 1, sheetCount, rowsInSheet, totalWritten));
//
//        Path tempFile = Files.createTempFile("orders-", ".xlsx");
//        try {
//            ExportResult result = writer.export(
//                    WorkbookSink.toPath(tempFile), null);
//            System.out.println("导出完成: " + result.getTotalRows()
//                    + " 行, " + result.getSheetCount() + " 个 Sheet");
//            System.out.println("文件: " + tempFile);
//        } catch (Exception e) {
//            System.out.println("导出失败: " + e.getMessage());
//        }
//    }

}
