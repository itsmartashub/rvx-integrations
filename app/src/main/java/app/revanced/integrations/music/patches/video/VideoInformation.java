package app.revanced.integrations.music.patches.video;

import static app.revanced.integrations.shared.utils.ResourceUtils.getString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import app.revanced.integrations.shared.utils.Logger;
import app.revanced.integrations.shared.utils.Utils;

/**
 * Hooking class for the current playing video.
 */
@SuppressWarnings("unused")
public final class VideoInformation {
    private static final float DEFAULT_YOUTUBE_MUSIC_PLAYBACK_SPEED = 1.0f;
    private static final int DEFAULT_YOUTUBE_MUSIC_VIDEO_QUALITY = -2;
    private static final String DEFAULT_YOUTUBE_MUSIC_VIDEO_QUALITY_STRING = getString("quality_auto");
    private static final String SEEK_METHOD_NAME = "seekTo";

    private static WeakReference<Object> playerControllerRef;
    private static Method seekMethod;

    @NonNull
    private static String videoId = "";

    private static long videoLength = 0;
    private static long videoTime = -1;

    /**
     * The current playback speed
     */
    private static float playbackSpeed = DEFAULT_YOUTUBE_MUSIC_PLAYBACK_SPEED;
    /**
     * The current video quality
     */
    private static int videoQuality = DEFAULT_YOUTUBE_MUSIC_VIDEO_QUALITY;
    /**
     * The current video quality string
     */
    private static String videoQualityString = DEFAULT_YOUTUBE_MUSIC_VIDEO_QUALITY_STRING;
    /**
     * The available qualities of the current video in human readable form: [1080, 720, 480]
     */
    @Nullable
    private static List<Integer> videoQualities;

    /**
     * Injection point.
     *
     * @param playerController player controller object.
     */
    public static void initialize(@NonNull Object playerController) {
        try {
            playerControllerRef = new WeakReference<>(Objects.requireNonNull(playerController));
            videoTime = -1;
            videoLength = 0;

            seekMethod = playerController.getClass().getMethod(SEEK_METHOD_NAME, Long.TYPE);
            seekMethod.setAccessible(true);
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to initialize", ex);
        }
    }

    /**
     * Id of the current video playing.  Includes Shorts and YouTube Stories.
     *
     * @return The id of the video. Empty string if not set yet.
     */
    @NonNull
    public static String getVideoId() {
        return videoId;
    }

    /**
     * Injection point.
     *
     * @param newlyLoadedVideoId id of the current video
     */
    public static void setVideoId(@NonNull String newlyLoadedVideoId) {
        if (Objects.equals(newlyLoadedVideoId, videoId)) {
            return;
        }
        Logger.printDebug(() -> "New video id: " + newlyLoadedVideoId);
        videoId = newlyLoadedVideoId;
    }

    /**
     * Seek on the current video.
     * Does not function for playback of Shorts.
     * <p>
     * Caution: If called from a videoTimeHook() callback,
     * this will cause a recursive call into the same videoTimeHook() callback.
     *
     * @param millisecond The millisecond to seek the video to.
     * @return if the seek was successful
     */
    public static boolean seekTo(final long millisecond) {
        Utils.verifyOnMainThread();
        try {
            Logger.printDebug(() -> "Seeking to " + millisecond);
            Object seekResultObject = seekMethod.invoke(playerControllerRef.get(), millisecond);

            if (!(seekResultObject instanceof Boolean seekResult))
                return false;

            return seekResult;
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to seek", ex);
            return false;
        }
    }

    /**
     * @return The current playback speed.
     */
    public static float getPlaybackSpeed() {
        return playbackSpeed;
    }

    /**
     * Injection point.
     *
     * @param newlyLoadedPlaybackSpeed The current playback speed.
     */
    public static void setPlaybackSpeed(float newlyLoadedPlaybackSpeed) {
        playbackSpeed = newlyLoadedPlaybackSpeed;
    }

