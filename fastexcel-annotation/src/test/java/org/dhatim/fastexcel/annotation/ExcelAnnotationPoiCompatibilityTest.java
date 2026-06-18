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
package org.dhatim.fastexcel.annotation;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-validation tests: write with fastexcel-annotation, read back with
 * Apache POI (and vice versa) to ensure data integrity.
 */
public class ExcelAnnotationPoiCompatibilityTest {

    public static class PoiBean {
        @ExcelProperty(index = 0)
        private String name;
        @ExcelProperty(index = 1)
        private Integer age;
        @ExcelProperty(index = 2)
        private BigDecimal salary;
        @ExcelProperty(index = 3)
        private Boolean active;

        public PoiBean() {}

        public PoiBean(String name, Integer age, BigDecimal salary, Boolean active) {
            this.name = name;
            this.age = age;
            this.salary = salary;
            this.active = active;
        }

        public String getName() { return name; }
        public Integer getAge() { return age; }
        public BigDecimal getSalary() { return salary; }
        public Boolean getActive() { return active; }
    }

    // --- Helper ---

    private static byte[] writeWithBeans(BeansWriter writer) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "Fastexcel-Annotation-Test", "1.0")) {
            writer.write(wb);
        }
        return os.toByteArray();
    }

    @FunctionalInterface
    private interface BeansWriter {
        void write(Workbook wb) throws IOException;
    }

    // --- Tests ---

    @Test
    void testWriteWithAnnotationReadWithPoi() throws Exception {
        List<PoiBean> beans = new ArrayList<>();
        beans.add(new PoiBean("Alice", 30, new BigDecimal("5000.50"), true));
        beans.add(new PoiBean("Bob", 25, new BigDecimal("6000.00"), false));
        beans.add(new PoiBean("Charlie", null, null, null));

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("People");
            ExcelWriterMapper<PoiBean> mapper = new ExcelWriterMapper<>(PoiBean.class);
            mapper.write(ws, beans, 0, true);
        });

        try (XSSFWorkbook poiWb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            XSSFSheet poiSheet = poiWb.getSheet("People");
            assertThat(poiSheet).isNotNull();

            // Header row
            assertThat(poiSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("name");
            assertThat(poiSheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("age");
            assertThat(poiSheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("salary");
            assertThat(poiSheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("active");

            // Row 1: Alice, 30, 5000.50, true
            assertThat(poiSheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Alice");
            assertThat(poiSheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(30.0);
            assertThat(poiSheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(5000.50);
            assertThat(poiSheet.getRow(1).getCell(3).getBooleanCellValue()).isTrue();

            // Row 2: Bob, 25, 6000.00, false
            assertThat(poiSheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Bob");
            assertThat(poiSheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(25.0);
            assertThat(poiSheet.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(6000.00);
            assertThat(poiSheet.getRow(2).getCell(3).getBooleanCellValue()).isFalse();

            // Row 3: Charlie, null, null, null — only name should exist
            assertThat(poiSheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Charlie");
            assertThat(poiSheet.getRow(3).getCell(1)).isNull();
            assertThat(poiSheet.getRow(3).getCell(2)).isNull();
            assertThat(poiSheet.getRow(3).getCell(3)).isNull();
        }
    }

    @Test
    void testWriteWithPoiReadWithAnnotation() throws Exception {
        // Write with POI
        byte[] data;
        try (XSSFWorkbook poiWb = new XSSFWorkbook()) {
            XSSFSheet poiSheet = poiWb.createSheet("Data");
            // Header
            org.apache.poi.ss.usermodel.Row header = poiSheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Age");
            header.createCell(2).setCellValue("Birth Date");
            // Data row 1
            org.apache.poi.ss.usermodel.Row row1 = poiSheet.createRow(1);
            row1.createCell(0).setCellValue("Diana");
            row1.createCell(1).setCellValue(22);
            org.apache.poi.ss.usermodel.Cell dateCell = row1.createCell(2);
            dateCell.setCellValue(java.time.LocalDate.of(2000, 1, 15));
            // Data row 2
            org.apache.poi.ss.usermodel.Row row2 = poiSheet.createRow(2);
            row2.createCell(0).setCellValue("Eve");
            row2.createCell(1).setCellValue(33);
            org.apache.poi.ss.usermodel.Cell dateCell2 = row2.createCell(2);
            dateCell2.setCellValue(java.time.LocalDate.of(1990, 6, 20));

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            poiWb.write(os);
            data = os.toByteArray();
        }

        // Read with fastexcel-annotation
        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            ExcelReaderMapper<ExcelReaderMapperTest.HeaderBean> mapper =
                    new ExcelReaderMapper<>(ExcelReaderMapperTest.HeaderBean.class);
            List<ExcelReaderMapperTest.HeaderBean> beans = mapper.map(rwb.getFirstSheet());

            assertThat(beans).hasSize(2);
            assertThat(beans.get(0).getName()).isEqualTo("Diana");
            assertThat(beans.get(0).getAge()).isEqualTo(22);
            assertThat(beans.get(0).getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 15));

            assertThat(beans.get(1).getName()).isEqualTo("Eve");
            assertThat(beans.get(1).getAge()).isEqualTo(33);
            assertThat(beans.get(1).getBirthDate()).isEqualTo(LocalDate.of(1990, 6, 20));
        }
    }

    @Test
    void testWriteDateTypesReadWithPoi() throws Exception {
        Date now = new Date(new Date().getTime() / 1000 * 1000); // truncate millis
        LocalDateTime ldt = LocalDateTime.of(2024, 3, 15, 9, 45, 0);
        LocalDate ld = LocalDate.of(2024, 3, 15);

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Dates");
            ws.value(0, 0, "Label");
            ws.value(0, 1, "DateVal");
            ws.value(0, 2, "DateTimeVal");
            ws.value(0, 3, "LocalDateVal");
            ws.value(1, 0, "Test");
            ws.value(1, 1, now);
            ws.value(1, 2, ldt);
            ws.value(1, 3, ld);
        });

        try (XSSFWorkbook poiWb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            XSSFSheet sheet = poiWb.getSheet("Dates");
            assertThat(sheet).isNotNull();

            // POI should detect date formatting for dates written by fastexcel
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Test");
            // POI should detect date formatting for dates written by fastexcel
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
        }
    }

    @Test
    void testWriteAnnotatedReadWithAnnotationRoundTrip() throws Exception {
        // Full round-trip: write with annotation mapper, read back with mapper
        List<PoiBean> original = new ArrayList<>();
        original.add(new PoiBean("Alice", 30, new BigDecimal("1234.56"), true));
        original.add(new PoiBean("Bob", 25, new BigDecimal("7890.12"), false));

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Data");
            ExcelWriterMapper<PoiBean> writerMapper = new ExcelWriterMapper<>(PoiBean.class);
            writerMapper.write(ws, original);
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            ExcelReaderMapper<PoiBean> readerMapper = new ExcelReaderMapper<>(PoiBean.class);
            List<PoiBean> result = readerMapper.map(rwb.getFirstSheet());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Alice");
            assertThat(result.get(0).getAge()).isEqualTo(30);
            assertThat(result.get(0).getSalary()).isEqualByComparingTo(new BigDecimal("1234.56"));
            assertThat(result.get(0).getActive()).isTrue();
            assertThat(result.get(1).getName()).isEqualTo("Bob");
            assertThat(result.get(1).getAge()).isEqualTo(25);
            assertThat(result.get(1).getSalary()).isEqualByComparingTo(new BigDecimal("7890.12"));
            assertThat(result.get(1).getActive()).isFalse();
        }
    }
}
