package com.github.balloonupdate.mcpatch.client.data;

public class Range {
    public long start;
    public long end;

    public Range(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public long len() {
        return end - start;
    }

    public static Range Empty() {
        return new Range(0, 0);
    }
}
