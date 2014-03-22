#!/bin/bash

FILENAME=$1
if [ -z $1 ]; then
  FILENAME=android-screenshot-`date +'%Y%m%d%H%M%S'`.png
fi

ANDROID_PATH=/sdcard/$FILENAME
LOCAL_PATH=./$FILENAME

echo "Attempting to store screenshot to $LOCAL_PATH ..."
adb shell screencap -p $ANDROID_PATH && adb pull $ANDROID_PATH $LOCAL_PATH
adb shell rm $ANDROID_PATH

