package com.github.balloonupdate.mcpatch.client.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 文件 hash 计算类，所有计算文件哈希值时都会调用此函数，可以在此函数中替换任意哈希算法
 */
public class HashUtility {
    static Crc64_XZ crc64 = new Crc64_XZ();
    static Crc16_IBM_SDLC crc16 = new Crc16_IBM_SDLC();

    /**
     * 计算一个文件的校验值（此函数不是线程安全的，多线程环境下需要注意）
     */
    public static String calculateHash(Path file) throws IOException {
        crc64.reset();
        crc16.reset();

        crc64.update(file);
        crc16.update(file);

        long a = crc64.getValue();
        long b = crc16.getValue();

        String crc64 = String.format("%016x", a);
        String crc16 = String.format("%04x", b);

        return crc64 + "_" + crc16;
    }
}

class Crc64_XZ {
    private final long polynomial = 0x42f0e1eba9ea3693L;
    private final long initialValue = 0xffffffffffffffffL;
    private final long finalXorValue = 0xffffffffffffffffL;
    private final boolean reflectInput = true;
    private final boolean reflectOutput = true;

    private long crc = initialValue;

    byte[] buf = new byte[128 * 1024];

    public void reset() {
        crc = initialValue;

        Arrays.fill(buf, (byte) 0);
    }

    public void update(Path file) throws IOException {
        try (BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(file))) {
            int read;

            while ((read = stream.read(buf)) != -1)
                update(buf, 0, read);
        }
    }

    public void update(byte[] data, int offset, int len) {
        for (int x = offset; x < len; x++) {
            byte b = data[x];
            long value = b & 0xFF;
            if (reflectInput) {
                value = reflect(value, 8);
            }
            crc ^= (value << 56);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000000000000000L) != 0) {
                    crc = (crc << 1) ^ polynomial;
                } else {
                    crc <<= 1;
                }
            }
        }
    }

    public long getValue() {
        long result = crc;
        if (reflectOutput) {
            result = reflect(result, 64);
        }
        return result ^ finalXorValue;
    }

    private long reflect(long value, int bits) {
        long reflected = 0;
        for (int i = 0; i < bits; i++) {
            if ((value & (1L << i)) != 0) {
                reflected |= (1L << (bits - 1 - i));
            }
        }
        return reflected;
    }
}

class Crc16_IBM_SDLC {
    private final int polynomial = 0x1021;
    private final int initialValue = 0xffff;
    private final int finalXorValue = 0xffff;
    private final boolean reflectInput = true;
    private final boolean reflectOutput = true;

    private int crc = initialValue;

    byte[] buf = new byte[128 * 1024];

    public void reset() {
        crc = initialValue;

        Arrays.fill(buf, (byte) 0);
    }

    public void update(Path file) throws IOException {
        try (BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(file))) {
            int read;

            while ((read = stream.read(buf)) != -1)
                update(buf, 0, read);
        }
    }

    public void update(byte[] data, int offset, int len) {
        for (int x = offset; x < len; x++) {
            byte b = data[x];
            int value = b & 0xFF;
            if (reflectInput) {
                value = reflect(value, 8);
            }
            crc ^= (value << 8);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ polynomial;
                } else {
                    crc <<= 1;
                }
            }
        }
    }

    public int getValue() {
        int result = crc;
        if (reflectOutput) {
            result = reflect(result, 16);
        }
        return (result ^ finalXorValue) & 0xFFFF;
    }

    private int reflect(int value, int bits) {
        int reflected = 0;
        for (int i = 0; i < bits; i++) {
            if ((value & (1 << i)) != 0) {
                reflected |= (1 << (bits - 1 - i));
            }
        }
        return reflected;
    }
}