package com.github.cythara;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.pitch.FastYin;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

import static be.tarsos.dsp.io.android.AudioDispatcherFactory.fromDefaultMicrophone;
import static be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm.FFT_YIN;

public class ListenerFragment extends Fragment {

    interface TaskCallbacks {

        void onProgressUpdate(PitchDifference percent);
    }

    private static final int SAMPLE_RATE = 44100;

    private static final int BUFFER_SIZE = FastYin.DEFAULT_BUFFER_SIZE;
    private static final int OVERLAP = FastYin.DEFAULT_OVERLAP;
    private static final int MIN_ITEMS_COUNT = 75;
    private static List<PitchDifference> pitchDifferences = new ArrayList<>();

    private PitchListener pitchListener;
    private TaskCallbacks taskCallbacks;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        taskCallbacks = (TaskCallbacks) context;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            taskCallbacks = (TaskCallbacks) activity;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        pitchListener = new PitchListener();
        pitchListener.execute();
    }

    @Override
    public void onDetach() {
        super.onDetach();

        taskCallbacks = null;
        pitchListener.cancel(true);
    }

    @Override
    public void onPause() {
        super.onPause();

        pitchListener.cancel(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (pitchListener.isCancelled()) {
            pitchListener = new PitchListener();
            pitchListener.execute();
        }
    }

    private class PitchListener extends AsyncTask<Void, PitchDifference, Void> {

        private AudioDispatcher audioDispatcher;

        @Override
        protected Void doInBackground(Void... params) {
            PitchDetectionHandler pitchDetectionHandler = new PitchDetectionHandler() {
                @Override
                public void handlePitch(PitchDetectionResult pitchDetectionResult,
                                        AudioEvent audioEvent) {

                    if (isCancelled()) {
                        stopAudioDispatcher();
                        return;
                    }

                    float pitch = pitchDetectionResult.getPitch();

                    if (pitch != -1) {
                        PitchDifference pitchDifference = PitchComparator.retrieveNote(pitch);


                        pitchDifferences.add(pitchDifference);

                        if (pitchDifferences.size() >= MIN_ITEMS_COUNT) {
                            PitchDifference average =
                                    Sampler.calculateAverageDifference(pitchDifferences);

                            publishProgress(average);

                            pitchDifferences.clear();
                        }
                    }
                }
            };

            PitchProcessor pitchProcessor = new PitchProcessor(FFT_YIN, SAMPLE_RATE,
                    BUFFER_SIZE, pitchDetectionHandler);

            audioDispatcher = fromDefaultMicrophone(SAMPLE_RATE,
                    BUFFER_SIZE, OVERLAP);

            audioDispatcher.addAudioProcessor(pitchProcessor);

            audioDispatcher.run();

            return null;
        }

        @Override
        protected void onCancelled(Void result) {
            stopAudioDispatcher();
        }

        @Override
        protected void onProgressUpdate(PitchDifference... pitchDifference) {
            if (taskCallbacks != null) {
                taskCallbacks.onProgressUpdate(pitchDifference[0]);
            }
        }

        private void stopAudioDispatcher() {
            if (!audioDispatcher.isStopped()) {
                audioDispatcher.stop();
            }
        }
    }
}
