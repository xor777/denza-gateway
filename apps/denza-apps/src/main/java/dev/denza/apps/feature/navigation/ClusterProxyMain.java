package dev.denza.apps.feature.navigation;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * One-shot shell-UID task command. It intentionally exposes only fixed
 * operations for the allowlisted Yandex Navigator task and always exits.
 */
public final class ClusterProxyMain {
    private static final String ALLOWED_PACKAGE = "ru.yandex.yandexnavi";
    private static final String RESULT_PREFIX = "DENZA_RESULT:";

    private ClusterProxyMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("operation required");
        Looper.prepareMainLooper();
        Commands commands = new Commands(systemContext());
        switch (args[0]) {
            case "find-task":
                requireCount(args, 2);
                result(commands.findAllowedTask(args[1]));
                return;
            case "move-task":
                requireCount(args, 3);
                result(commands.moveTask(integer(args[1]), integer(args[2])));
                return;
            case "set-bounds":
                requireCount(args, 6);
                result(commands.setTaskBounds(
                        integer(args[1]), integer(args[2]), integer(args[3]),
                        integer(args[4]), integer(args[5])));
                return;
            case "focus-task":
                requireCount(args, 2);
                result(commands.focusTask(integer(args[1])));
                return;
            case "task-display":
                requireCount(args, 2);
                result(commands.taskDisplayId(integer(args[1])));
                return;
            default:
                throw new IllegalArgumentException("unsupported operation");
        }
    }

    private static Context systemContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getDeclaredMethod("systemMain").invoke(null);
        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        return (Context) getSystemContext.invoke(activityThread);
    }

    private static void requireCount(String[] args, int expected) {
        if (args.length != expected) throw new IllegalArgumentException("invalid argument count");
    }

    private static int integer(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new IllegalArgumentException("negative integer");
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid integer", error);
        }
    }

    private static void result(Object value) {
        System.out.println(RESULT_PREFIX + value);
    }

    private static final class Commands {
        private final Context context;

        Commands(Context context) {
            this.context = context;
        }

        int findAllowedTask(String packageName) {
            if (!ALLOWED_PACKAGE.equals(packageName)) return -1;
            for (ActivityManager.RunningTaskInfo task : tasks()) {
                if (belongsToAllowedPackage(task)) return task.taskId;
            }
            return -1;
        }

        boolean moveTask(int taskId, int displayId) {
            enforceTask(taskId);
            return invokeTaskManager(
                    new String[] {"moveRootTaskToDisplay", "moveTaskToDisplay", "moveStackToDisplay"},
                    new Class<?>[] {int.class, int.class},
                    taskId,
                    displayId);
        }

        boolean setTaskBounds(int taskId, int left, int top, int right, int bottom) {
            enforceTask(taskId);
            boolean clear = left == 0 && top == 0 && right == 0 && bottom == 0;
            if (!clear && (right <= left || bottom <= top)) {
                throw new IllegalArgumentException("invalid bounds");
            }
            Rect bounds = clear ? null : new Rect(left, top, right, bottom);
            return invokeTaskManager(
                    new String[] {"resizeTask"},
                    new Class<?>[] {int.class, Rect.class, int.class},
                    taskId,
                    bounds,
                    0);
        }

        boolean focusTask(int taskId) {
            enforceTask(taskId);
            return invokeTaskManager(
                    new String[] {"setFocusedTask"},
                    new Class<?>[] {int.class},
                    taskId);
        }

        int taskDisplayId(int taskId) {
            enforceTask(taskId);
            for (ActivityManager.RunningTaskInfo task : tasks()) {
                if (task.taskId == taskId && belongsToAllowedPackage(task)) {
                    return displayIdOf(task);
                }
            }
            return -1;
        }

        private List<ActivityManager.RunningTaskInfo> tasks() {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            return manager == null ? java.util.Collections.emptyList() : manager.getRunningTasks(100);
        }

        private boolean belongsToAllowedPackage(ActivityManager.RunningTaskInfo task) {
            return hasPackage(task.topActivity) || hasPackage(task.baseActivity);
        }

        private boolean hasPackage(ComponentName component) {
            return component != null && ALLOWED_PACKAGE.equals(component.getPackageName());
        }

        private int displayIdOf(ActivityManager.RunningTaskInfo task) {
            try {
                Field field = task.getClass().getField("displayId");
                return field.getInt(task);
            } catch (ReflectiveOperationException ignored) {
                try {
                    Field configurationField = task.getClass().getField("configuration");
                    Object configuration = configurationField.get(task);
                    Field windowConfiguration = configuration.getClass().getField("windowConfiguration");
                    Object value = windowConfiguration.get(configuration);
                    Method getDisplayId = value.getClass().getMethod("getDisplayId");
                    return (Integer) getDisplayId.invoke(value);
                } catch (ReflectiveOperationException | RuntimeException nested) {
                    return -1;
                }
            }
        }

        private void enforceTask(int taskId) {
            for (ActivityManager.RunningTaskInfo task : tasks()) {
                if (task.taskId == taskId && belongsToAllowedPackage(task)) return;
            }
            throw new SecurityException("task is not an allowed Yandex task");
        }

        private boolean invokeTaskManager(String[] names, Class<?>[] parameterTypes, Object... args) {
            try {
                Class<?> managerClass = Class.forName("android.app.ActivityTaskManager");
                Object service = managerClass.getDeclaredMethod("getService").invoke(null);
                for (String name : names) {
                    try {
                        Method method = service.getClass().getMethod(name, parameterTypes);
                        method.setAccessible(true);
                        Object result = method.invoke(service, args);
                        return !(result instanceof Boolean) || (Boolean) result;
                    } catch (NoSuchMethodException ignored) {
                        // Try the equivalent method name used by another Android generation.
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
            return false;
        }
    }
}
