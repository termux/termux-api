package com.termux.api.util;

import androidx.annotation.NonNull;

import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;

////
/// Utils for processes.
///
/// See also [android.os.Process].
////
public class ProcessUtils {

    ////
    /// Get the `starttime` value for the process with `pid` from the field 22 of `/proc/<pid>/stat` file.
    ///
    /// > The time the process started after system boot.
    /// > Since Linux 2.6, the value is expressed in clock ticks (divide by sysconf(_SC_CLK_TCK)).
    ///
    /// - https://manpages.debian.org/testing/manpages/proc_pid_stat.5.en.html
    ///
    /// @param logTag The log tag to use for logging.
    /// @param label The label of the process.
    /// @param pid The pid of the process.
    /// @param logErrorMessage If an error message should be logged if failed to read stat file.
    /// @return Returns the `starttime` value if found, otherwise `-1`.
    ////
    public static long getProcessStartTime(@NonNull String logTag, @NonNull String label, int pid, boolean logErrorMessage) {
        if (pid < 1) return -1;

        StringBuilder statContentBuilder = new StringBuilder();
        Error error = FileUtils.readTextFromFile(label + "process stat", "/proc/" + pid + "/stat",
                null, statContentBuilder, /* `ignoreNonExistentFile` */ true);
        if (error != null) {
            if (logErrorMessage) {
                Logger.logError(logTag, error.toString());
            }
            return -1;
        }

        // If file does not exist or is not accessible, then stat file content will be empty.
        if (statContentBuilder.length() < 1) {
            return -1;
        }

        String statContent = statContentBuilder.toString();
        if (statContent.isEmpty()) {
            return -1;
        }

        // Find last bracket `)` of field 2 for `executable` in the format `(executable)`.
        int lastBracketIndex = statContent.lastIndexOf(')');
        if (lastBracketIndex == -1 || lastBracketIndex + 1 >= statContent.length()) {
            return -1; // Malformed stat file.
        }

        // Parse fields starting right after the last `)`, and split them by whitespace.
        // Field 3 `state` begins immediately after `) `.
        // Field 22 is `starttime` (the 20th field after field 2, index 19 for zero-indexed array).
        String remainingFields = statContent.substring(lastBracketIndex + 1).trim();
        String[] fields = remainingFields.split("\\s+");

        int starttimeIndex = 19;
        if (fields.length <= starttimeIndex) {
            return -1; // Malformed stat file.
        }

        try {
            return Long.parseUnsignedLong(fields[starttimeIndex]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

}
