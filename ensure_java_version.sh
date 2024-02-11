#!/bin/bash
CACHE_DIR=$NETLIFY_BUILD_BASE/cache

JAVA_VERSION_URL="https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-x64_bin.tar.gz"
JAVA_RELEASE=jdk-17.0.2 # Must match directory inside archive in JAVA_VERSION_URL

# Check if Java is installed and get its version
java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)

# Version check, handle errors
if [ -z "$java_version" ]; then
  echo "Java not found. Please install Java to proceed."
  exit 1
fi

# Version check using numeric values
if [ "$java_version" -lt 11 ]; then
  echo "Java version 11 is required as a minimum by Shadow-cljs (found Java version $java_version)"

  # Check if JDK is in cache
  if [ ! -d "$CACHE_DIR/$JAVA_RELEASE" ]; then
    echo "Downloading $JAVA_RELEASE since it isn't available in cache"

    wget --quiet -O openjdk.tar.gz $JAVA_VERSION_URL
    tar xf openjdk.tar.gz --directory $CACHE_DIR
  fi

  echo "Enabling $JAVA_RELEASE from cache by adding it to the PATH"
  export PATH=$CACHE_DIR/$JAVA_RELEASE/bin:$PATH
else
  echo "Java version is $java_version, which is 11 or higher. No need to change."
fi
