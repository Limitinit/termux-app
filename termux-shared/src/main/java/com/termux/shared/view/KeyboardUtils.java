package com.termux.shared.view;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class KeyboardUtils {

    /**
     * Toggle the soft keyboard. The {@link InputMethodManager#SHOW_FORCED} is passed as
     * {@code showFlags} so that keyboard is forcefully shown if it needs to be enabled.
     */
    public static void toggleSoftKeyboard(final Context context) {
        if (context == null) return;
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    /**
     * Show the soft keyboard. The {@code 0} value is passed as {@code flags} so that keyboard is
     * forcefully shown.
     *
     * This is also important for soft keyboard to be shown on app startup when a hardware keyboard
     * is connected, and user has disabled the {@code Show on-screen keyboard while hardware keyboard
     * is connected} toggle in Android "Language and Input" settings but the current soft keyboard app
     * overrides the default implementation of {@link InputMethodService#onEvaluateInputViewShown()}
     * and returns {@code true}.
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:frameworks/base/core/java/android/inputmethodservice/InputMethodService.java;l=1751
     *
     * Also check {@link InputMethodService#onShowInputRequested(int, boolean)} which must return
     * {@code true}, which can be done by failing its {@code ((flags&InputMethod.SHOW_EXPLICIT) == 0)}
     * check by passing {@code 0} as {@code flags}.
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:frameworks/base/core/java/android/inputmethodservice/InputMethodService.java;l=2022
     */
    public static void showSoftKeyboard(final Context context, final View view) {
        if (context == null || view == null) return;
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.showSoftInput(view, 0);
    }

    public static void hideSoftKeyboard(final Context context, final View view) {
        if (context == null || view == null) return;
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

}
