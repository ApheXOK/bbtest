package com.example.myapplication;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

import android.app.Activity;
import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;

import com.intertrust.wasabi.ErrorCodeException;
import com.intertrust.wasabi.Runtime;
import com.intertrust.wasabi.media.PlaylistProxy;
import com.intertrust.wasabi.media.PlaylistProxy.MediaSourceParams;
import com.intertrust.wasabi.media.PlaylistProxy.MediaSourceType;
import com.intertrust.wasabi.media.PlaylistProxyListener;


/*
 * this enum simply maps the media types to the mimetypes required for the playlist proxy
 */
enum ContentTypes {
    DASH("application/dash+xml"), HLS("application/vnd.apple.mpegurl"), PDCF(
            "video/mp4"), M4F("video/mp4"), DCF("application/vnd.oma.drm.dcf"), BBTS(
            "video/mp2t");
    String mediaSourceParamsContentType = null;

    private ContentTypes(String mediaSourceParamsContentType) {
        this.mediaSourceParamsContentType = mediaSourceParamsContentType;
    }

    public String getMediaSourceParamsContentType() {
        return mediaSourceParamsContentType;
    }
}

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marlin_broadband_example);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new MBB_Playback_Fragment()).commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.marlin_broadband_example, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class MBB_Playback_Fragment extends Fragment implements PlaylistProxyListener {

        private PlaylistProxy playerProxy;
        static final String TAG = "SampleBBPlayer";

        public MBB_Playback_Fragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(
                    R.layout.fragment_marlin_broadband_example, container,
                    false);

            /*
             * Create a VideView for playback
             */
            VideoView videoView = (VideoView) rootView
                    .findViewById(R.id.videoView);
            MediaController mediaController = new MediaController(
                    getActivity(), false);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);

            try {
                /*
                 * Initialize the Wasabi Runtime (necessary only once for each
                 * instantiation of the application)
                 *
                 * ** Note: Set Runtime Properties as needed for your
                 * environment
                 */
                Runtime.initialize(getActivity().getDir("wasabi", MODE_PRIVATE)
                        .getAbsolutePath());
                /*
                 * Personalize the application (acquire DRM keys). This is only
                 * necessary once each time the application is freshly installed
                 *
                 * ** Note: personalize() is a blocking call and may take long
                 * enough to complete to trigger ANR (Application Not
                 * Responding) errors. In a production application this should
                 * be called in a background thread.
                 */
                if (!Runtime.isPersonalized())
                    Runtime.personalize();

            } catch (NullPointerException e) {
                return rootView;
            } catch (ErrorCodeException e) {
                // Consult WasabiErrors.txt for resolution of the error codes
                Log.e(TAG, "runtime initialization or personalization error: "
                        + e.getLocalizedMessage());
                return rootView;
            }

            /*
             * Acquire a Marlin Broadband License. The license is acquired using
             * a License Acquisition token. Such tokens for sample content can
             * be obtained from http://content.intertrust.com/express/ and in
             * this example are stored in the Android project /assets directory
             * using the filename "license-token.xml".
             *
             * For instance, you can download such a token from
             * http://content-access.intertrust-dev.com/EXPR005/bb, and save it
             * to the assets directory as license-token.xml"
             *
             * *** Note: processServiceToken() is a blocking call and may take
             * long enough to complete to trigger ANR (Application Not
             * Responding) errors. In a production application this should be
             * called in a background thread.
             */


            String licenseAcquisitionToken = getActionTokenFromAssets("license-token.xml");
            if (licenseAcquisitionToken == null) {
                Log.e(TAG,
                        "Could not find action token in the assets directory - exiting");
                return rootView;
            }
            long start = System.currentTimeMillis();
            try {
                Runtime.processServiceToken(licenseAcquisitionToken);
                Log.i(TAG,
                        "License successfully acquired in (ms): "
                                + (System.currentTimeMillis() - start));
            } catch (ErrorCodeException e1) {
                Log.e(TAG,
                        "Could not acquire the license from the license acquisition token - exiting");
                return rootView;
            }

            /*
             * create a playlist proxy and start it
             */
            try {
                EnumSet<PlaylistProxy.Flags> flags = EnumSet.noneOf(PlaylistProxy.Flags.class);
                playerProxy = new PlaylistProxy(flags, this, new Handler());
                playerProxy.start();
            } catch (ErrorCodeException e) {
                // Consult WasabiErrors.txt for resolution of the error codes
                Log.e(TAG, "playlist proxy error: " + e.getLocalizedMessage());
                return rootView;
            }

            /*
             * Acquire a media stream URL encrypted with the key delivered in
             * the above license. Media Stream URLs can be obtained at
             * http://content.intertrust.com/express/.
             *
             * For instance, a DASH content stream protected with the license
             * token example above is
             * "http://content.intertrust.com/express/dash/mpd.xml"
             *
             * Note that the MediaSourceType must be adapted to the stream type
             * (DASH or HLS). Similarly,
             * the MediaSourceParams need to be set according to the media type
             * if MediaSourceType is SINGLE_FILE
             */

            String dash_url = "http://content-access.intertrust-dev.com/content/onDemandprofile/Frozen-OnDemand/stream.mpd";
            ContentTypes contentType = ContentTypes.DASH;

            MediaSourceParams params = new MediaSourceParams();
            params.sourceContentType = contentType
                    .getMediaSourceParamsContentType();

            /*
             * if the content has separate audio tracks (eg languages) you may
             * select one using MediaSourceParams, eg params.language="es";
             */

            /*
             * Create a PlaylistProxy URL and pass it to the VideView and start
             * playback
             */
            String proxy_url = null;
            String contentTypeValue = contentType.toString();
            try {
                proxy_url = playerProxy.makeUrl(dash_url, MediaSourceType.valueOf(
                        (contentTypeValue.equals("HLS") || contentTypeValue.equals("DASH"))?contentTypeValue:"SINGLE_FILE"), params);
                videoView.setVideoURI(Uri.parse(proxy_url));
                videoView.start();

            } catch (Exception e) {
                // Consult WasabiErrors.txt for resolution of the error codes
                Log.e(TAG, "playback error: " + e.getLocalizedMessage());
                e.printStackTrace();
                return rootView;
            }

            return rootView;

        }

        /**************************************
         * Helper methods to avoid cluttering *
         **************************************/

        /*
         * Read an action token file from the assets directory
         */
        protected String getActionTokenFromAssets(String tokenFileName) {
            String token = null;
            byte[] readBuffer = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = null;
            int bytesRead = 0;

            try {
                is = getActivity().getAssets()
                        .open(tokenFileName, MODE_PRIVATE);
                while ((bytesRead = is.read(readBuffer)) != -1) {
                    baos.write(readBuffer, 0, bytesRead);
                }
                baos.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            token = new String(baos.toByteArray());
            return token;
        }

        public void onErrorNotification(int errorCode, String errorString) {
            Log.e(TAG, "PlaylistProxy Event: Error Notification, error code = " +
                    Integer.toString(errorCode) + ", error string = " +
                    errorString);
        }
    }

}