    /**
     * Overrides the current quality.
     * Rest of the implementation added by patch.
     */
    public static void overrideVideoQuality(int qualityOverride) {
        Logger.printDebug(() -> "Overriding video quality to: " + qualityOverride);
    }

    /**
     * @return The current video quality.
     */
    public static int getVideoQuality() {
        return videoQuality;
    }

    /**
     * @return The current video quality string.
     */
    public static String getVideoQualityString() {
        return videoQualityString;
    }

    /**
     * Injection point.
     *
     * @param newlyLoadedQuality The current video quality string.
     */
    public static void setVideoQuality(String newlyLoadedQuality) {
        if (newlyLoadedQuality == null) {
            return;
        }
        try {
            String splitVideoQuality;
            if (newlyLoadedQuality.contains("p")) {
                splitVideoQuality = newlyLoadedQuality.split("p")[0];
                videoQuality = Integer.parseInt(splitVideoQuality);
                videoQualityString = splitVideoQuality + "p";
            } else if (newlyLoadedQuality.contains("s")) {
                splitVideoQuality = newlyLoadedQuality.split("s")[0];
                videoQuality = Integer.parseInt(splitVideoQuality);
                videoQualityString = splitVideoQuality + "s";
            } else {
                videoQuality = DEFAULT_YOUTUBE_MUSIC_VIDEO_QUALITY;
                videoQualityString = DEFAULT_YOUTUBE_MUSIC_VIDEO_QUALITY_STRING;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * @return available video quality.
     */
    public static int getAvailableVideoQuality(int preferredQuality) {
        if (videoQualities != null) {
            int qualityToUse = videoQualities.get(0); // first element is automatic mode
            for (Integer quality : videoQualities) {
                if (quality <= preferredQuality && qualityToUse < quality) {
                    qualityToUse = quality;
                }
            }
            preferredQuality = qualityToUse;
        }
        return preferredQuality;
    }

    /**
     * Injection point.
     *
     * @param qualities Video qualities available, ordered from largest to smallest, with index 0 being the 'automatic' value of -2
     */
    public static void setVideoQualityList(Object[] qualities) {
        try {
            if (videoQualities == null || videoQualities.size() != qualities.length) {
                videoQualities = new ArrayList<>(qualities.length);
                for (Object streamQuality : qualities) {
                    for (Field field : streamQuality.getClass().getFields()) {
                        if (field.getType().isAssignableFrom(Integer.TYPE)
                                && field.getName().length() <= 2) {
                            videoQualities.add(field.getInt(streamQuality));
                        }
                    }
                }
                Logger.printDebug(() -> "videoQualities: " + videoQualities);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to set quality list", ex);
        }
    }

    /**
     * Length of the current video playing.  Includes Shorts.
     *
     * @return The length of the video in milliseconds.
     * If the video is not yet loaded, or if the video is playing in the background with no video visible,
     * then this returns zero.
     */
    public static long getVideoLength() {
        return videoLength;
    }

    /**
     * Injection point.
     *
     * @param length The length of the video in milliseconds.
     */
    public static void setVideoLength(final long length) {
        if (videoLength != length) {
            videoLength = length;
        }
    }

    /**
     * Playback time of the current video playing.  Includes Shorts.
     * <p>
     * Value will lag behind the actual playback time by a variable amount based on the playback speed.
     * <p>
     * If playback speed is 2.0x, this value may be up to 2000ms behind the actual playback time.
     * If playback speed is 1.0x, this value may be up to 1000ms behind the actual playback time.
     * If playback speed is 0.5x, this value may be up to 500ms behind the actual playback time.
     * Etc.
     *
     * @return The time of the video in milliseconds. -1 if not set yet.
     */
    public static long getVideoTime() {
        return videoTime;
    }

    /**
     * Injection point.
     * Called on the main thread every 1000ms.
     *
     * @param currentPlaybackTime The current playback time of the video in milliseconds.
     */
    public static void setVideoTime(final long currentPlaybackTime) {
        videoTime = currentPlaybackTime;
    }

}
