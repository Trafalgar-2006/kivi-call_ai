# Shizuku starts the audio bridge by class name, in its own process, through this constructor.
-keep class dev.callai.audio.AudioBridge { <init>(...); }
-keep class dev.callai.IAudioBridge { *; }
-keep class dev.callai.IAudioBridge$** { *; }
