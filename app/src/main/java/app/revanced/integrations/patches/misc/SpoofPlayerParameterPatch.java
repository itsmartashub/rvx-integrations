package app.revanced.integrations.patches.misc;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.shared.PlayerType;
import app.revanced.integrations.utils.LogHelper;

public class SpoofPlayerParameterPatch {

    /**
     * Target player parameters.
     */
    private static final List<String> PLAYER_PARAMETER_WHITELIST = Arrays.asList(
            "YAHI", // Autoplay in feed
            "SAFg"  // Autoplay in scrim
    );

    /**
     * Player parameters parameters used in shorts.
     */
    private static final String PLAYER_PARAMETER_SHORTS = "8AEB";

    /**
     * Player parameters used in incognito mode's visitor data.
     * <p>
     * Known issue
     * - Ambient mode may not work
     * - Clip cannot be played normally
     * - Downloading videos may not work
     * - Filmstrip overlay are always hidden
     * - No spoofing applied when watching previews in the feed
     * - Seekbar thumbnails are hidden
     */
    private static final String PLAYER_PARAMETER_INCOGNITO = "CgIQBg==";

    private static boolean isPlayingShorts;

    /**
     * Injection point.
     *
     * @param playerParameter player parameter
     */
    public static String overridePlayerParameter(String playerParameter) {
        try {
            if (!SettingsEnum.SPOOF_PLAYER_PARAMETER.getBoolean()) {
                return playerParameter;
            }

            if (playerParameter.startsWith(PLAYER_PARAMETER_SHORTS)) {
                isPlayingShorts = true;

                return playerParameter;
            }

            isPlayingShorts = false;

            LogHelper.printDebug(SpoofPlayerParameterPatch.class, "Original player parameter value: " + playerParameter);

            boolean isPlayingFeed = PLAYER_PARAMETER_WHITELIST.stream().anyMatch(playerParameter::contains)
                    && PlayerType.getCurrent() == PlayerType.INLINE_MINIMAL;

            if (isPlayingFeed) {
                // Spoofing is not applied when playing in the feed.
                // It can prevent unintended histories from being added to your watching history.
                // This will cause playback buffer issues for videos playing in the feed.
                return playerParameter;
            } else {
                // Spoof the player parameter to prevent playback issues.
                return PLAYER_PARAMETER_INCOGNITO;
            }
        } catch (Exception ex) {
            LogHelper.printException(SpoofPlayerParameterPatch.class, "overrideProtobufParameter failure", ex);
        }

        return playerParameter;
    }


    /**
     * Injection point.
     */
    public static boolean getSeekbarThumbnailOverrideValue() {
        return SettingsEnum.SPOOF_PLAYER_PARAMETER.getBoolean();
    }

    /**
     * Injection point.
     *
     * @param view seekbar thumbnail view.  Includes both shorts and regular videos.
     */
    public static void seekbarImageViewCreated(@NonNull ImageView view) {
        // seekbar thumbnail view does not need to be hidden in Shorts videos
        if (!SettingsEnum.SPOOF_PLAYER_PARAMETER.getBoolean() || isPlayingShorts)
            return;

        try {
            view.setVisibility(View.GONE);
            // Also hide the white border around the thumbnail (otherwise a 1 pixel wide bordered frame is visible).
            ViewGroup parentLayout = (ViewGroup) view.getParent();
            parentLayout.setPadding(0, 0, 0, 0);
        } catch (Exception ex) {
            LogHelper.printException(SpoofPlayerParameterPatch.class, "seekbarImageViewCreated failure", ex);
        }
    }
}
