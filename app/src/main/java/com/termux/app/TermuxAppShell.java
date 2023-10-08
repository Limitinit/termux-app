package com.termux.app;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;

import com.termux.shared.R;
import com.termux.shared.data.DataUtils;
import com.termux.shared.errors.Errno;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.shell.StreamGobbler;
import com.termux.shared.shell.command.environment.IShellEnvironment;
import com.termux.shared.shell.command.result.ResultData;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A class that maintains info for background app shells run with {@link Runtime#exec(String[], String[], File)}.
 * It also provides a way to link each {@link Process} with the {@link TermuxExecutionCommand}
 * that started it. The shell is run in the app user context.
 */
public final class TermuxAppShell {

    private static final String LOG_TAG = "AppShell";

    private final Process mProcess;
    private final TermuxExecutionCommand mExecutionCommand;
    private final TermuxService mAppShellClient;

    private TermuxAppShell(@NonNull final Process process,
                           @NonNull final TermuxExecutionCommand executionCommand,
                           final TermuxService appShellClient) {
        this.mProcess = process;
        this.mExecutionCommand = executionCommand;
        this.mAppShellClient = appShellClient;
    }

    public static TermuxAppShell execute(@NonNull final TermuxService termuxService,
                                         @NonNull TermuxExecutionCommand executionCommand,
                                         @NonNull final IShellEnvironment shellEnvironmentClient) {
        if (executionCommand.executable == null || executionCommand.executable.isEmpty()) {
            //executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), currentPackageContext.getString(R.string.error_executable_unset, executionCommand.getCommandIdAndLabelLogString()));
            TermuxAppShell.processAppShellResult(null, executionCommand);
            return null;
        }

        if (executionCommand.workingDirectory == null || executionCommand.workingDirectory.isEmpty())
            executionCommand.workingDirectory = TermuxConstants.HOME_PATH;
        if (executionCommand.workingDirectory.isEmpty())
            executionCommand.workingDirectory = "/";

        // Transform executable path to shell/session name, e.g. "/bin/do-something.sh" => "do-something.sh".
        String executableBasename = ShellUtils.getExecutableBasename(executionCommand.executable);

        if (executionCommand.shellName == null)
            executionCommand.shellName = executableBasename;

        if (executionCommand.commandLabel == null)
            executionCommand.commandLabel = executableBasename;

        // Setup command args
        final String[] commandArray = shellEnvironmentClient.setupShellCommandArguments(executionCommand.executable, executionCommand.arguments);

        String[] environmentArray = TermuxShellUtils.setupEnvironment(executionCommand.isFailsafe);

        if (!executionCommand.setState(TermuxExecutionCommand.ExecutionState.EXECUTING)) {
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), termuxService.getString(R.string.error_failed_to_execute_app_shell_command, executionCommand.getCommandIdAndLabelLogString()));
            TermuxAppShell.processAppShellResult(null, executionCommand);
            return null;
        }

        // Exec the process
        final Process process;
        try {
            process = Runtime.getRuntime().exec(commandArray, environmentArray, new File(executionCommand.workingDirectory));
        } catch (IOException e) {
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), termuxService.getString(R.string.error_failed_to_execute_app_shell_command, executionCommand.getCommandIdAndLabelLogString()), e);
            TermuxAppShell.processAppShellResult(null, executionCommand);
            return null;
        }

        final TermuxAppShell appShell = new TermuxAppShell(process, executionCommand, termuxService);
        new Thread() {
            @Override
            public void run() {
                try {
                    appShell.executeInner(termuxService);
                } catch (IllegalThreadStateException | InterruptedException e) {
                    Log.e(LOG_TAG, "Error: " + e);
                }
            }
        }.start();

        return appShell;
    }

    public static void putToEnvIfInSystemEnv(@NonNull Map<String, String> environment,
                                             @NonNull String name) {
        String value = System.getenv(name);
        if (value != null) {
            environment.put(name, value);
        }
    }


    /**
     * Sets up stdout and stderr readers for the {@link #mProcess} and waits for the process to end.
     *
     * If the processes finishes, then sets {@link ResultData#stdout}, {@link ResultData#stderr}
     * and {@link ResultData#exitCode} for the {@link #mExecutionCommand} of the {@code appShell}
     * and then calls {@link #processAppShellResult(TermuxAppShell, ExecutionCommand) to process the result}.
     *
     * @param context The {@link Context} for operations.
     */
    private void executeInner(@NonNull final Context context) throws IllegalThreadStateException, InterruptedException {
        mExecutionCommand.mPid = ShellUtils.getPid(mProcess);

        Logger.logDebug(LOG_TAG, "Running \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" AppShell with pid " + mExecutionCommand.mPid);

        mExecutionCommand.resultData.exitCode = null;

        // setup stdin, and stdout and stderr gobblers
        DataOutputStream STDIN = new DataOutputStream(mProcess.getOutputStream());
        StreamGobbler STDOUT = new StreamGobbler(mExecutionCommand.mPid + "-stdout", mProcess.getInputStream(), mExecutionCommand.resultData.stdout, mExecutionCommand.backgroundCustomLogLevel);
        StreamGobbler STDERR = new StreamGobbler(mExecutionCommand.mPid + "-stderr", mProcess.getErrorStream(), mExecutionCommand.resultData.stderr, mExecutionCommand.backgroundCustomLogLevel);

        // start gobbling
        STDOUT.start();
        STDERR.start();

        if (!DataUtils.isNullOrEmpty(mExecutionCommand.stdin)) {
            try {
                STDIN.write((mExecutionCommand.stdin + "\n").getBytes(StandardCharsets.UTF_8));
                STDIN.flush();
                STDIN.close();
                //STDIN.write("exit\n".getBytes(StandardCharsets.UTF_8));
                //STDIN.flush();
            } catch(IOException e) {
                if (e.getMessage() != null && (e.getMessage().contains("EPIPE") || e.getMessage().contains("Stream closed"))) {
                    // Method most horrid to catch broken pipe, in which case we
                    // do nothing. The command is not a shell, the shell closed
                    // STDIN, the script already contained the exit command, etc.
                    // these cases we want the output instead of returning null.
                } else {
                    // other issues we don't know how to handle, leads to
                    // returning null
                    mExecutionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), context.getString(R.string.error_exception_received_while_executing_app_shell_command, mExecutionCommand.getCommandIdAndLabelLogString(), e.getMessage()), e);
                    mExecutionCommand.resultData.exitCode = 1;
                    TermuxAppShell.processAppShellResult(this, null);
                    kill();
                    return;
                }
            }
        }

        // wait for our process to finish, while we gobble away in the background
        int exitCode = mProcess.waitFor();

        // make sure our threads are done gobbling
        // and the process is destroyed - while the latter shouldn't be
        // needed in theory, and may even produce warnings, in "normal" Java
        // they are required for guaranteed cleanup of resources, so lets be
        // safe and do this on Android as well
        try {
            STDIN.close();
        } catch (IOException e) {
            // might be closed already
        }
        STDOUT.join();
        STDERR.join();
        mProcess.destroy();

        // Process result
        if (exitCode == 0)
            Logger.logDebug(LOG_TAG, "The \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" AppShell with pid " + mExecutionCommand.mPid + " exited normally");
        else
            Logger.logDebug(LOG_TAG, "The \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" AppShell with pid " + mExecutionCommand.mPid + " exited with code: " + exitCode);

        // If the execution command has already failed, like SIGKILL was sent, then don't continue
        if (mExecutionCommand.isStateFailed()) {
            Logger.logDebug(LOG_TAG, "Ignoring setting \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" AppShell state to ExecutionState.EXECUTED and processing results since it has already failed");
            return;
        }

        mExecutionCommand.resultData.exitCode = exitCode;

        if (!mExecutionCommand.setState(TermuxExecutionCommand.ExecutionState.EXECUTED))
            return;

        TermuxAppShell.processAppShellResult(this, null);
    }

    public void killIfExecuting(@NonNull final Context context, boolean processResult) {
        // If execution command has already finished executing, then no need to process results or send SIGKILL
        if (mExecutionCommand.hasExecuted()) {
            Logger.logDebug(LOG_TAG, "Ignoring sending SIGKILL to \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" AppShell since it has already finished executing");
            return;
        }

        Logger.logDebug(LOG_TAG, "Send SIGKILL to \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" AppShell");

        if (mExecutionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), context.getString(R.string.error_sending_sigkill_to_process))) {
            if (processResult) {
                mExecutionCommand.resultData.exitCode = 137; // SIGKILL
                TermuxAppShell.processAppShellResult(this, null);
            }
        }

        if (mExecutionCommand.isExecuting()) {
            kill();
        }
    }

    /**
     * Kill this {@link TermuxAppShell} by sending a {@link OsConstants#SIGILL} to its {@link #mProcess}.
     */
    public void kill() {
        int pid = ShellUtils.getPid(mProcess);
        try {
            // Send SIGKILL to process
            Os.kill(pid, OsConstants.SIGKILL);
        } catch (ErrnoException e) {
            Logger.logWarn(LOG_TAG, "Failed to send SIGKILL to \"" + mExecutionCommand.getCommandIdAndLabelLogString() + "\" AppShell with pid " + pid + ": " + e.getMessage());
        }
    }

    /**
     * Process the results of {@link TermuxAppShell} or {@link ExecutionCommand}.
     *
     * Only one of {@code appShell} and {@code executionCommand} must be set.
     *
     * If the {@code appShell} and its {@link #mAppShellClient} are not {@code null},
     * then the {@link AppShellClient#onAppShellExited(TermuxAppShell)} callback will be called.
     *
     * @param appShell The {@link TermuxAppShell}, which should be set if
     *                  {@link #execute(Context, ExecutionCommand, AppShellClient, IShellEnvironment, HashMap, boolean)}
     *                   successfully started the process.
     * @param executionCommand The {@link ExecutionCommand}, which should be set if
     *                          {@link #execute(Context, ExecutionCommand, AppShellClient, IShellEnvironment, HashMap, boolean)}
     *                          failed to start the process.
     */
    private static void processAppShellResult(final TermuxAppShell appShell, TermuxExecutionCommand executionCommand) {
        if (executionCommand.shouldNotProcessResults()) {
            Logger.logDebug(LOG_TAG, "Ignoring duplicate call to process \"" + executionCommand.getCommandIdAndLabelLogString() + "\" AppShell result");
            return;
        }

        Logger.logDebug(LOG_TAG, "Processing \"" + executionCommand.getCommandIdAndLabelLogString() + "\" AppShell result");

        if (appShell != null && appShell.mAppShellClient != null) {
            appShell.mAppShellClient.onAppShellExited(appShell);
        } else {
            // If a callback is not set and execution command didn't fail, then we set success state now
            // Otherwise, the callback host can set it himself when its done with the appShell
            if (!executionCommand.isStateFailed()) {
                executionCommand.setState(TermuxExecutionCommand.ExecutionState.SUCCESS);
            }
        }
    }

    public Process getProcess() {
        return mProcess;
    }

    public TermuxExecutionCommand getExecutionCommand() {
        return mExecutionCommand;
    }

}
