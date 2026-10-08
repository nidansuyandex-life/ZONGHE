# 保留讯飞 SDK 相关类
-keep class com.iflytek.sparkchain.** {*;}
-keep class com.iflytek.cloud.** {*;}
-keep class com.iflytek.codec.** {*;}

# 保留 WebView 和 JS 桥接代码
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*

# 基础组件保留
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
