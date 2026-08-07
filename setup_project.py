import os

# Definisi Struktur File dan Isinya
files = {
    # 1. Server PC
    "server_pc.py": '''from flask import Flask, request, jsonify
import pyautogui

app = Flask(__name__)

@app.route('/api/timbangan', methods=['POST'])
def receive_weight():
    data = request.get_json()
    if not data or 'weight' not in data:
        return jsonify({"status": "error", "message": "Data tidak valid"}), 400

    weight_value = data['weight']
    timestamp = data.get('timestamp', 'Realtime')
    print(f"[{timestamp}]  Data Timbangan Diterima: {weight_value} kg")

    pyautogui.typewrite(str(weight_value))
    pyautogui.press('enter')

    return jsonify({"status": "success", "received": weight_value}), 200

if __name__ == '__main__':
    print(" Server PC berjalan di Port 5000...")
    app.run(host='0.0.0.0', port=5000, debug=True)
''',

    # 2. Config Gradle Root
    "settings.gradle.kts": '''pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ScaleReader"
include(":app")
''',

    "build.gradle.kts": '''plugins {
    id("com.android.application") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
}
''',

    # 3. Config App Gradle
    "app/build.gradle.kts": '''plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.scalereader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.scalereader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("org.opencv:opencv:4.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
''',

    # 4. Manifest
    "app/src/main/AndroidManifest.xml": '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.scalereader">

    <uses-feature android:name="android.hardware.camera.any" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Timbangan OCR"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
''',

    # 5. UI XML
    "app/src/main/res/layout/activity_main.xml": '''<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/viewFinder"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <View
        android:id="@+id/scanBox"
        android:layout_width="280dp"
        android:layout_height="90dp"
        android:background="@drawable/border_scan_box"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="Arahkan kotak ke layar timbangan"
        android:textColor="#FFFFFF"
        android:textSize="16sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/scanBox" />

</androidx.constraintlayout.widget.ConstraintLayout>
''',

    "app/src/main/res/drawable/border_scan_box.xml": '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <stroke android:width="3dp" android:color="#FF0000" />
    <solid android:color="#00000000" />
    <corners android:radius="8dp"/>
</shape>
''',

    # 6. GitHub Action Workflow
    ".github/workflows/build.yml": '''name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Grant Execute Permission for Gradle
        run: chmod +x gradlew || true

      - name: Build Debug APK
        run: gradle assembleDebug

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: ScaleReader-APK
          path: app/build/outputs/apk/debug/app-debug.apk
'''
}

def create_project():
    print("Mulai membuat struktur proyek...")
    for path, content in files.items():
        dir_name = os.path.dirname(path)
        if dir_name:
            os.makedirs(dir_name, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f" File dibuat: {path}")
    print("\n Selesai! Semua struktur folder dan konfigurasi berhasil dibuat.")

if __name__ == "__main__":
    create_project()
