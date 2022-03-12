package com.termux.api.apis;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.io.IOException;

/**
 * API that enables playback of standard audio formats such as:
 * mp3, wav, flac, etc... using Android's default MediaPlayer
 */
public class MediaPlayerAPI {

    private static final String LOG_TAG = "MediaPlayerAPI";

    /**
     * Starts our MediaPlayerService
     */
    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        // Create intent for starting our player service and make sure
        // we retain all relevant info from this intent
        Intent playerService = new Intent(context, MediaPlayerService.class);
        playerService.setAction(intent.getAction());
        playerService.putExtras(intent.getExtras());

        context.startService(playerService);
    }

    /**
     * Converts time in seconds to a formatted time string: HH:MM:SS
     * Hours will not be included if it is 0
     */
    public static String getTimeString(int totalSeconds) {
        int hours = (totalSeconds / 3600);
        int mins = (totalSeconds % 3600) / 60;
        int secs = (totalSeconds % 60);

        String result = "";

        // only show hours if we have them
        if (hours > 0) {
            result += String.format("%02d:", hours);
        }
        result += String.format("%02d:%02d", mins, secs);
        return result;
    }


    /**
     * All media functionality exists in this background service
     */
    public static class MediaPlayerService extends Service implements MediaPlayer.OnErrorListener,
            MediaPlayer.OnCompletionListener {

        protected static MediaPlayer mediaPlayer;

        // do we currently have a track to play?
        protected static boolean hasTrack;

        protected static String trackName;

        private static final String LOG_TAG = "MediaPlayerService";

        /**
         * Returns our MediaPlayer instance and ensures it has all the necessary callbacks
         */
        protected MediaPlayer getMediaPlayer() {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setOnCompletionListener(this);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                mediaPlayer.setVolume(1.0f, 1.0f);
            }
            return mediaPlayer;
        }

        /**
         * What we received from TermuxApiReceiver but now within this service
         */
        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

            String command = intent.getAction();
            MediaPlayer player = getMediaPlayer();
            Context context = getApplicationContext();

            // get command handler and display result
            MediaCommandHandler handler = getMediaCommandHandler(command);
            MediaCommandResult result = handler.handle(player, context, intent);
            postMediaCommandResult(context, intent, result);

            return Service.START_NOT_STICKY;
        }

        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            super.onDestroy();
            cleanUpMediaPlayer();
        }

        /**
         * Releases MediaPlayer resources
         */
        protected static void cleanUpMediaPlayer() {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
            Logger.logVerbose(LOG_TAG, "onError: what: " + what + ", extra: "  + extra);
            return false;
        }

        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            hasTrack = false;
            mediaPlayer.reset();
        }

        protected static MediaCommandHandler getMediaCommandHandler(final String command) {
            switch (command == null ? "" : command) {
                case "info":
                    return infoHandler;
                case "play":
                    return playHandler;
                case "pause":
                    return pauseHandler;
                case "resume":
                    return resumeHandler;
                case "stop":
                    return stopHandler;
                default:
                    return (player, context, intent) -> {
                        MediaCommandResult result = new MediaCommandResult();
                        result.error = "Unknown command: " + command;
                        return result;
                    };
            }
        }

        /**
         * Returns result of executing a media command to termux
         */
        protected static void postMediaCommandResult(final Context context, final Intent intent,
                                                     final MediaCommandResult result) {

            ResultReturner.returnData(context, intent, out -> {
                out.append(result.message).append("\n");
                if (result.error != null) {
                    out.append(result.error).append("\n");
                }
                out.flush();
                out.close();
            });
        }

        /**
         * -----
         * Media Command Handlers
         * -----
         */

        static MediaCommandHandler infoHandler = new MediaCommandHandler() {
            @Override
            public MediaCommandResult handle(MediaPlayer player, Context context, Intent intent) {
                MediaCommandResult result = new MediaCommandResult();

                if (hasTrack) {
                    String status = player.isPlaying() ? "Playing" : "Paused";
                    result.message = String.format("Status: %s\nTrack: %s\nCurrent Position: %s", status, trackName, getPlaybackPositionString(player));
                } else {
                    result.message = "No track currently!";
                }
                return result;
            }
        };

        static MediaCommandHandler playHandler = new MediaCommandHandler() {
            @Override
            public MediaCommandResult handle(MediaPlayer player, Context context, Intent intent) {
                MediaCommandResult result = new MediaCommandResult();

                File mediaFile;
                try {
                    mediaFile = new File(intent.getStringExtra("file"));
                } catch (NullPointerException e) {
                    result.error = "No file was specified";
                    return result;
                }

                if (hasTrack) {
                    player.stop();
                    player.reset();
                    hasTrack = false;
                }

                try {
                    player.setDataSource(mediaFile.getCanonicalPath());
                    player.prepare();
                } catch (IOException e) {
                    result.error = e.getMessage();
                    return result;
                }

                player.start();
                hasTrack = true;
                trackName = mediaFile.getName();
                result.message = "Now Playing: " + trackName;
                return result;
            }
        };

        static MediaCommandHandler pauseHandler = new MediaCommandHandler() {
            @Override
            public MediaCommandResult handle(MediaPlayer player, Context context, Intent intent) {
                MediaCommandResult result = new MediaCommandResult();

                if (hasTrack) {
                    if (player.isPlaying()) {
                        player.pause();
                        result.message = "Paused playback";
                    } else {
                        result.message = "Playback already paused";
                    }
                } else {
                    result.message = "No track to pause";
                }
                return result;
            }
        };

        /**
         * Creates string showing current position in active track
         */
        protected static String getPlaybackPositionString(MediaPlayer player) {
            int duration = player.getDuration() / 1000;
            int position = player.getCurrentPosition() / 1000;
            return getTimeString(position) + " / " + getTimeString(duration);
        }

        static MediaCommandHandler resumeHandler = new MediaCommandHandler() {
            @Override
            public MediaCommandResult handle(MediaPlayer player, Context context, Intent intent) {
                MediaCommandResult result = new MediaCommandResult();
                if (hasTrack) {
                    String positionString = String.format("Track: %s\nCurrent Position: %s", trackName, getPlaybackPositionString(player));

                    if (player.isPlaying()) {
                        result.message = "Already playing track!\n" + positionString;
                    } else {
                        player.start();
                        result.message = "Resumed playback\n" + positionString;
                    }
                } else {
                    result.message = "No previous track to resume!\nPlease supply a new media file";
                }
                return result;
            }
        };

        static MediaCommandHandler stopHandler = new MediaCommandHandler() {
            @Override
            public MediaCommandResult handle(MediaPlayer player, Context context, Intent intent) {
                MediaCommandResult result = new MediaCommandResult();

                if (hasTrack) {
                    player.stop();
                    player.reset();
                    hasTrack = false;
                    result.message = "Stopped playback\nTrack cleared";
                } else {
                    result.message = "No track to stop";
                }
                return result;
            }
        };
    }

    /**
     * Interface for handling media commands
     */
    interface MediaCommandHandler {
        MediaCommandResult handle(MediaPlayer player, final Context context, final Intent intent);
    }

    /**
     * Simple POJO to store the result of executing a media command
     */
    static class MediaCommandResult {
        public String message = "";
        public String error;
    }
}
