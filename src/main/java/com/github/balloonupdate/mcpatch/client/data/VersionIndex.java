package com.github.balloonupdate.mcpatch.client.data;

import org.json.JSONObject;

/**
 * 代表一个版本的索引信息。保存时会被序列化成一个Json对象
 * {@code
 *     "label": "1.2",
 *     "file": "1.2.tar",
 *     "offset": 7A9C,
 *     "length": 1000,
 *     "hash": "23B87EA52C893"
 * }
 */
public class VersionIndex {
    /**
     * 版本号
     */
    public String label;

    /**
     * 版本的数据存在哪个文件里
     */
    public String filename;

    /**
     * 元数据组的偏移值
     */
    public long offset;

    /**
     * 元数据组的长度
     */
    public long len;

    /**
     * 整个tar包文件的校验
     */
    public String hash;

    public VersionIndex(JSONObject json) {
        label = json.getString("label");
        filename = json.getString("filename");
        offset = json.getLong("offset");
        len = json.getInt("length");
        hash = json.getString("hash");
    }
}

