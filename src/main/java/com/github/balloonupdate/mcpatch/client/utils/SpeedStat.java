package com.github.balloonupdate.mcpatch.client.utils;

import java.util.ArrayDeque;
import java.util.Stack;

/**
 * 代表一个速度统计，用来计算下载文件时的网速
 */
public class SpeedStat {
    /**
     * 采样周期
     */
    long period;

    /**
     * 记录的数据帧，新的采样数据会放到队列开头
     */
    ArrayDeque<Sample> frames = new ArrayDeque<>();

    /**
     * 对象池
     */
    Stack<Sample> cache = new Stack<>();

    public SpeedStat(int samplingPeriod) {
        this.period = samplingPeriod;
    }

    // 获取格式化后的采样速度
    public String sampleSpeed2() {
        return BytesUtils.convertBytes(sampleSpeed());
    }

    // 获取采样速度
    public long sampleSpeed() {
        // 如果数据不够，直接返回0
        if (frames.size() <= 2) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long delta = now - frames.getFirst().time;

        // 调用太快了，也返回0
        if (delta < 0) {
            return 0;
        }

        // 计算总bytes
        long totalBytes = 0;

        for (Sample sample : frames) {
            totalBytes += sample.bytes;
        }

        // 重新计算时间跨度
        delta = frames.getFirst().time - frames.getLast().time;

        // 计算平均速度
        return totalBytes / delta * 1000;
    }

    // 增加了新的字节数
    public void feed(long bytes) {
        long now = System.currentTimeMillis();

        if (!frames.isEmpty()) {
            Sample front = frames.getFirst();

            // 如果调用速度太快，就将数据合并进最近的采样数据里
            if (front.time == now) {
                front.bytes += bytes;
                return;
            }
        }

        // 正常添加一个采样数据
        frames.addFirst(GetSample(bytes, now));

        // 清理多余数据
        int invalidStartsAt = -1;
        int index = 0;

        // 收集多余数据的范围
        for (Sample frame : frames) {
            long diff = now - frame.time;

            if (diff > period && invalidStartsAt == -1) {
                invalidStartsAt = index;
            }

            index += 1;
        }

        // 开始回收多余数据
        if (invalidStartsAt != -1 && index - invalidStartsAt > 1) {
            for (int i = invalidStartsAt; i < frames.size(); i++) {
                ReleaseSample(frames.removeLast());
            }
        }

        // 不能太多
        RuntimeAssert.isTrue(frames.size() < 100000);
    }

    Sample GetSample(long bytes, long time) {
        Sample sample = cache.empty() ? new Sample() : cache.pop();

        sample.bytes = bytes;
        sample.time = time;

        return sample;
    }

    void ReleaseSample(Sample sample) {
        cache.push(sample);
    }

    // 代表一个采样数据
    static class Sample {
        /**
         * 记录的字节数量
         */
        public long bytes;

        /**
         * 采样生成的时间
         */
        public long time;
    }
}

