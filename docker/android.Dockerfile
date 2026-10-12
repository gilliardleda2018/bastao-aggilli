# Gradle 8.9 + Java 17 + Android SDK 34: as mesmas versões do CI do GitHub.
FROM gradle:8.9-jdk17
ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && cd /tmp \
 && curl -sSfLo ct.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
 && unzip -q ct.zip && mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest && rm ct.zip \
 && yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null \
 && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" > /dev/null
