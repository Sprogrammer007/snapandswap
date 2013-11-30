/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.snapandswap;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.media.SoundPool;
import android.os.Vibrator;
import android.util.Log;

/*
 * This class controls the sound playback according to the API level.
 */
public class SoundClips {
    // Sound actions.
    public static final int FOCUS_COMPLETE = 0;
    public static final int SHUTTER_CLICK = 1;

    public interface Player {
        public void release();
        public void play(int action);
        public void vibrate(long time);
    }

    public static Player getPlayer(Context context) {
        if (ApiHelper.HAS_MEDIA_ACTION_SOUND) {
            return new MediaActionSoundPlayer(context);
        } else {
            return new SoundPoolPlayer(context);
        }
    }

    /**
     * This class implements SoundClips.Player using MediaActionSound,
     * which exists since API level 16.
     */
    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private static class MediaActionSoundPlayer implements Player {
        private static final String TAG = "MediaActionSoundPlayer";
        private MediaActionSound mSound;
        private Vibrator mVibrator;

        @Override
        public void release() {
            if (mSound != null) {
                mSound.release();
                mSound = null;
            }
        }

        public MediaActionSoundPlayer(Context context) {
            mSound = new MediaActionSound();
            mSound.load(MediaActionSound.FOCUS_COMPLETE);
            mSound.load(MediaActionSound.SHUTTER_CLICK);

            mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        @Override
        public synchronized void play(int action) {
            switch(action) {
                case FOCUS_COMPLETE:
                    mSound.play(MediaActionSound.FOCUS_COMPLETE);
                    break;
                case SHUTTER_CLICK:
                    mSound.play(MediaActionSound.SHUTTER_CLICK);
                    break;
                default:
                    Log.w(TAG, "Unrecognized action:" + action);
            }
        }

        @Override
        public synchronized void vibrate(long time) {
            if (mVibrator.hasVibrator()) {
                mVibrator.cancel();
                mVibrator.vibrate(time);
            }
        }
    }

    /**
     * This class implements SoundClips.Player using SoundPool, which
     * exists since API level 1.
     */
    private static class SoundPoolPlayer implements
            Player, SoundPool.OnLoadCompleteListener {

        private static final String TAG = "SoundPoolPlayer";
        private static final int NUM_SOUND_STREAMS = 1;
        private static final int SOUND_RES = R.raw.focus_complete;

        // ID returned by load() should be non-zero.
        private static final int ID_NOT_LOADED = 0;

        // Maps a sound action to the id;
        private final int[] mSoundRes = {0, 1, 1};
        // Store the context for lazy loading.
        private Context mContext;
        // mSoundPool is created every time load() is called and cleared every
        // time release() is called.
        private SoundPool mSoundPool;
        // Sound ID of each sound resources. Given when the sound is loaded.
        private int mSoundID;
        private boolean mSoundIDReady;
        private int mSoundIDToPlay;

        public SoundPoolPlayer(Context context) {
            mContext = context;
            int audioType = ApiHelper.getIntFieldIfExists(AudioManager.class,
                    "STREAM_SYSTEM_ENFORCED", null, AudioManager.STREAM_RING);

            mSoundIDToPlay = ID_NOT_LOADED;

            mSoundPool = new SoundPool(NUM_SOUND_STREAMS, audioType, 0);
            mSoundPool.setOnLoadCompleteListener(this);

            mSoundID = mSoundPool.load(mContext, SOUND_RES, 1);
            mSoundIDReady = false;
        }

        @Override
        public synchronized void release() {
            if (mSoundPool != null) {
                mSoundPool.release();
                mSoundPool = null;
            }
        }

        @Override
        public synchronized void play(int action) {

            if (mSoundID == ID_NOT_LOADED) {
                // Not loaded yet, load first and then play when the loading is complete.
                mSoundID = mSoundPool.load(mContext, SOUND_RES, 1);
                mSoundIDToPlay = mSoundID;
            } else if (!mSoundIDReady) {
                // Loading and not ready yet.
                mSoundIDToPlay = mSoundID;
            } else {
                mSoundPool.play(mSoundID, 1f, 1f, 0, 0, 1f);
            }
        }

        @Override
        public void vibrate(long time) {

        }

        @Override
        public void onLoadComplete(SoundPool pool, int soundID, int status) {
            if (status != 0) {
                Log.e(TAG, "loading sound tracks failed (status=" + status + ")");
                if (mSoundID == soundID) {
                    mSoundID = ID_NOT_LOADED;
                }
                return;
            }

            if (mSoundID == soundID) {
                mSoundIDReady = true;
            }

            if (soundID == mSoundIDToPlay) {
                mSoundIDToPlay = ID_NOT_LOADED;
                mSoundPool.play(soundID, 1f, 1f, 0, 0, 1f);
            }
        }
    }
}
