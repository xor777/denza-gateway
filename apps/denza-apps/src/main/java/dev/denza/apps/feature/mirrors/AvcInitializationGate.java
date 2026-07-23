package dev.denza.apps.feature.mirrors;

import android.os.RemoteException;

/**
 * Keeps the vendor init acknowledgement as the fail-closed gate for viewpoint and READY.
 */
final class AvcInitializationGate {
    interface Attempt {
        boolean initDisplay() throws RemoteException;

        void setViewpoint() throws RemoteException;

        void onReady();

        void onRejected();
    }

    private AvcInitializationGate() {
    }

    static void run(Attempt attempt) throws RemoteException {
        if (!attempt.initDisplay()) {
            attempt.onRejected();
            return;
        }
        attempt.setViewpoint();
        attempt.onReady();
    }
}
