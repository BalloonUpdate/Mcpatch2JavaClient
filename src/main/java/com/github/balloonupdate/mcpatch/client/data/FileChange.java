package com.github.balloonupdate.mcpatch.client.data;

/**
 * 代表单个文件操作
 */
public interface FileChange {
    /**
     * 创建一个目录
     */
    class CreateFolder implements FileChange {
        /**
         * 要创建目录的路径
         */
        public String path;
    }

    /**
     * 新增新的文件或者更新现有文件
     */
    class UpdateFile implements FileChange {
        /**
         * 要更新的文件路径
         */
        public String path;

        /**
         * 文件校验值
         */
        public String hash;

        /**
         * 文件长度
         */
        public long len;

        /**
         * 文件的修改时间
         */
        public long modified;

        /**
         * 文件二进制数据在更新包中的偏移值
         */
        public long offset;
    }

    /**
     * 删除一个目录
     */
    class DeleteFolder implements FileChange {
        /**
         * 要删除的目录的路径
         */
        public String path;
    }

    /**
     * 删除一个文件
     */
    class DeleteFile implements FileChange {
        /**
         * 要删除的文件的路径
         */
        public String path;
    }

    /**
     * 移动一个文件
     */
    class MoveFile implements FileChange {
        /**
         * 文件从哪里来
         */
        public String from;

        /**
         * 文件到哪里去
         */
        public String to;
    }
}
