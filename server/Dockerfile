# --- build ---------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY server ./server

# The Android module needs the Android SDK, so leave it out of the image build.
ENV LAZIER_SKIP_ANDROID=true

RUN chmod +x gradlew \
 && ./gradlew :server:installDist --no-daemon --no-build-cache

# --- runtime -------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /src/server/build/install/server ./

ENV PORT=8787 \
    BOOKS_DIR=/data/novels

EXPOSE 8787
ENTRYPOINT ["./bin/server"]
