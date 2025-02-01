package com.github.balloonupdate.mcpatch.client.data;

/**
 * 代表更新过程中创建的版本元数据，且多一个 filename 字段
 */
public class TempVersionMeta {
    /**
     * 更新包文件名
     */
    public String filename;

    /**
     * 版本元数据
     */
    public VersionMeta metadata;

    public TempVersionMeta(String filename, VersionMeta metadata) {
        this.filename = filename;
        this.metadata = metadata;
    }
}
