package dev.denza.apps;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Seamless Simulcast overlay driven by accessibility, replacing the old
 * broadcast/timer/hard-coded-profile machinery.
 *
 * <p>It watches the native {@code com.byd.dishare/.app.ui.ShareDialogActivity}
 * window, reads its live node bounds via {@link SimulcastDialogGeometry}, and
 * paints the Russian app row exactly over the native row so it looks native. It
 * shows/hides in lock-step with the real window (open/close/move/resize), which
 * is what the accessibility spike proved works on this firmware.
 *
 * <p>Touch model that keeps the native dialog usable: a full-screen
 * {@link DrawView} window does all painting but is {@code FLAG_NOT_TOUCHABLE}
 * (every touch passes through to the native dialog, so App Change / close /
 * screen cards keep working). Input is captured only by small per-slot
 * {@link SlotView} windows sitting on the Russian icons, so a drag of a Russian
 * icon is intercepted while everything else stays native. Launch reuses the
 * proven {@link SimulcastOverlayService} bridge path.
 */
public class SimulcastAccessibilityService extends AccessibilityService {
    private static final String TAG = "DenzaSimulcastA11y";
    private static final String DISHARE_PKG = "com.byd.dishare";
    /** Source size for the share so the app renders at native resolution, not 1024x576. */
    private static final int SHARE_VIDEO_WIDTH = 2560;
    private static final int SHARE_VIDEO_HEIGHT = 1440;

    // Native row colours from decompiled DiShare (night theme): the dialog body is an
    // opaque #15181f; the row sits on a 70%-alpha #373c49 rounded panel over it. We
    // erase the native panel with the body colour, then redraw the panel + our icons,
    // which reproduces the native look for any number of selected apps.
    private static final int DIALOG_BG = Color.rgb(0x15, 0x18, 0x1f);
    private static final int ROW_PANEL = Color.argb(0xb3, 0x37, 0x3c, 0x49);
    private static final float ROW_CORNER_DP = 20f;
    /** Rounded-icon corner radius as a fraction of icon size (squircle-ish, native look). */
    private static final float ICON_CORNER_RATIO = 0.22f;
    private static final int ICON_BITMAP_PX = 256;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.Map<String, Target> targetCache = new java.util.HashMap<>();
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix iconMatrix = new Matrix();
    private WindowManager windowManager;
    private BroadcastReceiver debugReceiver;
    private DrawView drawView;
    private final List<SlotView> slotViews = new ArrayList<>();

    private SimulcastDialogGeometry geometry;
    private final List<Slot> slots = new ArrayList<>();
    /** Our centered row panel + the region to erase the native row, recomputed per layout. */
    private Rect panelBounds;
    private Rect eraseBounds;
    /** Slot index that gets the native-style selection ring (active share, else first). */
    private int selectedSlot;

    private boolean dragging;
    private Target dragTarget;
    private float dragX;
    private float dragY;
    private String hoverReceiver;

    private final Runnable refreshRunnable = this::refresh;

