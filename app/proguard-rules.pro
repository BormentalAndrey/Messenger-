# ================================================================================
# ОСНОВНЫЕ ПРАВИЛА СОХРАНЕНИЯ (CORE)
# ================================================================================

# Сохранять атрибуты для отладки, работы библиотек и рефлексии
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Сохранять перечисления и методы сериализации
-keepclassmembers enum * { *; }

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object readResolve();
    java.lang.Object writeReplace();
}

# ================================================================================
# AI & JNI INTEGRATION (Критически важно для работы LlamaBridge)
# ================================================================================

# Запрещаем переименовывать модели данных
-keep class com.kakdela.p2p.model.** { *; }

# ЗАПРЕЩАЕМ обфускацию моста JNI.
# Библиотека libllama.so ожидает строгое имя: Java_com_kakdela_p2p_ai_LlamaBridge_init
-keep class com.kakdela.p2p.ai.LlamaBridge {
    native <methods>;
    <fields>;
    public *;
}

# Защищаем ViewModel и их конструкторы. 
# Ошибка "Cannot create an instance" лечится сохранением конструкторов.
-keep class com.kakdela.p2p.viewmodel.AiChatViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# ================================================================================
# GUAVA (Исправляет Missing class com.google.common.io.MoreFiles)
# ================================================================================
-dontwarn com.google.common.io.**
-dontwarn com.google.common.collect.**
-dontwarn com.google.common.util.concurrent.**
-dontwarn com.google.common.cache.**
-keep class com.google.common.io.** { *; }
-keep class com.google.common.collect.** { *; }
-keep class com.google.common.base.** { *; }

# ================================================================================
# TERMUX / TERMINAL
# ================================================================================
-dontwarn com.termux.**
-keep class com.termux.** { *; }
-keep interface com.termux.** { *; }

-keep class com.termux.terminal.TermuxSessionClient { *; }
-keep class com.termux.view.TerminalViewClient { *; }
-keep class com.termux.shared.termux.shell.TermuxShellManager { *; }
-keep class com.termux.app.TermuxService { *; }
-keep class com.termux.app.terminal.TermuxTerminalSessionActivityClient { *; }
-keep class com.termux.app.terminal.TermuxTerminalSessionServiceClient { *; }

# ================================================================================
# PDFBOX & APACHE POI (Работа с документами)
# ================================================================================
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

-dontwarn org.apache.poi.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.xmlbeans.**
-dontwarn net.sf.saxon.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class net.sf.saxon.** { *; }

# Batik & Log4j
-dontwarn org.apache.batik.**
-keep class org.apache.batik.** { *; }
-dontwarn org.apache.logging.log4j.**
-keep class org.apache.logging.log4j.** { *; }

# ================================================================================
# БИБЛИОТЕКИ БЕЗОПАСНОСТИ И БД
# ================================================================================
# Tink (Crypto)
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# SQLCipher
-dontwarn net.sqlcipher.**
-keep class net.sqlcipher.** { *; }

# ================================================================================
# ГРАФИКА И ДВИЖКИ
# ================================================================================
# libGDX
-dontwarn com.badlogicgames.gdx.**
-keep class com.badlogicgames.gdx.** { *; }

# ================================================================================
# ANDROIDX, COMPOSE & KOTLIN
# ================================================================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class com.google.android.material.** { *; }

-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.** { *; }

# OkHttp (необходимо для работы ModelDownloadManager)
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**

# ================================================================================
# ИСКЛЮЧЕНИЯ ДЛЯ ПРЕДОТВРАЩЕНИЯ ОШИБОК СБОРКИ (DON'T WARN)
# ================================================================================
-dontwarn java.awt.**
-dontwarn java.awt.color.**
-dontwarn java.awt.geom.**
-dontwarn java.awt.image.**
-dontwarn com.gemalto.jp2.**
-dontwarn net.sf.saxon.sxpath.**
-dontwarn org.osgi.framework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn edu.umd.cs.findbugs.annotations.**
