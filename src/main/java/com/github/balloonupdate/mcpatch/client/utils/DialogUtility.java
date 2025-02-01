package com.github.balloonupdate.mcpatch.client.utils;

import javax.swing.*;

/**
 * 对话框实用类
 */
public class DialogUtility {
    public static boolean confirm(String title, String content) {
        return JOptionPane.showConfirmDialog(null, content, title, JOptionPane.YES_NO_OPTION) == 0;
    }

    public static  void error(String title, String content) {
        JOptionPane.showMessageDialog(null, content, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void info(String title, String content) {
        JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE);
    }
}
