package dev.denza.disharebridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiShareScreens {
    public interface Callback {
        void onScreens(List<Screen> screens);

        void onFailed(String message);
    }

    public static final class Screen {
        public final String deviceId;
        public final String screenId;
        public final boolean available;

        Screen(String deviceId, String screenId, boolean available) {
            this.deviceId = deviceId;
            this.screenId = screenId;
            this.available = available;
        }

        @Override
        public String toString() {
            return "Screen{deviceId=" + deviceId
                    + ", screenId=" + screenId
                    + ", available=" + available + '}';
        }
    }

    private static final String CONTROL_ACTION = "com.byd.dishare.control.DiShareControlService";
    private static final String CONTROL_DESCRIPTOR = "com.byd.dishare.control.IDiShareControl";
    private static final String CONTROL_PACKAGE = "com.byd.dishare";
    private static final int TX_GET_SCREENS = 0x4;

    private DiShareScreens() {
    }

    public static void query(Context context, String packageName, Callback callback) {
        new Query(context.getApplicationContext(), packageName, callback).start();
    }

    private static final class Query {
        private final Context context;
        private final String packageName;
        private final Callback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private IBinder controlBinder;
        private boolean bound;
        private boolean finished;

        private final ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                controlBinder = service;
                try {
                    callback.onScreens(readScreens());
                    finish();
                } catch (RuntimeException e) {
                    fail(shortError(e));
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                controlBinder = null;
                bound = false;
            }
        };

        Query(Context context, String packageName, Callback callback) {
            this.context = context;
            this.packageName = packageName == null || packageName.trim().isEmpty()
                    ? CONTROL_PACKAGE : packageName.trim();
            this.callback = callback;
        }

        void start() {
            Intent intent = new Intent();
            intent.setAction(CONTROL_ACTION);
            intent.setPackage(CONTROL_PACKAGE);
            try {
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            } catch (RuntimeException e) {
                fail("bind failed: " + shortError(e));
                return;
            }
            if (!bound) {
                fail("bind returned false");
                return;
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    fail("timeout");
                }
            }, 3000L);
        }

        private List<Screen> readScreens() {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(CONTROL_DESCRIPTOR);
                data.writeString(packageName);
                if (controlBinder == null || !controlBinder.transact(TX_GET_SCREENS, data, reply, 0)) {
                    throw new IllegalStateException("getScreens transact failed");
                }
                reply.readException();
                int size = reply.readInt();
                if (size < 0) {
                    return Collections.emptyList();
                }
                List<Screen> screens = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    if (reply.readInt() == 0) {
                        continue;
                    }
                    screens.add(new Screen(reply.readString(), reply.readString(),
                            reply.readByte() != 0));
                }
                return Collections.unmodifiableList(screens);
            } catch (RemoteException e) {
                throw new IllegalStateException(e);
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        private void fail(String message) {
            if (finished) {
                return;
            }
            callback.onFailed(message);
            finish();
        }

        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            handler.removeCallbacksAndMessages(null);
            if (bound) {
                try {
                    context.unbindService(connection);
                } catch (RuntimeException ignored) {
                    // DiShare can already have restarted or closed the binding.
                }
            }
            bound = false;
            controlBinder = null;
        }
    }

    private static String shortError(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
