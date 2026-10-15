package com.operit.chrometablist;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Chrome Tab List compact mode.
 *
 * Two modifications to Chrome's tab switcher grid:
 * 1. Hide thumbnails, giving a compact list.
 * 2. Center the title area vertically in each item.
 *
 * Hooks ViewGroup.addView to find the RecyclerView by its resource ID, then
 * applies layout/style patches that persist across scrolling via an
 * OnLayoutChangeListener.
 */
public class ModuleMain extends XposedModule {

    private static final String TAG = "ChromeTabList";
    private static final String CHROME_PKG = "com.android.chrome";
    private static final String TAB_RV_ID_NAME = "tab_list_recycler_view";

    /** Delay before patching a newly found RecyclerView, so it has laid out. */
    private static final long PATCH_DELAY_MS = 200;
    /** Compact height of each tab item. */
    private static final int ITEM_HEIGHT_DP = 72;
    /** Title text size. */
    private static final float TITLE_TEXT_SIZE_SP = 14;

    /** RecyclerView instances already patched (weak, so Chrome can free them). */
    private final Set<ViewGroup> mPatchedViews =
        Collections.newSetFromMap(new WeakHashMap<>());
    /** Error tags already logged, so a failure on every addView/layout is logged once. */
    private final Set<String> mLoggedErrors = new HashSet<>();
    /** Created lazily: the main Looper is guaranteed to exist once views are being added. */
    private Handler mMainHandler;

    private int mTargetRvId = 0;
    private boolean mResolved = false;
    private int mThumbnailId = 0;
    private int mTitleId = 0;
    private int mContentViewId = 0;

