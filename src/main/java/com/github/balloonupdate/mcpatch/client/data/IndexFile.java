package com.github.balloonupdate.mcpatch.client.data;

import com.github.balloonupdate.mcpatch.client.logging.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表一个索引文件
 */
public class IndexFile {
    public ArrayList<VersionIndex> versions;

    public IndexFile() {
        versions = new ArrayList<>();
    }

    public static IndexFile loadFromJson(String json) {
        IndexFile indexFile = new IndexFile();

        JSONArray array = new JSONArray(json);

        for (int i = 0; i < array.length(); i++) {
            JSONObject element = array.getJSONObject(i);

            indexFile.versions.add(new VersionIndex(element));
        }

        return indexFile;
    }

    public List<VersionIndex> calculateMissingVersions(String currentVersion) {
        List<VersionIndex> missingVersions = new ArrayList<>();

        int index = -1;

        for (int i = 0; i < versions.size(); i++) {
            VersionIndex ver = versions.get(i);

            if (ver.label.equals(currentVersion)) {
                index = i;
                break;
            }
        }

        for (int i = index + 1; i < versions.size(); i++) {
            missingVersions.add(versions.get(i));
        }

        return missingVersions;
    }

    public boolean contains(String label) {
        return versions.stream().anyMatch(v -> v.label.equals(label));
    }

    public int len() {
        return versions.size();
    }

    public VersionIndex get(int index) {
        return versions.get(index);
    }
}
