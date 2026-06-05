package com.operit.chrometablist;

import android.content.Context;
import android.content.res.Resources;
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
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Chrome Tab List Mod
 * 
 * Two modifications to Chrome's tab switcher GridView:
 * 1. Hide thumbnails -> compact list mode
 * 2. Center title text vertically in each item
 * 
 * Hooks ViewGroup.addView to find the RecyclerView by its resource ID,
 * then applies layout/style patches that persist across scrolling via
 * OnLayoutChangeListener.
 */
public class ModuleMain extends XposedModule {

    private static final String TAG = "ChromeTabList";
    private static final String CHROME_PKG = "com.android.chrome";
    private static final String TAB_RV_ID_NAME = "tab_list_recycler_view";

    private final Set<ViewGroup> mPatchedViews =
        Collections.newSetFromMap(new WeakHashMap<>());
    private boolean mResolved = false;
    private int mThumbnailId = 0;
    private int mTitleId = 0;
    private int mContentViewId = 0;
    private boolean mModifying = false;
    private int mTargetRvid = 0;
    private int mLastW = -1, mLastCount = -1;

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
     * Check if the given view (or its descendants) contains Chrome's
     * tab_list_recycler_view. If found, schedule a patch.
     */
    private void checkForTarget(View view) {
        try {
            // Resolve the resource ID lazily
            if (mTargetRvid == 0) {
                mTargetRvid = view.getResources().getIdentifier(
                    TAB_RV_ID_NAME, "id", CHROME_PKG);
                if (mTargetRvid == 0) return;
                logInfo("target ID = " + mTargetRvid);
            }

            ViewGroup rv = null;
            if (view instanceof ViewGroup && view.getId() == mTargetRvid) {
                rv = (ViewGroup) view;
            } else if (view instanceof ViewGroup) {
                View found = ((ViewGroup) view).findViewById(mTargetRvid);
                if (found instanceof ViewGroup) rv = (ViewGroup) found;
            }
            if (rv != null && !mPatchedViews.contains(rv)) {
                logInfo("FOUND! patching...");
                mPatchedViews.add(rv);
                final ViewGroup finalRv = rv;
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> patchRecyclerView(finalRv), 200);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Apply initial styling and install a LayoutChangeListener to keep
     * styling applied when items are recycled during scrolling.
     */
    private void patchRecyclerView(final ViewGroup rv) {
        try {
            logInfo("patch w=" + rv.getWidth() + " h=" + rv.getHeight());
            mLastW = -1;
            mLastCount = -1;

            // Style existing children
            modifyExistingChildren(rv);

            // Keep styling applied on layout changes (scroll, resize)
            rv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (mModifying) return;
                int w = r - l, count = rv.getChildCount();
                if (w == mLastW && count == mLastCount) return;
                mLastW = w;
                mLastCount = count;
                mModifying = true;
                modifyExistingChildrenNoLog(rv);
                rv.post(() -> mModifying = false);
            });

            logInfo("LayoutChangeListener ok");
        } catch (Throwable t) {
            logError("patch error", t);
        }
    }

    private void modifyExistingChildren(ViewGroup rv) {
        try {
            int count = rv.getChildCount();
            for (int i = 0; i < count; i++) modifyItem(rv.getChildAt(i));
            logInfo("Modified " + count + " children");
        } catch (Throwable ignored) {}
    }

    private void modifyExistingChildrenNoLog(ViewGroup rv) {
        try {
            for (int i = 0; i < rv.getChildCount(); i++) modifyItem(rv.getChildAt(i));
        } catch (Throwable ignored) {}
    }

    /**
     * Modify a single tab item view:
     * - Set height to 72dp (compact)
     * - Hide thumbnail (GONE)
     * - Shrink content_view to WRAP_CONTENT and center it vertically
     * - Style title: single-line, 14sp
     */
    private void modifyItem(View itemView) {
        try {
            resolveResources(itemView.getContext());

            // --- SIZE: full width, 72dp height ---
            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
            if (lp != null) {
                boolean changed = false;
                if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    changed = true;
                }
                int targetH = dpToPx(72);
                if (lp.height != targetH) {
                    lp.height = targetH;
                    changed = true;
                }
                if (changed) itemView.setLayoutParams(lp);
            }

            // --- HIDE THUMBNAIL ---
            if (mThumbnailId != 0) {
                View thumb = itemView.findViewById(mThumbnailId);
                if (thumb != null && thumb.getVisibility() != View.GONE) {
                    thumb.setVisibility(View.GONE);
                }
            }

            // --- VERTICAL CENTERING of content_view ---
            if (mContentViewId != 0) {
                View cv = itemView.findViewById(mContentViewId);
                if (cv != null) {
                    // Shrink content_view height so it wraps its children
                    ViewGroup.LayoutParams cvlp = cv.getLayoutParams();
                    if (cvlp != null) {
                        cvlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        cv.setLayoutParams(cvlp);
                    }
                    // Center within the parent FrameLayout
                    ViewGroup.LayoutParams cvlp2 = cv.getLayoutParams();
                    if (cvlp2 instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) cvlp2).gravity = Gravity.CENTER;
                        cv.setLayoutParams(cvlp2);
                    }
                }
            }

            // --- TITLE STYLE: single line, 14sp ---
            if (mTitleId != 0) {
                View title = itemView.findViewById(mTitleId);
                if (title instanceof TextView) {
                    TextView tv = (TextView) title;
                    tv.setMaxLines(1);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Resolve Chrome's internal view IDs (tab_thumbnail, tab_title, content_view).
     */
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
        } catch (Throwable e) {
            logError("resolveResources error", e);
        }
    }

    /** Convert dp to px using system display metrics. */
    private int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density + 0.5f);
    }

    private void logInfo(String msg) {
        log(Log.INFO, TAG, msg);
    }

    private void logError(String msg, Throwable tr) {
        log(Log.ERROR, TAG, msg, tr);
    }
}
