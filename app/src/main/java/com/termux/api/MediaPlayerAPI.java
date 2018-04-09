package com.termux.api;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * API that enables playback of standard audio formats such as:
 * mp3, wav, flac, etc... using Android's default MediaPlayer
 */
public class MediaPlayerAPI {

    /**
     * Starts our PlayerService
     * @param context
     * @param intent
     */
    static void onReceive(final Context context, final Intent intent) {
        // Create intent for starting our player service and make sure
        // we retain all relevant info from this intent
        Intent playerService = new Intent(context, PlayerService.class);
        playerService.setAction(intent.getAction());
        playerService.putExtras(intent.getExtras());

        context.startService(playerService);
    }

    /**
     * Converts time in seconds to a formatted time string: HH:MM:SS
     * Hours will not be included if it is 0
     * @param totalSeconds
     * @return
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
    public static class PlayerService extends Service implements MediaPlayer.OnErrorListener,
            MediaPlayer.OnCompletionListener {

        protected static MediaPlayer mediaPlayer;

        // do we currently have a track to play?
        protected static boolean hasTrack;


        /**
         * Returns our MediaPlayer instance and ensures it has all the necessary callbacks
         * @return
         */
        protected MediaPlayer getMediaPlayer() {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setOnCompletionListener(this);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                mediaPlayer.setVolume(100, 100);
            }
            return mediaPlayer;
        }

        /**
         * What we received from TermuxApiReceiver but now within this service
         * @param intent
         * @param flags
         * @param startId
         * @return
         */
        public int onStartCommand(Intent intent, int flags, int startId) {
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
            super.onDestroy();
            cleanUpMediaPlayer();
            TermuxApiLogger.info("MediaPlayerAPI PlayerService onDestroy()");
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
            TermuxApiLogger.error("MediaPlayerAPI error: " + what);
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
                    return new MediaCommandHandler() {
                        @Override
                        public MediaCommandResult handle(MediaPlayer player, Context context, Intent intent) {
                            MediaCommandResult result = new MediaCommandResult();
                            result.error = "Unknown command: " + command;
                            return result;
                        };
                    };
            }
        }

        /**
         * Returns result of executing a media command to termux
         * @param context
         * @param intent
         * @param result
         */
        protected static void postMediaCommandResult(final Context context, final Intent intent,
                                                     final MediaCommandResult result) {

            ResultReturner.returnData(context, intent, new ResultReturner.ResultWriter() {
                @Override
                public void writeResult(PrintWriter out) throws Exception {
                    out.append(result.message + "\n");
                    if (result.error != null) {
                        out.append(result.error + "\n");
                    }
                    out.flush();
                    out.close();
                }
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
                    result.message = String.format("Song: %s\nStatus: %s\n%s",mediaFile.getName(), status, getPlaybackPositionString(player));
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

                File mediaFile = new File(intent.getStringExtra("file"));

                if (player.isPlaying()) {
                    player.stop();
                    player.reset();
                }
                try {
                    player.setDataSource(context, Uri.fromFile(mediaFile));
                    player.prepare();
                    player.start();
                    hasTrack = true;

                    if (player.isPlaying()) {
                        result.message = "Now Playing: " + mediaFile.getName();
                    } else {
                        result.error = "Failed to play: " + mediaFile.getName();
                    }
                } catch (IOException e) {
                    result.error = e.getMessage();
                }
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
         * @param player
         * @return
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
                    String positionString = "Current Position: " + getPlaybackPositionString(player);

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

                if (player.isPlaying()) {
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
