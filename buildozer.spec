[app]
title = PyAndroid
package.name = pyandroid
package.domain = org.pyandroid
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 0.1
# version.regex and version.filename removed (conflict with version)
requirements = python3,kivy,pygments
orientation = portrait
osx.python_version = 3
osx.kivy_version = 2.3.1
fullscreen = 0

[buildozer]
log_level = 2
warn_on_root = 1
archs = arm64-v8a

[app:android]
android.api = 34
android.minapi = 21
android.sdk = 34
android.ndk = 25b
android.gradle_dependencies = 'com.android.support:support-annotations:28.0.0'
android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE
android.archs = arm64-v8a
android.accept_sdk_license = True
android.presplash_color = #1e1e1e
android.wakelock = True

[app:ios]
title = PyAndroid

[deploy]
android.artifacts = apk
