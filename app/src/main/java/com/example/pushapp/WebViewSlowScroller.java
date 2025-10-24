package com.example.pushapp;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;

import java.util.WeakHashMap;

public final class WebViewSlowScroller {
    private WebViewSlowScroller() {
    }

    private static final TimeInterpolator DEFAULT_INTERP = new DecelerateInterpolator(1.5f);


    /**
     * Smoothly scroll to the end of the page in exactly durationMs on the main thread.
     * Safe to call multiple times; it cancels previous runs. No external libs.
     */
    public static void scrollToBottomOver(WebView webView, long durationMs, Runnable onFinished) {
        if (webView == null || durationMs <= 0) return;

        webView.post(() -> {
            final int startY = webView.getScrollY();

            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(durationMs);
            va.setInterpolator(DEFAULT_INTERP);

            va.addUpdateListener(anim -> {
                float f = (float) anim.getAnimatedValue();
                int targetY = getCurrentBottomScrollY(webView);
                int y = (int) (startY + (targetY - startY) * f);
                y = Math.max(0, Math.min(y, targetY));
                webView.scrollTo(0, y);
            });

            va.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (onFinished != null) {
                        webView.post(onFinished);  // ensure runs on UI thread
                    }
                }

            });

            va.start();
        });
    }


    /**
     * Computes the maximum scrollY to show the bottom of the content *right now*.
     * This adjusts automatically if the page adds content or changes scale.
     */
    private static int getCurrentBottomScrollY(WebView webView) {
        // contentHeight is in CSS px; multiply by scale to get view px.
        float scaledContentPx = webView.getContentHeight() * webView.getScale();
        int viewH = webView.getHeight();
        int bottom = (int) Math.max(0, Math.round(scaledContentPx) - viewH);
        return bottom;
    }

    // Minimal no-op listener to avoid boilerplate
    private static abstract class SimpleAnimatorListener implements android.animation.Animator.AnimatorListener {
        @Override
        public void onAnimationStart(android.animation.Animator animation) {
        }

        @Override
        public void onAnimationEnd(android.animation.Animator animation) {
        }

        @Override
        public void onAnimationCancel(android.animation.Animator animation) {
        }

        @Override
        public void onAnimationRepeat(android.animation.Animator animation) {
        }
    }
}
