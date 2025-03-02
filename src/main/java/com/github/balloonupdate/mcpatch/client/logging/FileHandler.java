package com.github.balloonupdate.mcpatch.client.logging;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;

/**
 * 文件日志记录器，会把日志输出到日志文件里
 */
public class FileHandler implements LogHandler {
    LogLevel level;

    Path logFile;

    PrintWriter writer;

    SimpleDateFormat fmt;

    public FileHandler(LogLevel level, Path logFile) {
        this.level = level;
        this.logFile = logFile;
        fmt = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
    }

    @Override
    public LogLevel getFilterLevel() {
        return level;
    }

    @Override
    public void onStart() {
        try {
            Files.write(logFile, new byte[] {});

            writer = new PrintWriter(logFile.toFile(), StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onStop() {
        if (writer != null)
            writer.close();
    }

    @Override
    public void onMessage(Message message) {
        if (writer == null)
            return;


        String indentText = "";

        if (!message.indents.isEmpty())
            indentText = String.join(" ", message.indents) + " ";

        String appId = message.appIdentifier ? "Mcpatch" : "";
        String ts = fmt.format(System.currentTimeMillis());
        String level = message.level.name().toUpperCase();

        String prefix = String.format("%s[ %s %-5s ] %s", appId, ts, level, indentText);

        String text = prefix + message.content;

        text = text.replaceAll("\n", "\n" + prefix);

        writer.println(text);
        writer.flush();
    }
}
