"""Small, asserted patches to the pinned upstream JNI sample."""
from pathlib import Path

def replace_once(source, old, new):
    assert source.count(old) == 1, f'Pinned upstream changed: {old[:80]}'
    return source.replace(old, new)

jni = Path('third_party/whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/jni.c')
s = jni.read_text()
s = replace_once(s, '#include <string.h>', '#include <string.h>\n#include <time.h>')
s = replace_once(s, 'JNIEXPORT void JNICALL\nJava_com_whispercpp_whisper_WhisperLib_00024Companion_fullTranscribe(', '''static bool avakav_abort(void * user_data) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    const struct timespec * started = (const struct timespec *) user_data;
    return now.tv_sec - started->tv_sec >= 300;
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_fullTranscribe(''')
s = replace_once(s, '    params.print_realtime = true;', '    params.print_realtime = false;')
s = replace_once(s, '    params.print_timestamps = true;', '    params.print_timestamps = false;')
s = replace_once(s, '    params.single_segment = false;', '''    params.single_segment = false;
    struct timespec started;
    clock_gettime(CLOCK_MONOTONIC, &started);
    params.abort_callback = avakav_abort;
    params.abort_callback_user_data = &started;''')
s = replace_once(s, '''    if (whisper_full(context, params, audio_data_arr, audio_data_length) != 0) {
        LOGI("Failed to run the model");
    } else {
        whisper_print_timings(context);
    }
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);''', '''    int result = whisper_full(context, params, audio_data_arr, audio_data_length);
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
    if (result != 0) {
        jclass error_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (error_class != NULL) {
            (*env)->ThrowNew(env, error_class,
                "Whisper failed or exceeded the 5-minute limit for this audio chunk");
        }
    }''')
jni.write_text(s)
