package com.github.balloonupdate.mcpatch.client.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BytesUtils {
    private static final int kb = 1024;
    private static final int mb = 1024 * 1024;
    private static final int gb = 1024 * 1024 * 1024;

    /**
     * 字节转换为 kb, mb, gb 等单位
     * @param bytes 字节数
     * @return 转换后的带有单位的字符串
     */
    public static String convertBytes(long bytes) {
        String b = "B";
        String kb = "KB";
        String mb = "MB";
        String gb = "GB";

        if (bytes < 1024) {
            return bytes + " " + b;
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f", (bytes / 1024f)) + " " + kb;
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f", (bytes / 1024 / 1024f)) + " " + mb;
        } else {
            return String.format("%.2f", (bytes / 1024 / 1024 / 1024f)) + " " + gb;
        }
    }

    /**
     * 拆分较长的字符串到多行里
     *
     * @param str        要拆分的字符串
     * @param lineLength 每行的长度
     * @param newline    换行符，默认为 "\n"
     * @return 拆分并添加换行符后的字符串
     */
    public static String stringBreak(String str, int lineLength, String newline) {
        ArrayList<String> lines = new ArrayList<>();

        int lineCount = str.length() / lineLength;
        int remains = str.length() % lineLength;

        // 按指定长度拆分字符串到 lines 列表中
        for (int i = 0; i < lineCount; i++) {
            lines.add(str.substring(lineLength * i, lineLength * (i + 1)));
        }

        // 如果有剩余字符，将剩余部分添加到 lines 列表
        if (remains > 0) {
            lines.add(str.substring(lineLength * lineCount));
        }

        // 使用 StringBuilder 构建最终结果字符串
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            result.append(lines.get(i));

            // 除了最后一行，每行后面添加换行符
            if (i < lines.size() - 1) {
                result.append(newline);
            }
        }

        return result.toString();
    }

    /**
     * 根据文件大小选择合适的缓冲区大小
     *
     * @param size 文件大小
     * @return 缓冲区大小
     */
    public static int chooseBufferSize(long size) {
        if (size < 1 * mb) {
            return 16 * kb;
        } else if (size < 2 * mb) {
            return 32 * kb;
        } else if (size < 4 * mb) {
            return 64 * kb;
        } else if (size < 8 * mb) {
            return 256 * kb;
        } else if (size < 16 * mb) {
            return 512 * kb;
        } else if (size < 32 * mb) {
            return 1 * mb;
        } else if (size < 64 * mb) {
            return 2 * mb;
        } else if (size < 128 * mb) {
            return 4 * mb;
        } else if (size < 256 * mb) {
            return 8 * mb;
        } else if (size < 512 * mb) {
            return 16 * mb;
        } else if (size < 1 * gb) {
            return 32 * mb;
        } else {
            return 64 * mb;
        }
    }

    /**
     * 将 long 转换成 8 字节小端 bytes
     */
    public static byte[] longToBytesLE(long value) {
        byte[] bytes = new byte[8];

        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }

        return bytes;
    }

    /**
     * 将 8 字节小端 bytes 转换回 long
     */
    public static long bytesLeToLong(byte[] bytes) {
        if (bytes.length != 8) {
            throw new IllegalArgumentException("Input byte array must be 8 bytes long.");
        }

        long result = 0;

        for (int i = 7; i >= 0; i--) {
            result <<= 8;
            result |= (bytes[i] & 0xFF);
        }

        return result;
    }

    /**
     * 从流中不断读取数据，直到填满buf或者遇到异常，否则一直读取
     */
    public static void readAll(byte[] buf, InputStream input) throws IOException {
        int offset = 0;

        while (offset < buf.length) {
            int len = buf.length - offset;

            RuntimeAssert.isTrue(offset >= 0);
            RuntimeAssert.isTrue(len >= 0);
            RuntimeAssert.isTrue(len <= buf.length - offset);

            int received = input.read(buf, offset, len);

            if (received == -1)
                throw new IOException("The io reached the end");

            offset += received;
        }
    }

    /**
     * 将 bytes 转换为 [0, 16, 32]这样的格式
     */
    public static String bytesToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];

            String hex = String.format("%02X", b & 0xFF);

            sb.append(hex);

            if (i != bytes.length - 1)
                sb.append(", ");
        }
        // 将列表转换为字符串形式
        return "[" + sb + "]";
    }

    /**
     * 从流里读取出字符串
     */
    public static String readIntoString(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int length;

        while ((length = input.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }

        return result.toString(StandardCharsets.UTF_8.name());
    }
}
