# --------------------------------------------------------------------------------
# PDFBox для Android
# --------------------------------------------------------------------------------
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# --------------------------------------------------------------------------------
# Apache POI (DOCX/Excel/PowerPoint)
# --------------------------------------------------------------------------------
-dontwarn org.apache.poi.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.xmlbeans.**
-dontwarn net.sf.saxon.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class net.sf.saxon.** { *; }

# --------------------------------------------------------------------------------
# Batik (SVG в Apache POI)
# --------------------------------------------------------------------------------
-dontwarn org.apache.batik.**
-keep class org.apache.batik.** { *; }

# --------------------------------------------------------------------------------
# Log4j и OSGi
# --------------------------------------------------------------------------------
-dontwarn org.apache.logging.log4j.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-keep class org.apache.logging.log4j.** { *; }

# --------------------------------------------------------------------------------
# Tink (Google Security Crypto)
# --------------------------------------------------------------------------------
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# --------------------------------------------------------------------------------
# libGDX
# --------------------------------------------------------------------------------
-dontwarn com.badlogicgames.gdx.**
-keep class com.badlogicgames.gdx.** { *; }

# --------------------------------------------------------------------------------
# SQLCipher
# --------------------------------------------------------------------------------
-dontwarn net.sqlcipher.**
-keep class net.sqlcipher.** { *; }

# --------------------------------------------------------------------------------
# AndroidX / Compose
# --------------------------------------------------------------------------------
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# --------------------------------------------------------------------------------
# Kotlin / Coroutines
# --------------------------------------------------------------------------------
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.** { *; }

# --------------------------------------------------------------------------------
# Termux / Terminal
# --------------------------------------------------------------------------------
-keep class com.termux.terminal.TermuxSessionClient { *; }
-keep class com.termux.view.TerminalViewClient { *; }
-keep class com.termux.shared.termux.shell.TermuxShellManager { *; }
-keep class com.termux.app.TermuxService { *; }
-keep class com.termux.app.terminal.TermuxTerminalSessionActivityClient { *; }
-keep class com.termux.app.terminal.TermuxTerminalSessionServiceClient { *; }

# --------------------------------------------------------------------------------
# Прочие общие рекомендации
# --------------------------------------------------------------------------------
# Не удалять все классы с аннотациями
-keepattributes *Annotation*

# Не удалять перечисления
-keepclassmembers enum * { *; }

# Для сериализации
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object readResolve();
    java.lang.Object writeReplace();
}

# --------------------------------------------------------------------------------
# Исключения для R8 / ProGuard
# --------------------------------------------------------------------------------
-dontwarn java.awt.**
-dontwarn java.awt.color.**
-dontwarn java.awt.geom.**
-dontwarn java.awt.image.**
-dontwarn com.gemalto.jp2.**
-dontwarn net.sf.saxon.sxpath.**
-dontwarn org.osgi.framework.**
