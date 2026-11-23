package com.foobnix.drive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.io.File;

/**
 * There is no Google Drive in this build, and none of the code that used to talk to it.
 *
 * <p>What remains is only the shape the rest of the reader asks for: the settings screen asks
 * whether an account is connected (no), the file menu asks to open one (nothing happens), the
 * activity forwards the sign-in result it will never receive (not handled), and the profile
 * asks to delete a remote copy of a book (there is no remote copy). Keeping the names means the
 * call sites read unchanged, while no Google library, no account, and no network request is in
 * the APK at all.
 */
public final class GFile {

    public static final String TAG = "GFile";
    public static final int REQUEST_CODE_SIGN_IN = 1110;
    public static final String MY_SCOPE = "";
    public static final String SKIP = "skip";
    public static final String TEMP_SUFFIX = ".temp";
    public static final String MIME_FOLDER = "application/vnd.google-apps.folder";
    public static final int PAGE_SIZE = 1000;
    public static final StringBuilder debugOut = new StringBuilder();

    /** Seconds a sync may run before it gives up. No sync runs here, so it stays zero. */
    public static int timeout = 0;

    private GFile() {
    }

    static void debug(String text) {
    }

    public static void clearDebug() {
        debugOut.setLength(0);
    }

    /** The account the settings screen would print: there is none. */
    public static String getDisplayInfo(Context c) {
        return null;
    }

    public static void logout(Context c) {
    }

    public static void init(Activity c) {
    }

    public static boolean onSignInActivityResult(Activity c, int requestCode, int resultCode, Intent data) {
        return false;
    }

    public static void runSyncService(Activity c) {
    }

    public static void runSyncService(Activity c, boolean force) {
    }

    /** Books are only ever local; there is no remote copy to remove. */
    public static void deleteRemoteFile(File file) {
    }

    /** No sync runs, so nothing is ever out of date. */
    public static boolean isNeedUpdate = false;

    public static void sycnronizeAll(Context c) {
    }

    /** Book order falls back to the file's own timestamp, which is all there is without a remote copy. */
    public static long getLastModified(File file) {
        return file == null ? 0 : file.lastModified();
    }
}