package app.orange.tv.checkup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lists every Home candidate on the TV, says whether `su` works, and can disable one with it.
 *
 * <p>Written for a box whose firmware puts its own launcher back no matter what the viewer picks.
 * A launcher that ships in /system and holds the platform signature can re-register itself as the
 * default Home, so the picker's "Always" is undone within seconds; and "uninstall" of a /system app
 * only removes its updates. Disabling the package is the one thing it cannot undo.
 *
 * <p>Plain Java, no support library, no lambdas: this has to run on Dalvik 4.x.
 */
public class CheckupActivity extends Activity {

    private static final int BG = 0xFF101014;
    private static final int CARD = 0xFF1C1C22;
    private static final int PRIMARY = 0xFFF2F2F7;
    private static final int SECONDARY = 0xFF9A9AA2;
    private static final int GOOD = 0xFF34C759;
    private static final int BAD = 0xFFFF453A;
    private static final int ACCENT = 0xFFFF8A1F;

    /** Lite's entry, started after a disable so a black screen is not a dead end. */
    private static final String LITE_PKG = "app.orange.tv.lite";
    private static final String LITE_ACT = "app.orange.tv.lite.HomeActivity";

    private LinearLayout column;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private static class Candidate {
        String pkg;
        String cls;
        String label;
        int priority;
        boolean system;
        boolean enabled;
        boolean self;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(36);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        column.addView(title("Orange Checkup"));
        column.addView(note("正在读取桌面列表和 root 状态……", SECONDARY));
        new Thread(new Runnable() {
            public void run() {
                collect();
            }
        }).start();
    }

    // ---- gathering -------------------------------------------------------------------------

    private void collect() {
        final PackageManager pm = getPackageManager();
        final Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);

        final List<Candidate> found = new ArrayList<Candidate>();
        List<ResolveInfo> ris;
        try {
            ris = pm.queryIntentActivities(home, 0);
        } catch (Throwable t) {
            ris = new ArrayList<ResolveInfo>();
        }
        for (int i = 0; i < ris.size(); i++) {
            ResolveInfo ri = ris.get(i);
            if (ri == null || ri.activityInfo == null) continue;
            Candidate c = new Candidate();
            c.pkg = ri.activityInfo.packageName;
            c.cls = ri.activityInfo.name;
            c.priority = ri.priority;
            c.self = getPackageName().equals(c.pkg);
            try {
                c.label = ri.loadLabel(pm).toString();
            } catch (Throwable t) {
                c.label = c.pkg;
            }
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            c.system = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            c.enabled = ri.activityInfo.enabled && (ai == null || ai.enabled);
            found.add(c);
        }
        Collections.sort(found, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                if (a.priority != b.priority) return b.priority - a.priority;
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        String current = "（系统没有指定默认桌面）";
        try {
            ResolveInfo cur = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (cur != null && cur.activityInfo != null) {
                String p = cur.activityInfo.packageName;
                // With no default, 4.x answers with the ResolverActivity itself.
                if (!"android".equals(p)) {
                    current = p + "  " + labelOf(pm, p);
                }
            }
        } catch (Throwable ignored) {
        }

        final String id = runWithTimeout(8000, "su", "-c", "id");
        final boolean rooted = id != null && id.indexOf("uid=0") >= 0;

        final String currentHome = current;
        final String suOut = id == null ? "(无输出)" : id;

        ui.post(new Runnable() {
            public void run() {
                render(found, currentHome, rooted, suOut);
            }
        });
    }

