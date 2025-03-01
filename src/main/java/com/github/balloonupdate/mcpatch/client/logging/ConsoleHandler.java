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
        if (!message.newLine) {
            System.out.print(message.content);
            System.out.flush();
            return;
        }

        String prefix;
        String tags = "";

        if (!message.tags.isEmpty())
            tags = String.join("/", message.tags);

        prefix = String.format("[ %-5s ] %s ", level.toString().toUpperCase(), tags);

        String text = prefix + message.content.replaceAll("\n", "\n" + prefix);

        System.err.println(text);
    }
}
