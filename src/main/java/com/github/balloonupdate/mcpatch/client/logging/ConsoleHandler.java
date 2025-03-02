package com.github.balloonupdate.mcpatch.client.logging;

/**
 * Console 日志记录器，会把日志输出到控制台
 */
public class ConsoleHandler implements LogHandler {
    LogLevel level;

    public ConsoleHandler(LogLevel level) {
        this.level = level;
    }

    @Override
    public LogLevel getFilterLevel() {
        return level;
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onMessage(Message message) {
        String indentText = "";

        if (!message.indents.isEmpty())
            indentText = String.join(" ", message.indents) + " ";

        String appId = message.appIdentifier ? "Mcpatch" : "";
        String level = message.level.name().toUpperCase();

        String prefix = String.format("%s[ %-5s ] %s ", appId, level, indentText);

        String text = prefix + message.content;

        text = text.replaceAll("\n", "\n" + prefix);

        System.out.println(text);
    }
}
