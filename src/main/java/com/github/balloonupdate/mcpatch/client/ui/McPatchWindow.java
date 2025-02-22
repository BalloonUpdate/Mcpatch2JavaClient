package com.github.balloonupdate.mcpatch.client.ui;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 更新主窗口
 */
public class McPatchWindow {
    int width;
    int height;

    JFrame window;
    JLabel label;
    JLabel labelSecondary;
    JProgressBar progressBar;

    public OnWindowClosing onWindowClosing;

    public McPatchWindow(int width, int height) {
        this.width = width;
        this.height = height;

        window = new JFrame();

        label = new JLabel("空标签空标签空标签空标签空标签空标签空标签空标签空标签");
        label.setBounds(45, 15, 295, 20);
        label.setHorizontalAlignment(JLabel.CENTER);
        window.getContentPane().add(label);

        labelSecondary = new JLabel("空标签空标签空标签空标签空标签空标签空标签空标签空标签");
        labelSecondary.setBounds(45, 40, 295, 20);
        labelSecondary.setHorizontalAlignment(JLabel.CENTER);
        window.getContentPane().add(labelSecondary);

        progressBar = new JProgressBar(0, 1000);
        progressBar.setBounds(40, 80, 300, 40);
        progressBar.setStringPainted(true);
        window.getContentPane().add(progressBar);

        window.setUndecorated(false);
        window.getContentPane().setLayout(null);
        window.setVisible(false);
        window.setSize(width, height);
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.setLocationRelativeTo(null);
        window.setResizable(false);
//        window.isAlwaysOnTop = true;

        McPatchWindow that = this;

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (onWindowClosing != null)
                    onWindowClosing.run(that);
                else
                    destroy();
            }
        });
    }

    public McPatchWindow() {
        this(400, 180);
    }

    // 标题栏文字
    public void setTitleText(String value) {
        window.setTitle(value);
    }

    // 显示窗口
    public void show() {
        window.setVisible(true);
    }

    // 隐藏窗口
    public void hide() {
        window.setVisible(false);
    }

    // 销毁窗口
    public void destroy() {
        window.dispose();
    }

    // 进度条上的文字
    public void setProgressBarText(String value) {
        progressBar.setString(value);
    }

    // 进度条的值

    public void setProgressBarValue(int value) {
        progressBar.setValue(value);
    }

    // 标签上的文字
    public void setLabelText(String value) {
        label.setText(value);
    }

    // 副签上的文字
    public void setLabelSecondaryText(String value) {
        labelSecondary.setText(value);
    }

    @FunctionalInterface
    public interface OnWindowClosing {
        void run(McPatchWindow window);
    }

    // 开发时调试用
    public static void main(String[] args) {
        new McPatchWindow().show();
    }
}