    private boolean mModifying = false;
    private int mLastWidth = -1;
    private int mLastCount = -1;

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!CHROME_PKG.equals(param.getPackageName())) return;

        logInfo("API 101 module loaded in " + param.getPackageName());

        try {
            Method addViewMethod = ViewGroup.class.getDeclaredMethod(
                "addView",
                View.class,
                Integer.TYPE,
                ViewGroup.LayoutParams.class
            );
            hook(addViewMethod)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object child = chain.getArg(0);
                    if (child instanceof View) {
                        checkForTarget((View) child);
                    }
                    return result;
                });
            logInfo("addView hook active");
        } catch (Throwable t) {
            logError("addView hook error", t);
        }
    }

    /**
     * Check if the given view (or one of its descendants) is Chrome's
     * tab_list_recycler_view. If found and not yet patched, schedule a patch.
     */
    private void checkForTarget(View view) {
        try {
            if (!(view instanceof ViewGroup)) return;

            // Resolve the resource ID lazily.
            if (mTargetRvId == 0) {
                mTargetRvId = view.getResources().getIdentifier(
                    TAB_RV_ID_NAME, "id", CHROME_PKG);
                if (mTargetRvId == 0) return;
                logInfo("target ID = " + mTargetRvId);
            }

            View found = view.getId() == mTargetRvId
                ? view
                : view.findViewById(mTargetRvId);
            if (!(found instanceof ViewGroup)) return;

            final ViewGroup rv = (ViewGroup) found;
            if (mPatchedViews.add(rv)) {
                logInfo("FOUND! patching...");
                if (mMainHandler == null) mMainHandler = new Handler(Looper.getMainLooper());
                mMainHandler.postDelayed(() -> patchRecyclerView(rv), PATCH_DELAY_MS);
            }
        } catch (Throwable t) {
            logErrorOnce("checkForTarget error", t);
        }
    }

    /**
     * Apply initial styling and install a LayoutChangeListener to keep the
     * styling applied when items are recycled during scrolling.
     */
    private void patchRecyclerView(final ViewGroup rv) {
        try {
            logInfo("patch w=" + rv.getWidth() + " h=" + rv.getHeight());
            mLastWidth = -1;
            mLastCount = -1;

            // Style existing children.
            modifyChildren(rv, true);

            // Keep styling applied on layout changes (scroll, resize).
            rv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (mModifying) return;
                int width = r - l;
                int count = rv.getChildCount();
                if (width == mLastWidth && count == mLastCount) return;
                mLastWidth = width;
                mLastCount = count;
                mModifying = true;
                modifyChildren(rv, false);
                rv.post(() -> mModifying = false);
            });

            logInfo("LayoutChangeListener ok");
        } catch (Throwable t) {
            logError("patch error", t);
        }
    }

    /** Apply {@link #modifyItem} to every current child of the RecyclerView. */
    private void modifyChildren(ViewGroup rv, boolean logCount) {
        try {
            int count = rv.getChildCount();
            for (int i = 0; i < count; i++) modifyItem(rv.getChildAt(i));
            if (logCount) logInfo("Modified " + count + " children");
        } catch (Throwable t) {
            logErrorOnce("modifyChildren error", t);
        }
    }

    /**
     * Modify a single tab item view:
     * - Full width, compact height
     * - Hide thumbnail (GONE)
     * - Shrink content_view to WRAP_CONTENT and center it vertically
     * - Style title: single line, fixed text size
     */
    private void modifyItem(View itemView) {
        if (itemView == null) return;
        try {
            resolveResources(itemView.getContext());

            // Size: full width, compact height.
            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
            if (lp != null) {
                boolean changed = false;
                if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    changed = true;
                }
                int targetHeight = dpToPx(ITEM_HEIGHT_DP);
                if (lp.height != targetHeight) {
                    lp.height = targetHeight;
                    changed = true;
                }
                if (changed) itemView.setLayoutParams(lp);
            }

            // Hide thumbnail.
            View thumb = findById(itemView, mThumbnailId);
            if (thumb != null && thumb.getVisibility() != View.GONE) {
                thumb.setVisibility(View.GONE);
            }

            // Wrap content_view's height and center it in its parent FrameLayout.
            View contentView = findById(itemView, mContentViewId);
            if (contentView != null) {
                ViewGroup.LayoutParams cvlp = contentView.getLayoutParams();
                if (cvlp != null) {
                    cvlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    if (cvlp instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) cvlp).gravity = Gravity.CENTER;
                    }
                    contentView.setLayoutParams(cvlp);
                }
            }

            // Title: single line, fixed size.
            View title = findById(itemView, mTitleId);
            if (title instanceof TextView) {
                TextView tv = (TextView) title;
                tv.setMaxLines(1);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP);
            }
        } catch (Throwable t) {
            logErrorOnce("modifyItem error", t);
        }
    }

    /** Resolve Chrome's internal view IDs (tab_thumbnail, tab_title, content_view) once. */
    private void resolveResources(Context ctx) {
        if (mResolved) return;
        try {
            Resources res = ctx.getResources();
            mThumbnailId = res.getIdentifier("tab_thumbnail", "id", CHROME_PKG);
            mTitleId = res.getIdentifier("tab_title", "id", CHROME_PKG);
            mContentViewId = res.getIdentifier("content_view", "id", CHROME_PKG);
            mResolved = true;
            logInfo("Resolved IDs: thumbnail=" + mThumbnailId
                + " title=" + mTitleId + " contentView=" + mContentViewId);
        } catch (Throwable t) {
            logError("resolveResources error", t);
        }
    }

    /** findViewById that tolerates an unresolved (0) ID. */
    private static View findById(View root, int id) {
        return id != 0 ? root.findViewById(id) : null;
    }

    /** Convert dp to px using system display metrics. */
    private static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density + 0.5f);
    }

    private void logInfo(String msg) {
        log(Log.INFO, TAG, msg);
    }

    private void logError(String msg, Throwable tr) {
        log(Log.ERROR, TAG, msg, tr);
    }

    /** Log an error only the first time it happens, for code that runs very often. */
    private void logErrorOnce(String msg, Throwable tr) {
        synchronized (mLoggedErrors) {
            if (!mLoggedErrors.add(msg)) return;
        }
        logError(msg, tr);
    }
}
