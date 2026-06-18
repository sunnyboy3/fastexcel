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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工作簿输出目标的抽象——解耦"写到哪里"和"怎么写"。
 *
 * <p>内置工厂方法覆盖常见场景：
 * <pre>{@code
 * // 写入本地文件
 * WorkbookSink.toFile(new File("/path/to/output.xlsx"))
 *
 * // 写入本地路径
 * WorkbookSink.toPath(Path.of("/path/to/output.xlsx"))
 *
 * // 写入内存（测试用）
 * WorkbookSink.toByteArray()
 *
 * // 自定义——例如上传到 OSS/S3
 * WorkbookSink.of(() -> {
 *     PipedOutputStream pos = new PipedOutputStream();
 *     // 另起线程将 PipedInputStream 上传到云存储
 *     executor.submit(() -> ossClient.upload(new PipedInputStream(pos)));
 *     return pos;
 * })
 * }</pre>
 */
@FunctionalInterface
public interface WorkbookSink {

    /**
     * 打开一个输出流。调用方负责在写入完成后关闭该流。
     *
     * @return 就绪的输出流
     * @throws IOException 打开失败时抛出
     */
    OutputStream open() throws IOException;

    // ── 内置工厂方法 ──────────────────────────────────────────────

    /** 写入本地 {@link File}。 */
    static WorkbookSink toFile(File file) {
        return () -> new FileOutputStream(file);
    }

    /** 写入本地 {@link Path}。 */
    static WorkbookSink toPath(Path path) {
        return () -> Files.newOutputStream(path);
    }

    /** 写入内存字节数组（测试或小文件场景）。 */
    static WorkbookSink toByteArray() {
        return ByteArrayOutputStream::new;
    }

    /** 从已有的 {@link OutputStream} 包装（调用方管理流生命周期）。 */
    static WorkbookSink ofStream(OutputStream os) {
        return () -> os;
    }

    /** 从自定义 {@link OutputStream} 工厂创建。 */
    static WorkbookSink of(WorkbookSink sink) {
        return sink;
    }
}