    @Override
    protected void onServiceConnected() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Log.i(TAG, "service connected");
        // Debug-only: render our overlay to a PNG so we can inspect the row composition
        // (icons/panel/centering) — the main display is FLAG_SECURE so screencap is black.
        debugReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("dev.denza.apps.DEBUG_DUMP_NODES".equals(intent.getAction())) {
                    dumpWindowNodes();
                    return;
                }
                if ("dev.denza.apps.DEBUG_SIMULATE_DROP".equals(intent.getAction())) {
                    // Exercise the real drop path (slot -> receiverAt -> startTarget) without
                    // injecting touch events, which DiShare's handler mishandles.
                    float x = intent.getFloatExtra("dropX", -1f);
                    float y = intent.getFloatExtra("dropY", -1f);
                    int slot = intent.getIntExtra("slot", 0);
                    if (x >= 0 && y >= 0 && slot >= 0 && slot < slots.size()) {
                        onDragStart(slots.get(slot).target, x, y);
                        onDragEnd(x, y);
                    } else {
                        Log.i(TAG, "simulate drop: bad args or no slots=" + slots.size());
                    }
                    return;
                }
                // Optional drag simulation for capturing the drag visuals offscreen.
                float dx = intent.getFloatExtra("dragX", -1f);
                float dy = intent.getFloatExtra("dragY", -1f);
                boolean simulated = dx >= 0 && dy >= 0 && !slots.isEmpty();
                if (simulated) {
                    dragging = true;
                    dragTarget = slots.get(0).target;
                    dragX = dx;
                    dragY = dy;
                    hoverReceiver = geometry == null ? null : geometry.receiverAt(dx, dy);
                }
                captureOverlay();
                if (simulated) {
                    cancelDrag();
                    if (drawView != null) {
                        drawView.invalidate();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("dev.denza.apps.DEBUG_CAPTURE_OVERLAY");
        filter.addAction("dev.denza.apps.DEBUG_DUMP_NODES");
        filter.addAction("dev.denza.apps.DEBUG_SIMULATE_DROP");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(debugReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(debugReceiver, filter);
        }
        // The dialog may already be open when we (re)connect — scan once.
        scheduleRefresh();
    }

    /** Debug: log clickable/identified nodes of every window, to find the caption maximize button. */
    private void dumpWindowNodes() {
        java.util.List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) {
            return;
        }
        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) {
                continue;
            }
            Rect wb = new Rect();
            w.getBoundsInScreen(wb);
            Log.i(TAG, "WINDOW pkg=" + root.getPackageName() + " bounds=" + wb);
            dumpNode(root, 0);
            root.recycle();
        }
    }

    private void dumpNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 12) {
            return;
        }
        CharSequence id = node.getViewIdResourceName();
        CharSequence desc = node.getContentDescription();
        if (node.isClickable() || (id != null && id.length() > 0) || (desc != null && desc.length() > 0)) {
            Rect b = new Rect();
            node.getBoundsInScreen(b);
            Log.i(TAG, "  ".concat(new String(new char[depth]).replace('\0', ' '))
                    + "node id=" + id + " desc=" + desc + " cls=" + node.getClassName()
                    + " click=" + node.isClickable() + " " + b);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNode(node.getChild(i), depth + 1);
        }
    }

    private void captureOverlay() {
        if (drawView == null || drawView.getWidth() == 0 || drawView.getHeight() == 0) {
            Log.i(TAG, "capture: no overlay to render");
            return;
        }
        Bitmap bmp = Bitmap.createBitmap(drawView.getWidth(), drawView.getHeight(),
                Bitmap.Config.ARGB_8888);
        drawView.draw(new Canvas(bmp));
        java.io.File out = new java.io.File(getFilesDir(), "overlay.png");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Log.i(TAG, "capture saved " + out + " slots=" + slots.size());
        } catch (java.io.IOException e) {
            Log.w(TAG, "capture failed", e);
        } finally {
            bmp.recycle();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        // The App Change row appears as a content change inside the same window, so
        // we must react to content changes too. Coalesce bursts with a short debounce.
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 60L);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (debugReceiver != null) {
            try {
                unregisterReceiver(debugReceiver);
            } catch (RuntimeException ignored) {
            }
            debugReceiver = null;
        }
        tearDown();
        super.onDestroy();
    }

    private void refresh() {
        AccessibilityNodeInfo root = findDiShareRoot();
        // Mid-drag: keep the current overlay; only a vanished dialog cancels it.
        if (dragging) {
            if (root == null) {
                cancelDrag();
                tearDown();
            } else {
                root.recycle();
            }
            return;
        }
        if (!SimulcastIntegration.isEnabled(this)) {
            if (root != null) {
                root.recycle();
            }
            tearDown();
            return;
        }
        SimulcastDialogGeometry geo = SimulcastDialogGeometry.from(root);
        if (root != null) {
            root.recycle();
        }
        if (geo == null) {
            tearDown();
            return;
        }
        // Ignore the window events our own overlay windows generate: only re-apply
        // when DiShare's actual geometry changed. This prevents an add/remove loop.
        if (drawView != null && geo.sameAs(geometry)) {
            return;
        }
        Log.i(TAG, "apply " + geo);
        geometry = geo;
        rebuildSlots(geo);
        ensureViews();
        layoutSlotViews();
        if (drawView != null) {
            drawView.invalidate();
        }
    }

    private AccessibilityNodeInfo findDiShareRoot() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) {
            return null;
        }
        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) {
                continue;
            }
            CharSequence pkg = root.getPackageName();
            if (pkg != null && DISHARE_PKG.contentEquals(pkg)) {
                return root;
            }
            root.recycle();
        }
        return null;
    }

    /**
     * Lay out the user's selected apps as a centered row, native-sized, and compute
     * the panel + erase regions. We center N icons (instead of mapping onto the native
     * slots) and erase the native row underneath, so a 3-app selection looks like a
     * native 3-app row rather than 3 of 5 stock slots.
     */
    private void rebuildSlots(SimulcastDialogGeometry geo) {
        slots.clear();
        panelBounds = null;
        eraseBounds = null;
        if (!geo.isAppPickerOpen() || geo.appList == null) {
            return;
        }
        List<String> selected = SimulcastApps.getSelected(this);
        int count = Math.min(geo.appSlots.size(), selected.size());
        if (count == 0) {
            return;
        }
        Rect ref = geo.appSlots.get(0);
        int size = ref.width();
        int top = ref.top;
        int pitch = geo.appSlots.size() >= 2
                ? geo.appSlots.get(1).left - ref.left
                : Math.round(size * 1.18f);
        int gap = Math.max(pitch - size, 0);
        int totalWidth = count * size + (count - 1) * gap;
        int left = geo.appList.centerX() - totalWidth / 2;
        for (int i = 0; i < count; i++) {
            Target target = targetFor(selected.get(i));
            Rect bounds = new Rect(left, top, left + size, top + size);
            slots.add(new Slot(target, bounds));
            left += size + gap;
        }
        int padX = Math.round(size * 0.12f);
        int padY = Math.round(size * 0.18f);
        Rect first = slots.get(0).bounds;
        Rect last = slots.get(slots.size() - 1).bounds;
        panelBounds = new Rect(first.left - padX, first.top - padY,
                last.right + padX, first.bottom + padY);
        eraseBounds = new Rect(panelBounds);
        eraseBounds.union(geo.appList);
        eraseBounds.inset(-dp(24), -dp(16));

        // Native shows a selection ring on the active source app; ring our active share
        // if it's in the row, otherwise the first slot, so one icon is always ringed.
        selectedSlot = 0;
        String active = SimulcastIntegration.getLastTargetPackage(this);
        if (active != null) {
            for (int i = 0; i < slots.size(); i++) {
                if (active.equals(slots.get(i).target.packageName)) {
                    selectedSlot = i;
                    break;
                }
            }
        }
    }

    private Target targetFor(String packageName) {
        Target cached = targetCache.get(packageName);
        if (cached != null) {
            return cached;
        }
        BitmapShader shader = null;
        Drawable icon = loadIcon(packageName);
        if (icon != null) {
            Bitmap bmp = Bitmap.createBitmap(ICON_BITMAP_PX, ICON_BITMAP_PX,
                    Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, ICON_BITMAP_PX, ICON_BITMAP_PX);
            icon.draw(new Canvas(bmp));
            shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        Target target = new Target(packageName, loadLabel(packageName), shader);
        targetCache.put(packageName, target);
        return target;
    }

    private void ensureViews() {
        if (drawView != null) {
            return;
        }
        drawView = new DrawView(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(drawView, params);
        } catch (RuntimeException e) {
            Log.w(TAG, "add draw overlay failed", e);
            drawView = null;
        }
    }

    /** Recreate the small touch windows to match the current Russian slots. */
    private void layoutSlotViews() {
        removeSlotViews();
        if (windowManager == null) {
            return;
        }
        for (Slot slot : slots) {
            SlotView view = new SlotView(this, slot.target);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    slot.bounds.width(),
                    slot.bounds.height(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = slot.bounds.left;
            params.y = slot.bounds.top;
            try {
                windowManager.addView(view, params);
                slotViews.add(view);
            } catch (RuntimeException e) {
                Log.w(TAG, "add slot window failed", e);
            }
        }
    }

    private void removeSlotViews() {
        for (SlotView v : slotViews) {
            try {
                windowManager.removeView(v);
            } catch (RuntimeException ignored) {
            }
        }
        slotViews.clear();
    }

    private void tearDown() {
        removeSlotViews();
        if (drawView != null) {
            try {
                windowManager.removeView(drawView);
            } catch (RuntimeException ignored) {
            }
            drawView = null;
        }
        slots.clear();
        geometry = null;
        dragging = false;
        dragTarget = null;
        hoverReceiver = null;
    }

    // ----- drag handling, called from SlotView -----

    private void onDragStart(Target target, float rawX, float rawY) {
        dragging = true;
        dragTarget = target;
        dragX = rawX;
        dragY = rawY;
        hoverReceiver = geometry == null ? null : geometry.receiverAt(rawX, rawY);
        if (drawView != null) {
            drawView.invalidate();
        }
    }

    private void onDragMove(float rawX, float rawY) {
        dragX = rawX;
        dragY = rawY;
        hoverReceiver = geometry == null ? null : geometry.receiverAt(rawX, rawY);
        if (drawView != null) {
            drawView.invalidate();
        }
    }

    private void onDragEnd(float rawX, float rawY) {
        String receiver = geometry == null ? null : geometry.receiverAt(rawX, rawY);
        Target target = dragTarget;
        cancelDrag();
        if (receiver != null && target != null) {
            Log.i(TAG, "drop " + target.packageName + " -> " + receiver);
            SimulcastOverlayService.startTarget(this, target.packageName, receiver,
                    SHARE_VIDEO_WIDTH, SHARE_VIDEO_HEIGHT);
            tearDown();
        } else if (drawView != null) {
            drawView.invalidate();
        }
    }

    private void cancelDrag() {
        dragging = false;
        dragTarget = null;
        hoverReceiver = null;
    }

    private Drawable loadIcon(String packageName) {
        try {
            return getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private String loadLabel(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void drawIcon(Canvas canvas, Paint fill, Paint text, Target target, RectF bounds,
            float alpha) {
        float radius = bounds.width() * ICON_CORNER_RATIO;
        if (target.shader != null) {
            // Draw the app icon clipped to a rounded square (anti-aliased) so full-bleed
            // launcher icons look native instead of hard squares.
            iconMatrix.reset();
            float scale = bounds.width() / (float) ICON_BITMAP_PX;
            iconMatrix.setScale(scale, scale);
            iconMatrix.postTranslate(bounds.left, bounds.top);
            target.shader.setLocalMatrix(iconMatrix);
            iconPaint.setShader(target.shader);
            iconPaint.setAlpha(Math.round(alpha * 255f));
            canvas.drawRoundRect(bounds, radius, radius, iconPaint);
            iconPaint.setShader(null);
            return;
        }
        int save = canvas.saveLayerAlpha(bounds, Math.round(alpha * 255f));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(38, 50, 64));
        canvas.drawRoundRect(bounds, radius, radius, fill);
        text.setColor(Color.WHITE);
        text.setTextSize(bounds.height() * 0.45f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = bounds.centerY() - (fm.ascent + fm.descent) / 2f;
        String letter = target.label.isEmpty()
                ? "?" : target.label.substring(0, 1).toUpperCase();
        canvas.drawText(letter, bounds.centerX(), baseline, text);
        canvas.restoreToCount(save);
    }

    private static final class Target {
        final String packageName;
        final String label;
        final BitmapShader shader;

        Target(String packageName, String label, BitmapShader shader) {
            this.packageName = packageName;
            this.label = label;
            this.shader = shader;
        }
    }

    private static final class Slot {
        final Target target;
        final Rect bounds;

        Slot(Target target, Rect bounds) {
            this.target = target;
            this.bounds = bounds;
        }
    }

    /** Full-screen painter. Never touchable, so the native dialog stays usable. */
    private final class DrawView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

        DrawView(Context context) {
            super(context);
            setWillNotDraw(false);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Replace the native row: erase it with the dialog body colour, then redraw
            // the rounded panel sized to our selection so it reads as a native N-app row.
            if (!slots.isEmpty() && eraseBounds != null && panelBounds != null) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(DIALOG_BG);
                canvas.drawRect(new RectF(eraseBounds), fill);
                fill.setColor(ROW_PANEL);
                float r = dp(ROW_CORNER_DP);
                canvas.drawRoundRect(new RectF(panelBounds), r, r, fill);
            }
            // Drop-zone hints while dragging.
            if (dragging && geometry != null) {
                drawHint(canvas, geometry.hud, "screen_hud".equals(hoverReceiver));
                drawHint(canvas, geometry.fse, "screen_fse".equals(hoverReceiver));
            }
            // Selected app icons in our row.
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                RectF b = new RectF(slot.bounds);
                float alpha = (dragging && slot.target == dragTarget) ? 0.35f : 1f;
                drawIcon(canvas, fill, text, slot.target, b, alpha);
                if (i == selectedSlot && !(dragging && slot.target == dragTarget)) {
                    drawSelectionRing(canvas, b);
                }
            }
            // Floating dragged icon.
            if (dragging && dragTarget != null) {
                float size = dp(86);
                RectF b = new RectF(dragX - size / 2f, dragY - size / 2f,
                        dragX + size / 2f, dragY + size / 2f);
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Color.argb(70, 0, 0, 0));
                canvas.drawCircle(dragX + dp(3), dragY + dp(5), size * 0.55f, fill);
                drawIcon(canvas, fill, text, dragTarget, b, 0.96f);
            }
        }

        private void drawSelectionRing(Canvas canvas, RectF iconBounds) {
            RectF ring = new RectF(iconBounds);
            ring.inset(-dp(4), -dp(4));
            float r = ring.width() * ICON_CORNER_RATIO;
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(dp(2.5f));
            fill.setColor(Color.argb(203, 69, 162, 255));
            canvas.drawRoundRect(ring, r, r, fill);
            fill.setStyle(Paint.Style.FILL);
        }

        private void drawHint(Canvas canvas, Rect target, boolean active) {
            if (target == null) {
                return;
            }
            RectF b = new RectF(target);
            b.inset(-dp(10), -dp(10));
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(active ? Color.argb(70, 31, 194, 142) : Color.argb(20, 255, 255, 255));
            canvas.drawRoundRect(b, dp(14), dp(14), fill);
            if (active) {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(dp(2));
                fill.setColor(Color.argb(210, 31, 194, 142));
                canvas.drawRoundRect(b, dp(14), dp(14), fill);
            }
        }
    }

    /** Small transparent input window over one Russian icon; owns its drag gesture. */
    private final class SlotView extends View {
        private final Target target;

        SlotView(Context context, Target target) {
            super(context);
            this.target = target;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    onDragStart(target, event.getRawX(), event.getRawY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        onDragMove(event.getRawX(), event.getRawY());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        onDragEnd(event.getRawX(), event.getRawY());
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelDrag();
                    if (drawView != null) {
                        drawView.invalidate();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
