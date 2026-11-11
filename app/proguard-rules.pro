# Имена не запутываем (для приложения без секрета в коде это безопаснее при сборке без проверки на устройстве), но неиспользуемое вырезаем.
-dontobfuscate
# JNI: Java_app_rayclient_Native_* ищутся по имени
-keep class app.rayclient.Native { native <methods>; }
# WorkManager создаёт воркеры по имени класса
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# Room/WorkManager creates WorkDatabase_Impl reflectively before MainActivity starts.
# AGP 9 R8 full mode can retain the class but strip its zero-argument constructor.
# Keep only this required constructor; leave all other Room code optimizable.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
