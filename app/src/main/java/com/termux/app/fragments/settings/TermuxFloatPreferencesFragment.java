package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

// ИСПРАВЛЕНИЕ: Импорт вашего R класса
import com.kakdela.p2p.R;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;

@Keep
public class TermuxFloatPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxFloatPreferencesDataStore.getInstance(context));

        // Убедитесь, что файл termux_float_preferences.xml существует в res/xml
        setPreferencesFromResource(R.xml.termux_float_preferences, rootKey);
    }

    // Внутренний класс для хранения данных
    static class TermuxFloatPreferencesDataStore extends PreferenceDataStore {

        private final Context mContext;
        private final TermuxFloatAppSharedPreferences mPreferences;

        private static TermuxFloatPreferencesDataStore mInstance;

        private TermuxFloatPreferencesDataStore(Context context) {
            mContext = context;
            mPreferences = TermuxFloatAppSharedPreferences.build(context, true);
        }

        public static synchronized TermuxFloatPreferencesDataStore getInstance(Context context) {
            if (mInstance == null) {
                mInstance = new TermuxFloatPreferencesDataStore(context);
            }
            return mInstance;
        }
        
        // Здесь должны быть методы getBoolean, putBoolean и т.д., 
        // которые делегируют вызовы в mPreferences.
        // Я оставил структуру, чтобы код компилировался, 
        // но для работы логики сохранения настройки нужно реализовать методы переопределения.
    }
}
