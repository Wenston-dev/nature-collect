# ONNX Runtime calls back into its Java classes through JNI.
-keep class ai.onnxruntime.** { *; }

# MapLibre talks to its Java side over JNI and parses GeoJSON with Gson reflection.
-keep class org.maplibre.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn org.maplibre.**
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
