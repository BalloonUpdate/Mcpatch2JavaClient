package com.github.balloonupdate.mcpatch.client.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedList;

/**
 * 代表一个版本的元数据
 */
public class VersionMeta {
    /**
     * 版本号，也叫版本标签
     */
    public String label;

    /**
     * 这个版本的更新记录
     */
    public String logs;

    /**
     * 文件变动列表
     */
    public LinkedList<FileChange> changes;

    /**
     * 从 JSON 里解析元数据
     * @param json
     */
    public VersionMeta(JSONObject json) {
        label = json.getString("label");
        logs = json.getString("logs");

        changes = new LinkedList<>();

        JSONArray array = json.getJSONArray("changes");

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);

            FileChange op = parseFileChange(obj);

            // 也有可能会解析失败
            if (op == null)
                continue;

            changes.add(op);
        }
    }

    /**
     * 解析单个文件变动操作
     */
    static FileChange parseFileChange(JSONObject json) {
        String operation = json.getString("operation");

        switch (operation) {
            case "create-directory": {
                String path = json.getString("path");

                FileChange.CreateFolder op = new FileChange.CreateFolder();
                op.path = path;

                return op;
            }

            case "update-file": {
                String path = json.getString("path");
                String hash = json.getString("hash");
                long len = json.getLong("len");
                long modified = json.getLong("modified");
                long offset = json.getLong("offset");

                FileChange.UpdateFile op = new FileChange.UpdateFile();
                op.path = path;
                op.hash = hash;
                op.len = len;
                op.modified = modified;
                op.offset = offset;

                return op;
            }

            case "delete-directory": {
                String path = json.getString("path");

                FileChange.DeleteFolder op = new FileChange.DeleteFolder();
                op.path = path;

                return op;
            }

            case "delete-file": {
                String path = json.getString("path");

                FileChange.DeleteFile op = new FileChange.DeleteFile();
                op.path = path;

                return op;
            }

            case "move-file": {
                String from = json.getString("from");
                String to = json.getString("to");

                FileChange.MoveFile op = new FileChange.MoveFile();
                op.from = from;
                op.to = to;

                return op;
            }

            default:
                return null;
        }
    }
}
