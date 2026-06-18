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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ExcelWriterMapperTest {

    public static class Person {
        @ExcelProperty(index = 0)
        private String name;
        @ExcelProperty(index = 1)
        private Integer age;
        @ExcelProperty(index = 2)
        private BigDecimal salary;

        public Person() {}

        public Person(String name, Integer age, BigDecimal salary) {
            this.name = name;
            this.age = age;
            this.salary = salary;
        }

        public String getName() { return name; }
        public Integer getAge() { return age; }
        public BigDecimal getSalary() { return salary; }
    }

    public static class HeaderBean {
        @ExcelProperty(value = "Full Name", index = 0)
        private String name;
        @ExcelProperty(value = "Years")
        private Integer age;

        public HeaderBean() {}

        public HeaderBean(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public Integer getAge() { return age; }
    }

    public static class NullableBean {
        @ExcelProperty(index = 0)
        private String name;
        @ExcelProperty(index = 1)
        private String optional;

        public NullableBean() {}

        public NullableBean(String name, String optional) {
            this.name = name;
            this.optional = optional;
        }

        public String getName() { return name; }
        public String getOptional() { return optional; }
    }

    public static class DateBean {
        @ExcelProperty(index = 0)
        private String label;
        @ExcelProperty(index = 1)
        private Date dateVal;
        @ExcelProperty(index = 2)
        private LocalDateTime dateTimeVal;
        @ExcelProperty(index = 3)
        private LocalDate localDateVal;

        public DateBean() {}

        public DateBean(String label, Date dateVal, LocalDateTime dateTimeVal,
                        LocalDate localDateVal) {
            this.label = label;
            this.dateVal = dateVal;
            this.dateTimeVal = dateTimeVal;
            this.localDateVal = localDateVal;
        }

        public String getLabel() { return label; }
        public Date getDateVal() { return dateVal; }
        public LocalDateTime getDateTimeVal() { return dateTimeVal; }
        public LocalDate getLocalDateVal() { return localDateVal; }
    }

    // --- Helper ---

    private static byte[] writeWithBeans(BeansWriter writer) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "Test", "1.0")) {
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
    void testWriteWithoutHeader() throws Exception {
        List<Person> people = Arrays.asList(
                new Person("Alice", 30, new BigDecimal("5000.50")),
                new Person("Bob", 25, new BigDecimal("6000.00"))
        );

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ExcelWriterMapper<Person> mapper = new ExcelWriterMapper<>(Person.class);
            mapper.write(ws, people);
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = rwb.getFirstSheet();
            List<Row> rows = sheet.read();
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getCellText(0)).isEqualTo("Alice");
            assertThat(rows.get(0).getCellText(1)).isEqualTo("30");
            assertThat(rows.get(0).getCellText(2)).isEqualTo("5000.50");
            assertThat(rows.get(1).getCellText(0)).isEqualTo("Bob");
            assertThat(rows.get(1).getCellText(1)).isEqualTo("25");
            assertThat(rows.get(1).getCellText(2)).isEqualTo("6000.00");
        }
    }

    @Test
    void testWriteWithHeader() throws Exception {
        List<HeaderBean> people = Arrays.asList(
                new HeaderBean("Charlie", 28),
                new HeaderBean("Diana", 22)
        );

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ExcelWriterMapper<HeaderBean> mapper = new ExcelWriterMapper<>(HeaderBean.class);
            mapper.write(ws, people, 0, true);
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = rwb.getFirstSheet();
            List<Row> rows = sheet.read();
            assertThat(rows).hasSize(3);
            // Header row
            assertThat(rows.get(0).getCellText(0)).isEqualTo("Full Name");
            assertThat(rows.get(0).getCellText(1)).isEqualTo("Years");
            // Data rows
            assertThat(rows.get(1).getCellText(0)).isEqualTo("Charlie");
            assertThat(rows.get(1).getCellText(1)).isEqualTo("28");
            assertThat(rows.get(2).getCellText(0)).isEqualTo("Diana");
            assertThat(rows.get(2).getCellText(1)).isEqualTo("22");
        }
    }

    @Test
    void testWriteWithStartRow() throws Exception {
        List<Person> people = Arrays.asList(
                new Person("Eve", 33, new BigDecimal("7000.00"))
        );

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ExcelWriterMapper<Person> mapper = new ExcelWriterMapper<>(Person.class);
            mapper.write(ws, people, 2, true);
            // row 2 = header, row 3 = data
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = rwb.getFirstSheet();
            List<Row> rows = sheet.read();
            assertThat(rows).hasSize(2);
            // Row 0 and 1 should be empty or absent — the writer produces
            // rows starting at the requested offset
        }
    }

    @Test
    void testNullFieldSkipped() throws Exception {
        List<NullableBean> beans = Arrays.asList(
                new NullableBean("HasOptional", "present"),
                new NullableBean("NoOptional", null)
        );

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ExcelWriterMapper<NullableBean> mapper = new ExcelWriterMapper<>(NullableBean.class);
            mapper.write(ws, beans);
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = rwb.getFirstSheet();
            List<Row> rows = sheet.read();
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getCellText(0)).isEqualTo("HasOptional");
            assertThat(rows.get(0).getCellText(1)).isEqualTo("present");
            assertThat(rows.get(1).getCellText(0)).isEqualTo("NoOptional");
            // The optional field is null, verify the cell is empty
            assertThat(rows.get(1).getCellText(1)).isEmpty();
        }
    }

    @Test
    void testDateTypes() throws Exception {
        Date now = new Date();
        now = new Date(now.getTime() / 1000 * 1000); // truncate mills for comparison
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30, 0);
        LocalDate ld = LocalDate.of(2024, 6, 15);

        List<DateBean> beans = Arrays.asList(
                new DateBean("Test", now, ldt, ld)
        );

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ExcelWriterMapper<DateBean> mapper = new ExcelWriterMapper<>(DateBean.class);
            mapper.write(ws, beans);
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = rwb.getFirstSheet();
            List<Row> rows = sheet.read();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getCellText(0)).isEqualTo("Test");
            assertThat(rows.get(0).getCell(1).getType().toString()).isIn("NUMBER", "FORMULA");
            assertThat(rows.get(0).getCell(2).getType().toString()).isIn("NUMBER", "FORMULA");
            assertThat(rows.get(0).getCell(3).getType().toString()).isIn("NUMBER", "FORMULA");
        }
    }

    @Test
    void testEmptyList() throws Exception {
        List<Person> people = new ArrayList<>();

        byte[] data = writeWithBeans(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ExcelWriterMapper<Person> mapper = new ExcelWriterMapper<>(Person.class);
            mapper.write(ws, people, 0, true);
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = rwb.getFirstSheet();
            List<Row> rows = sheet.read();
            // Only the header row
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getCellText(0)).isEqualTo("name");
        }
    }

    @Test
    void testGetFieldMappings() {
        ExcelWriterMapper<Person> mapper = new ExcelWriterMapper<>(Person.class);
        List<FieldMapping> mappings = mapper.getFieldMappings();

        assertThat(mappings).hasSize(3);
        assertThat(mappings.get(0).field.getName()).isEqualTo("name");
        assertThat(mappings.get(1).field.getName()).isEqualTo("age");
        assertThat(mappings.get(2).field.getName()).isEqualTo("salary");
    }
}
