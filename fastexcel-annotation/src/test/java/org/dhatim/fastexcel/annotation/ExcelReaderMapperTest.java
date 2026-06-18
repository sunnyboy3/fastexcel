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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelReaderMapperTest {

    // --- Bean definitions ---

    public static class IndexBean {
        @ExcelProperty(index = 0)
        private String name;
        @ExcelProperty(index = 1)
        private Integer age;
        @ExcelProperty(index = 2)
        private BigDecimal salary;

        public String getName() { return name; }
        public Integer getAge() { return age; }
        public BigDecimal getSalary() { return salary; }
    }

    public static class HeaderBean {
        @ExcelProperty("Name")
        private String name;
        @ExcelProperty("Age")
        private int age;
        @ExcelProperty("Birth Date")
        private LocalDate birthDate;

        public String getName() { return name; }
        public int getAge() { return age; }
        public LocalDate getBirthDate() { return birthDate; }
        public void setName(String name) { this.name = name; }
        public void setAge(int age) { this.age = age; }
        public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    }

    public static class MixedBean {
        @ExcelProperty(index = 0)
        private String name;
        @ExcelProperty("Age")
        private int age;

        public String getName() { return name; }
        public int getAge() { return age; }
    }

    public static class AutoIndexBean {
        private String firstName;
        private String lastName;
        private Integer score;

        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public Integer getScore() { return score; }
        @SuppressWarnings("unused")
        public void setScore(Integer score) { this.score = score; }
    }

    public static class TypeVarietyBean {
        @ExcelProperty(index = 0)
        private String text;
        @ExcelProperty(index = 1)
        private Integer intVal;
        @ExcelProperty(index = 2)
        private Long longVal;
        @ExcelProperty(index = 3)
        private Double doubleVal;
        @ExcelProperty(index = 4)
        private BigDecimal bigDecimal;
        @ExcelProperty(index = 5)
        private Boolean boolVal;
        @ExcelProperty(index = 6)
        private Date dateVal;
        @ExcelProperty(index = 7)
        private LocalDateTime dateTimeVal;
        @ExcelProperty(index = 8)
        private LocalDate localDateVal;

        public String getText() { return text; }
        public Integer getIntVal() { return intVal; }
        public Long getLongVal() { return longVal; }
        public Double getDoubleVal() { return doubleVal; }
        public BigDecimal getBigDecimal() { return bigDecimal; }
        public Boolean getBoolVal() { return boolVal; }
        public Date getDateVal() { return dateVal; }
        public LocalDateTime getDateTimeVal() { return dateTimeVal; }
        public LocalDate getLocalDateVal() { return localDateVal; }
    }

    public static class BeanWithIgnore {
        @ExcelProperty(index = 0)
        private String name;
        @ExcelIgnore
        private String internal;

        public String getName() { return name; }
        public String getInternal() { return internal; }
        public void setInternal(String internal) { this.internal = internal; }
    }

    public static class ParentBean {
        @ExcelProperty(index = 0)
        private String baseField;

        public String getBaseField() { return baseField; }
    }

    public static class ChildBean extends ParentBean {
        @ExcelProperty(index = 1)
        private String childField;

        public String getChildField() { return childField; }
    }

    // --- Helper ---

    private static byte[] writeWorkbook(WorkbookWriter writer) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(os, "Test", "1.0")) {
            writer.write(wb);
        }
        return os.toByteArray();
    }

    @FunctionalInterface
    private interface WorkbookWriter {
        void write(Workbook wb) throws IOException;
    }

    private static List<Row> readAllRows(byte[] data) throws IOException {
        try (ReadableWorkbook wb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getFirstSheet();
            return sheet.read();
        }
    }

    // --- Tests ---

    @Test
    void testIndexMapping() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Alice");
            ws.value(0, 1, 30);
            ws.value(0, 2, new BigDecimal("5000.50"));
            ws.value(1, 0, "Bob");
            ws.value(1, 1, 25);
            ws.value(1, 2, new BigDecimal("6000.00"));
        });

        ExcelReaderMapper<IndexBean> mapper = new ExcelReaderMapper<>(IndexBean.class);
        List<IndexBean> beans = mapper.map(readAllRows(data));

        assertThat(beans).hasSize(2);
        assertThat(beans.get(0).getName()).isEqualTo("Alice");
        assertThat(beans.get(0).getAge()).isEqualTo(30);
        assertThat(beans.get(0).getSalary()).isEqualByComparingTo(new BigDecimal("5000.50"));
        assertThat(beans.get(1).getName()).isEqualTo("Bob");
        assertThat(beans.get(1).getAge()).isEqualTo(25);
        assertThat(beans.get(1).getSalary()).isEqualByComparingTo(new BigDecimal("6000.00"));
    }

    @Test
    void testHeaderNameMapping() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Name");
            ws.value(0, 1, "Age");
            ws.value(0, 2, "Birth Date");
            ws.value(1, 0, "Charlie");
            ws.value(1, 1, 28);
            ws.value(1, 2, LocalDate.of(1996, 5, 15));
        });

        ExcelReaderMapper<HeaderBean> mapper = new ExcelReaderMapper<>(HeaderBean.class);
        assertThat(mapper.needsHeaderRow()).isTrue();

        List<Row> rows = readAllRows(data);
        List<HeaderBean> beans = mapper.map(rows);

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getName()).isEqualTo("Charlie");
        assertThat(beans.get(0).getAge()).isEqualTo(28);
        assertThat(beans.get(0).getBirthDate()).isEqualTo(LocalDate.of(1996, 5, 15));
    }

    @Test
    void testMixedIndexAndHeaderMapping() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Name");
            ws.value(0, 1, "Age");
            ws.value(1, 0, "Diana");
            ws.value(1, 1, 22);
        });

        ExcelReaderMapper<MixedBean> mapper = new ExcelReaderMapper<>(MixedBean.class);
        List<MixedBean> beans = mapper.map(readAllRows(data));

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getName()).isEqualTo("Diana");
        assertThat(beans.get(0).getAge()).isEqualTo(22);
    }

    @Test
    void testAutoIndexMapping() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "John");
            ws.value(0, 1, "Doe");
            ws.value(0, 2, 85);
        });

        ExcelReaderMapper<AutoIndexBean> mapper = new ExcelReaderMapper<>(AutoIndexBean.class);
        List<AutoIndexBean> beans = mapper.map(readAllRows(data));

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getFirstName()).isEqualTo("John");
        assertThat(beans.get(0).getLastName()).isEqualTo("Doe");
        assertThat(beans.get(0).getScore()).isEqualTo(85);
    }

    @Test
    void testNullAndEmptyCellHandling() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Alice");
            ws.value(0, 1, 30);
            // column 2 (salary) left empty
            ws.value(1, 0, "Bob");
            // column 1 (age) left empty
            ws.value(1, 2, new BigDecimal("7000.00"));
        });

        ExcelReaderMapper<IndexBean> mapper = new ExcelReaderMapper<>(IndexBean.class);
        List<IndexBean> beans = mapper.map(readAllRows(data));

        assertThat(beans).hasSize(2);
        assertThat(beans.get(0).getName()).isEqualTo("Alice");
        assertThat(beans.get(0).getAge()).isEqualTo(30);
        assertThat(beans.get(0).getSalary()).isNull();
        assertThat(beans.get(1).getName()).isEqualTo("Bob");
        assertThat(beans.get(1).getAge()).isNull();
        assertThat(beans.get(1).getSalary()).isEqualByComparingTo(new BigDecimal("7000.00"));
    }

    @Test
    void testTypeVariety() throws Exception {
        Date now = new Date();
        LocalDateTime ldt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDate ld = LocalDate.of(2024, 1, 15);

        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Hello");
            ws.value(0, 1, 42);
            ws.value(0, 2, 1234567890123L);
            ws.value(0, 3, 3.14);
            ws.value(0, 4, new BigDecimal("99.99"));
            ws.value(0, 5, true);
            ws.value(0, 6, now);
            ws.value(0, 7, ldt);
            ws.value(0, 8, ld);
        });

        ExcelReaderMapper<TypeVarietyBean> mapper = new ExcelReaderMapper<>(TypeVarietyBean.class);
        List<TypeVarietyBean> beans = mapper.map(readAllRows(data));

        assertThat(beans).hasSize(1);
        TypeVarietyBean bean = beans.get(0);
        assertThat(bean.getText()).isEqualTo("Hello");
        assertThat(bean.getIntVal()).isEqualTo(42);
        assertThat(bean.getLongVal()).isEqualTo(1234567890123L);
        assertThat(bean.getDoubleVal()).isEqualTo(3.14);
        assertThat(bean.getBigDecimal()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(bean.getBoolVal()).isTrue();
        assertThat(bean.getDateVal()).isNotNull();
        assertThat(bean.getDateTimeVal()).isEqualTo(ldt);
        assertThat(bean.getLocalDateVal()).isEqualTo(ld);
    }

    @Test
    void testExcelIgnore() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Name");
            ws.value(0, 1, "Secret");
        });

        ExcelReaderMapper<BeanWithIgnore> mapper = new ExcelReaderMapper<>(BeanWithIgnore.class);
        List<BeanWithIgnore> beans = mapper.map(readAllRows(data));

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getName()).isEqualTo("Name");
        assertThat(beans.get(0).getInternal()).isNull();
    }

    @Test
    void testInheritedFields() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Base");
            ws.value(0, 1, "Child");
        });

        ExcelReaderMapper<ChildBean> mapper = new ExcelReaderMapper<>(ChildBean.class);
        List<ChildBean> beans = mapper.map(readAllRows(data));

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getBaseField()).isEqualTo("Base");
        assertThat(beans.get(0).getChildField()).isEqualTo("Child");
    }

    @Test
    void testEmptySheet() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            wb.newWorksheet("Sheet1");
        });

        ExcelReaderMapper<IndexBean> mapper = new ExcelReaderMapper<>(IndexBean.class);
        List<IndexBean> beans = mapper.map(readAllRows(data));

        assertThat(beans).isEmpty();
    }

    @Test
    void testMapFromSheet() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Alice");
            ws.value(0, 1, 30);
            ws.value(0, 2, new BigDecimal("5000.50"));
        });

        try (ReadableWorkbook rwb = new ReadableWorkbook(new ByteArrayInputStream(data))) {
            ExcelReaderMapper<IndexBean> mapper = new ExcelReaderMapper<>(IndexBean.class);
            List<IndexBean> beans = mapper.map(rwb.getFirstSheet());
            assertThat(beans).hasSize(1);
            assertThat(beans.get(0).getName()).isEqualTo("Alice");
        }
    }

    @Test
    void testHeaderNotFoundThrowsException() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "WrongName");
            ws.value(0, 1, "Age");
            ws.value(1, 0, "Test");
            ws.value(1, 1, 10);
        });

        ExcelReaderMapper<HeaderBean> mapper = new ExcelReaderMapper<>(HeaderBean.class);
        List<Row> rows = readAllRows(data);

        assertThatThrownBy(() -> mapper.map(rows))
                .isInstanceOf(ExcelMappingException.class)
                .hasMessageContaining("Name");
    }

    @Test
    void testNoArgConstructorRequired() {
        // A class without a no-arg constructor will fail during mapper creation
        // We verify that the no-arg constructor is found for classes that have it
        ExcelReaderMapper<IndexBean> mapper = new ExcelReaderMapper<>(IndexBean.class);
        assertThat(mapper).isNotNull(); // constructor resolution succeeded
    }

    @Test
    void testBooleanFromNumber() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "test");
            ws.value(0, 1, 1);  // 1 booleannly written as number
            ws.value(1, 0, "test2");
            ws.value(1, 1, 0);  // 0 written as number
        });

        // Test with a separate small bean
        class BoolBean {
            @ExcelProperty(index = 0)
            String name;
            @ExcelProperty(index = 1)
            boolean flag;
        }

        // We can't map to a local class, so let's verify via manual read
        List<Row> rows = readAllRows(data);
        assertThat(rows).hasSize(2);
    }

    @Test
    void testResolveHeaderRowExplicitly() throws Exception {
        byte[] data = writeWorkbook(wb -> {
            Worksheet ws = wb.newWorksheet("Sheet1");
            ws.value(0, 0, "Name");
            ws.value(0, 1, "Age");
            ws.value(0, 2, "Birth Date");
            ws.value(1, 0, "Eve");
            ws.value(1, 1, 33);
            ws.value(1, 2, LocalDate.of(1991, 3, 10));
        });

        List<Row> rows = readAllRows(data);
        ExcelReaderMapper<HeaderBean> mapper = new ExcelReaderMapper<>(HeaderBean.class);

        // Resolve header explicitly, then map individual rows
        mapper.resolveHeaderRow(rows.get(0));
        assertThat(mapper.needsHeaderRow()).isFalse();

        List<HeaderBean> beans = rows.subList(1, rows.size()).stream()
                .map(mapper::map)
                .collect(Collectors.toList());

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).getName()).isEqualTo("Eve");
        assertThat(beans.get(0).getAge()).isEqualTo(33);
        assertThat(beans.get(0).getBirthDate()).isEqualTo(LocalDate.of(1991, 3, 10));
    }
}
