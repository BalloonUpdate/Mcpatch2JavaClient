package com.github.balloonupdate.mcpatch.client.data;

/**
 * 代表更新过程中创建的文件移动信息
 */
public class TempMoveFile {
    /// 文件从哪里来
    public String from;

    /// 文件到哪里去
    public String to;

    public TempMoveFile(String from, String to) {
        this.from = from;
        this.to = to;
    }
}