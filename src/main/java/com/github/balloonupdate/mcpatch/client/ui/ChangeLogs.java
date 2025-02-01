package com.github.balloonupdate.mcpatch.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 展示更新记录的窗口
 */
public class ChangeLogs {
    static String testText = "Java平台由Java虚拟机(Java Virtual Machine)和Java 应用编程接口(Application Programming Interface、简称API)构成。\n" +
            "Java 应用编程接口为Java应用提供了一个独立于操作系统的标准接口，可分为基本部分和扩展部分。\n" +
            "在硬件或操作系统平台上安装一个Java平台之后，Java应用程序就可运行。Java平台已经嵌入了几乎所有的操作系统。\n" +
            "这样Java程序可以只编译一次，就可以在各种系统中运行。Java应用编程接口已经从1.1x版发展到1.2版。常用的Java平台基于Java1.8，最近版本为Java19。";

    JFrame window = new JFrame();
    JPanel panel = new JPanel();
    JPanel panel2 = new JPanel();
    JTextArea text = new JTextArea();
    JButton closeButton = new JButton("关闭");

    Thread threadLock = new Thread(() -> {
        try {
            while (true)
                Thread.sleep(1000);
        } catch (InterruptedException e) {  }
    });

    int autoCloseDelay = 0;

    Thread autoCloseThread = new Thread(() -> {
        try {
            Thread.sleep(autoCloseDelay);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        close();
    });

    public ChangeLogs() {
        window.setUndecorated(false);
        window.setContentPane(panel);
        window.setVisible(true);
        window.setSize(400, 300);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setLocationRelativeTo(null);
        window.setResizable(true);

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                threadLock.interrupt();
            }
        });

        // esc 关闭
        window.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                    close();
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });

        panel.setBorder(new EmptyBorder(4, 4, 4, 4));
        panel.setLayout(new BorderLayout(8, 4));
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        panel.add(panel2, BorderLayout.SOUTH);

        panel2.setLayout(new BorderLayout(0, 0));
        panel2.add(closeButton, BorderLayout.EAST);

        text.setEditable(false);
        text.setText(testText);
        text.setLineWrap(true);
        closeButton.addActionListener(e -> close());

        threadLock.setDaemon(true);
        threadLock.start();

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            this.window.repaint();
        });

        t.setDaemon(true);
        t.start();
    }

    public void setAutoClose(int time)
    {
        autoCloseDelay = time;
        autoCloseThread.setDaemon(true);
        autoCloseThread.start();
    }

    public void close()
    {
        window.dispose();
    }

    public void waitForClose()
    {
        try {
            threadLock.join();
        } catch (InterruptedException ignored) { }
    }

    /**
     * 列表文字
     */
    public void setContentText(String value) {
        text.setText(value);
    }

    /**
     * 标题栏文字
     */
    public void setTitleText(String value) {
        window.setTitle(value);
    }

    // 开发时调试用
    public static void main(String[] args) {
        ChangeLogs win = new ChangeLogs();

        win.setAutoClose(1000);
    }

}
