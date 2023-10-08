package com.termux.shared.shell.am;

import android.Manifest;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.net.socket.local.ILocalSocketManager;
import com.termux.shared.net.socket.local.LocalSocketRunConfig;

import java.io.Serializable;

/**
 * Run config for {@link AmSocketServer}.
 */
public class AmSocketServerRunConfig extends LocalSocketRunConfig implements Serializable {

    /**
     * Create an new instance of {@link AmSocketServerRunConfig}.
     *
     * @param title The {@link #mTitle} value.
     * @param path The {@link #mPath} value.
     * @param localSocketManagerClient The {@link #mLocalSocketManagerClient} value.
     */
    public AmSocketServerRunConfig(@NonNull String title, @NonNull String path, @NonNull ILocalSocketManager localSocketManagerClient) {
        super(title, path, localSocketManagerClient);
    }

    /**
     * Get a log {@link String} for {@link AmSocketServerRunConfig}.
     *
     * @param config The {@link AmSocketServerRunConfig} to get info of.
     * @return Returns the log {@link String}.
     */
    @NonNull
    public static String getRunConfigLogString(final AmSocketServerRunConfig config) {
        if (config == null) return "null";
        return config.getLogString();
    }

    /** Get a log {@link String} for the {@link AmSocketServerRunConfig}. */
    @NonNull
    public String getLogString() {
        StringBuilder logString = new StringBuilder();
        logString.append(super.getLogString()).append("\n\n\n");

        return logString.toString();
    }

    /** Get a markdown {@link String} for the {@link AmSocketServerRunConfig}. */
    @NonNull
    public String getMarkdownString() {
        StringBuilder markdownString = new StringBuilder();
        markdownString.append(super.getMarkdownString()).append("\n\n\n");

        markdownString.append("## ").append("Am Command");

        return markdownString.toString();
    }



    @NonNull
    @Override
    public String toString() {
        return getLogString();
    }

}
