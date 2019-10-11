FROM cirrusci/android-sdk:29

RUN sdkmanager "system-images;android-25;google_apis;x86" && sdkmanager --licenses

RUN echo no | avdmanager create avd --force -n test -k "system-images;android-25;google_apis;x86"