    private static String labelOf(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            return l == null ? "" : l.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Runs a command; null when it produced nothing at all. */
    private static String run(String... cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            StringBuilder sb = new StringBuilder();
            BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = out.readLine()) != null) sb.append(line).append('\n');
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = err.readLine()) != null) sb.append(line).append('\n');
            return sb.toString().trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * `Process.waitFor(timeout)` needs API 26, so the wait is done by hand: a Superuser prompt that
     * nobody answers must not leave the screen saying "checking" forever.
     */
    private static String runWithTimeout(long ms, final String... cmd) {
        final String[] box = new String[] { null };
        Thread t = new Thread(new Runnable() {
            public void run() {
                // Qualified: inside a Runnable, a bare `run(...)` is Runnable's own run().
                box[0] = CheckupActivity.run(cmd);
            }
        });
        t.start();
        try {
            t.join(ms);
        } catch (InterruptedException ignored) {
        }
        return t.isAlive() ? null : box[0];
    }

    // ---- drawing ---------------------------------------------------------------------------

    private void render(List<Candidate> list, String current, boolean rooted, String suOut) {
        column.removeAllViews();

        column.addView(title("Orange Checkup"));
        column.addView(note(Build.MANUFACTURER + " " + Build.MODEL + "  ·  Android "
            + Build.VERSION.RELEASE + "  (SDK " + Build.VERSION.SDK_INT + ")", SECONDARY));

        LinearLayout summary = card();
        summary.addView(row("root 权限", rooted ? "可用" : "不可用", rooted ? GOOD : BAD));
        summary.addView(row("当前默认桌面", current, PRIMARY));
        summary.addView(row("检测到的桌面数", String.valueOf(list.size()), PRIMARY));
        if (!rooted) {
            summary.addView(note("没有 root：不能从命令行停用，用下面的按钮跳到系统页面去停。", BAD));
        } else {
            summary.addView(note("su 返回：" + oneLine(suOut), SECONDARY));
        }
        column.addView(summary);

        column.addView(heading("桌面候选"));
        if (list.isEmpty()) {
            column.addView(note("一个也没查到。", SECONDARY));
        }
        for (int i = 0; i < list.size(); i++) {
            final Candidate c = list.get(i);
            LinearLayout box = card();
            box.addView(text(c.label, 20, PRIMARY, true));
            String meta = c.pkg + "\n" + (c.system ? "系统应用" : "普通应用")
                + "  ·  " + (c.enabled ? "已启用" : "已停用")
                + "  ·  priority " + c.priority;
            box.addView(note(meta, SECONDARY));
            if (c.self) {
                box.addView(note("（本工具自己，不停用）", SECONDARY));
            } else if (rooted) {
                Button b = button("停用这个桌面");
                b.setEnabled(c.enabled);
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        confirmDisable(c);
                    }
                });
                box.addView(b, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                // No su: the system's own "Disable" is the only switch that works, and the
                // firmware hides this app from its list. Jumping straight at the details page
                // skips the list, which is the whole point.
                Button open = button("打开它的系统详情页");
                open.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        openAppDetails(c.pkg);
                    }
                });
                box.addView(open, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                box.addView(note("进去后找「停用」。没有的话，点下面回到应用列表，"
                    + "再按遥控器菜单键显示系统应用。", SECONDARY));
                Button all = button("打开应用管理列表");
                all.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (!openAppList()) showCantOpen(c.pkg);
                    }
                });
                box.addView(all, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            column.addView(box);
        }

        Button again = button("重新检测");
        again.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                column.removeAllViews();
                column.addView(title("Orange Checkup"));
                column.addView(note("正在重新读取……", SECONDARY));
                new Thread(new Runnable() {
                    public void run() {
                        collect();
                    }
                }).start();
            }
        });
        column.addView(again);

        column.addView(note("停用成功后按遥控器 HOME 键试一下，应该直接回到 Orange Lite。"
            + "如果屏幕变黑，重新上电即可；这个工具不占 HOME，不会把自己也卡掉。", SECONDARY));
    }

    private void confirmDisable(final Candidate c) {
        new AlertDialog.Builder(this)
            .setTitle("停用「" + c.label + "」？")
            .setMessage("将执行：\nsu -c pm disable-user --user 0 " + c.pkg
                + "\n\n停用后这个桌面不会再被系统启动，也就不能再把 HOME 抢回去。"
                + "如果它正是现在的默认桌面，屏幕会先黑一下，工具会尝试立刻打开 Orange Lite。")
            .setPositiveButton("停用", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    disable(c);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void disable(final Candidate c) {
        new Thread(new Runnable() {
            public void run() {
                String out = runWithTimeout(15000, "su", "-c", "pm disable-user --user 0 " + c.pkg);
                if (out == null || out.indexOf("Error") >= 0 || out.indexOf("Exception") >= 0) {
                    String second = runWithTimeout(15000, "su", "-c", "pm disable " + c.pkg);
                    if (second != null && second.length() > 0) out = second;
                }
                final String result = out == null ? "(没有输出)" : out;
                // Bring Lite up by name: if the disabled package was Home, the screen is black now.
                try {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                    i.setClassName(LITE_PKG, LITE_ACT);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable ignored) {
                }
                ui.post(new Runnable() {
                    public void run() {
                        new AlertDialog.Builder(CheckupActivity.this)
                            .setTitle("结果")
                            .setMessage(result.length() == 0 ? "（命令没有输出，通常表示成功）" : result)
                            .setPositiveButton("好", null)
                            .show();
                    }
                });
            }
        }).start();
    }

    /**
     * The details page for one package, skipping the app list that the firmware filters.
     *
     * <p>Tried in order, because nothing here is guaranteed: Xiaomi's TV settings answers none of
     * the standard intents, while a plain AOSP 4.4 answers all of them. The 4.x class name is
     * spelled out as a second attempt for ROMs that never wired up the public action.
     */
    private void openAppDetails(String pkg) {
        Uri pkgUri = Uri.parse("package:" + pkg);

        Intent details = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(pkgUri);
        if (tryStart(details)) return;

        Intent direct = new Intent();
        direct.setComponent(new ComponentName("com.android.settings",
            "com.android.settings.applications.InstalledAppDetails"));
        direct.setData(pkgUri);
        direct.putExtra("pkg", pkg);
        if (tryStart(direct)) return;

        if (openAppList()) return;
        showCantOpen(pkg);
    }

    private boolean openAppList() {
        if (tryStart(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))) return true;
        if (tryStart(new Intent("android.settings.APPLICATION_SETTINGS"))) return true;
        return false;
    }

    private boolean tryStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void showCantOpen(String pkg) {
        new AlertDialog.Builder(this)
            .setTitle("这台电视的设置不接受跳转")
            .setMessage("包名：" + pkg + "\n\n手动找：设置 → 应用/应用管理，"
                + "在列表页按遥控器「菜单」键，勾上「显示系统应用」，再找「乐家桌面」。")
            .setPositiveButton("好", null)
            .show();
    }

    // ---- small view helpers ----------------------------------------------------------------

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
            getResources().getDisplayMetrics());
    }

    private TextView title(String s) {
        TextView t = text(s, 26, PRIMARY, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView heading(String s) {
        TextView t = text(s, 16, SECONDARY, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView note(String s, int color) {
        TextView t = text(s, 13, color, false);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private LinearLayout row(String k, String v, int color) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView key = text(k, 14, SECONDARY, false);
        key.setWidth(dp(150));
        r.addView(key);
        r.addView(text(v, 14, color, false), new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setPadding(0, dp(5), 0, dp(5));
        return r;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        box.setBackgroundDrawable(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        box.setLayoutParams(lp);
        return box;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(0xFFFFFFFF);
        b.setAllCaps(false);
        b.setPadding(dp(20), dp(10), dp(20), dp(10));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xFF2C2C36);
        normal.setCornerRadius(dp(10));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(ACCENT);
        focused.setCornerRadius(dp(10));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_focused }, focused);
        states.addState(new int[] { android.R.attr.state_pressed }, focused);
        states.addState(new int[] {}, normal);
        b.setBackgroundDrawable(states);
        return b;
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').trim();
    }
}
