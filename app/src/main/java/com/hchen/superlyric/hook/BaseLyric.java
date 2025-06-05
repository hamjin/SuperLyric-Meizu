/*
 * This file is part of SuperLyric.

 * SuperLyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2025 HChenX
 */
package com.hchen.superlyric.hook;

import android.app.Application;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hchen.dexkitcache.DexkitCache;
import com.hchen.dexkitcache.IDexkitList;
import com.hchen.hooktool.HCBase;
import com.hchen.hooktool.HCData;
import com.hchen.hooktool.hook.IHook;
import com.hchen.superlyric.helper.MeiZuNotification;
import com.hchen.superlyricapi.ISuperLyricDistributor;
import com.hchen.superlyricapi.SuperLyricData;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.BaseDataList;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import dalvik.system.PathClassLoader;

/**
 * Super Lyric 基类
 *
 * @author 焕晨HChen
 */
public abstract class BaseLyric extends HCBase {
    private static ISuperLyricDistributor iSuperLyricDistributor;
    public static AudioManager audioManager;
    public static String packageName;
    public static long versionCode = -1L;
    public static String versionName = "unknown";

    @Override
    @CallSuper
    protected void onApplication(@NonNull Context context) {
        if (!isEnabled()) return;
        packageName = context.getPackageName();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        Intent intent = new Intent("Super_Lyric");
        intent.putExtra("super_lyric_add_package", packageName);
        context.sendBroadcast(intent);

        Intent intentBinder = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        Objects.requireNonNull(intentBinder, "Failed to get designated binder intent, can't use SuperLyric!!");

        Bundle bundle = intentBinder.getBundleExtra("super_lyric_info");
        Objects.requireNonNull(bundle, "Failed to get designated binder bundle, please try reboot system!!");

        iSuperLyricDistributor = ISuperLyricDistributor.Stub.asInterface(bundle.getBinder("super_lyric_binder"));

        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = packageInfo.versionName;
            versionCode = packageInfo.getLongVersionCode();
            logI(TAG, "App packageName: " + packageName + ", versionName: " + versionName + ", versionCode: " + versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            logW(TAG, "Failed to get package: [" + packageName + "] version code!!");
        }

        logD(TAG, "Success to get binder: " + iSuperLyricDistributor + ", caller package name: " + packageName);
    }

    /**
     * Hook 热更新服务，用于更改当前 classloader
     */
    public static void hookTencentTinker() {
        hookMethod("com.tencent.tinker.loader.TinkerLoader",
            "tryLoad", "com.tencent.tinker.loader.app.TinkerApplication",
            new IHook() {
                @Override
                public void after() {
                    Intent intent = (Intent) getResult();
                    Application application = (Application) getArg(0);
                    int code = intent.getIntExtra("intent_return_code", -2);
                    if (code == 0) {
                        HCData.setClassLoader(application.getClassLoader());
                    }
                }
            }
        );
    }

    /**
     * 模拟连接蓝牙
     */
    public static void openBluetoothA2dp() {
        hookMethod("android.media.AudioManager",
            "isBluetoothA2dpOn",
            returnResult(true)
        );

        hookMethod("android.bluetooth.BluetoothAdapter",
            "isEnabled",
            returnResult(true)
        );
    }

    /**
     * 获取 MediaMetadataCompat 中的歌词数据
     */
    public static void getMediaMetadataCompatLyric() {
        hookMethod("android.support.v4.media.MediaMetadataCompat$Builder",
            "putString",
            String.class, String.class,
            new IHook() {
                @Override
                public void after() {
                    if (Objects.equals("android.media.metadata.TITLE", getArg(0))) {
                        String lyric = (String) getArg(1);
                        if (lyric == null) return;
                        sendLyric(lyric);
                    }
                }
            }
        );
    }

    private static String lastLyric;

    /**
     * 发送歌词
     *
     * @param lyric 歌词
     */
    public static void sendLyric(String lyric) {
        sendLyric(lyric, 0);
    }

    /**
     * 发送歌词和当前歌词的持续时间 (ms)
     *
     * @param lyric 歌词
     * @param delay 歌词持续时间 (ms)
     */
    public static void sendLyric(String lyric, int delay) {
        if (lyric == null) return;
        if (iSuperLyricDistributor == null) return;

        try {
            lyric = lyric.trim();
            if (Objects.equals(lyric, lastLyric)) return;
            if (lyric.isEmpty()) return;
            lastLyric = lyric;

            iSuperLyricDistributor.onSuperLyric(new SuperLyricData()
                .setPackageName(packageName)
                .setLyric(lyric)
                .setDelay(delay)
            );
        } catch (RemoteException e) {
            logE("BaseLyric", "sendLyric: ", e);
        }

        logD("BaseLyric", delay != 0 ? "Lyric: " + lyric + ", Delay: " + delay : "Lyric: " + lyric);
    }

    /**
     * 发送播放状态暂停
     */
    public static void sendStop() {
        sendStop(packageName);
    }

    /**
     * 发送播放状态暂停
     *
     * @param packageName 暂停播放的音乐软件包名
     */
    public static void sendStop(String packageName) {
        sendStop(
            new SuperLyricData()
                .setPackageName(packageName)
        );
    }

    /**
     * 发送播放状态暂停
     *
     * @param data 数据
     */
    public static void sendStop(@NonNull SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onStop(data);
        } catch (RemoteException e) {
            logE("BaseLyric", "sendStop: " + e);
        }

        logD("BaseLyric", "Stop: " + data);
    }

    /**
     * 发送数据包
     *
     * @param data 数据
     */
    public static void sendSuperLyricData(@NonNull SuperLyricData data) {
        if (iSuperLyricDistributor == null) return;

        try {
            iSuperLyricDistributor.onSuperLyric(data);
        } catch (RemoteException e) {
            logE("BaseLyric", "sendSuperLyricData: " + e);
        }

        logD("BaseLyric", "SuperLyricData: " + data);
    }

    /**
     * 超时检查，超时自动发送暂停状态
     */
    public static class Timeout {
        private static Timer timer = new Timer();
        private static boolean isRunning = false;

        public static void start() {
            if (isRunning) return;

            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (audioManager != null && !audioManager.isMusicActive()) {
                        sendStop();
                        stop();
                    }
                }
            }, 0, 1000);
            isRunning = true;
        }

        public static void stop() {
            if (timer == null || !isRunning) return;

            timer.cancel();
            timer = null;
            isRunning = false;
        }
    }

    /**
     * 模拟为魅族设备，用于开启魅族状态栏功能
     */
    public static class MeizuHelper {
        private static Class<?> meizu;

        /**
         * 模拟魅族
         */
        public static void mockDevice(boolean hook) {
            setStaticField("android.os.Build", "BRAND", "meizu");
            setStaticField("android.os.Build", "MANUFACTURER", "Meizu");
            setStaticField("android.os.Build", "DEVICE", "m1892");
            setStaticField("android.os.Build", "DISPLAY", "Flyme");
            setStaticField("android.os.Build", "PRODUCT", "meizu_16thPlus_CN");
            setStaticField("android.os.Build", "MODEL", "meizu 16th Plus");

            if (!hook)
                return;

            meizu = findClass("com.hchen.superlyric.helper.MeiZuNotification", new PathClassLoader(HCData.getModulePath(), classLoader));
            hookMethod(Class.class, "forName", String.class,
                new IHook() {
                    @Override
                    public void before() {
                        try {
                            if ("android.app.Notification".equals(getArg(0))) {
                                setResult(meizu);
                                return;
                            }
                            Class<?> clazz = (Class<?>) callThisStaticMethod("forName", getArg(0), true, classLoader);
                            setResult(clazz);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            );
        }

        public static void mockDevice() {
            mockDevice(true);
        }

        public static void getMeizuNotificationLyric() {
            if (existsClass("androidx.media3.common.util.Util")) {
                hookMethod("androidx.media3.common.util.Util",
                    "setForegroundServiceNotification",
                    Service.class, int.class, Notification.class, int.class, String.class,
                    createNotificationHook()
                );
            }
            if (existsClass("androidx.core.app.NotificationManagerCompat")) {
                hookMethod("androidx.core.app.NotificationManagerCompat",
                    "notify",
                    String.class, int.class, Notification.class,
                    createNotificationHook()
                );
            }
            if (existsClass("android.app.NotificationManager")) {
                hookMethod("android.app.NotificationManager",
                    "notify",
                    String.class, int.class, Notification.class,
                    createNotificationHook()
                );
            }
        }

        private static IHook createNotificationHook() {
            return new IHook() {
                @Override
                public void before() {
                    Notification notification = (Notification) getArg(2);
                    if (notification == null) return;

                    boolean isLyric = ((notification.flags & MeiZuNotification.FLAG_ALWAYS_SHOW_TICKER) != 0
                        || (notification.flags & MeiZuNotification.FLAG_ONLY_UPDATE_TICKER) != 0);
                    if (isLyric) {
                        if (notification.tickerText == null) {
                            sendStop();
                        } else if (notification.tickerText.toString().trim().isEmpty()) {
                            sendStop();
                        } else if (notification.tickerText.toString().trim().contains("纯音乐") ||
                                    notification.tickerText.toString().trim().contains("无歌词")) {
                            sendStop();
                        } else sendLyric(notification.tickerText.toString());

                        return;
                    }

                    if ("网易云音乐正在播放".equals(notification.tickerText.toString()))
                        sendStop();
                }
            };
        }
    }

    /**
     * 模拟为 OPPO 设备
     */
    public static class OPPOHelper {
        public static void mockDevice() {
            setStaticField("android.os.Build", "BRAND", "oppo");
            setStaticField("android.os.Build", "MANUFACTURER", "Oppo");
            setStaticField("android.os.Build", "DISPLAY", "Color");
        }
    }

    /**
     * 获取 QQLite 歌词
     */
    public static class QQLite {
        /**
         * 是否支持 QQLite
         */
        public static boolean isQQLite() {
            return existsClass("com.tencent.qqmusic.core.song.SongInfo");
        }

        public static void init() {
            if (!isQQLite()) return;

            hookMethod("com.tencent.qqmusiccommon.util.music.RemoteLyricController",
                "BluetoothA2DPConnected",
                returnResult(true)
            );

            hookMethod("com.tencent.qqmusiccommon.util.music.RemoteControlManager",
                "updataMetaData",
                "com.tencent.qqmusic.core.song.SongInfo", String.class,
                new IHook() {
                    @Override
                    public void before() {
                        String lyric = (String) getArg(1);
                        if (lyric == null || lyric.isEmpty()) return;
                        if (Objects.equals(lyric, "NEED_NOT_UPDATE_TITLE")) return;

                        sendLyric(lyric);
                    }
                }
            );
        }
    }

    /**
     * 阻止音乐应用获取屏幕关闭的广播，可能可以使其在息屏状态输出歌词
     */
    public static class ScreenHelper {
        public static void screenOffNotStopLyric(@NonNull String... excludes) {
            try {
                Method[] methods = DexkitCache.findMemberList("screen_helper", new IDexkitList() {
                    @NonNull
                    @Override
                    public BaseDataList<?> dexkit(@NonNull DexKitBridge bridge) throws ReflectiveOperationException {
                        return bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                .usingStrings("android.intent.action.SCREEN_OFF")
                                .returnType(void.class)
                                .name("onReceive")
                                .paramTypes(Context.class, Intent.class)
                            )
                        );
                    }
                });

                Arrays.stream(methods).forEach(method -> {
                    String className = method.getDeclaringClass().getSimpleName();
                    if (!className.contains("Fragment") && !className.contains("Activity")) {
                        if (Arrays.stream(excludes).noneMatch(className::contains)) {
                            logI("ScreenHelper", "screenOffNotStopLyric class name: " + className);

                            hook(method,
                                new IHook() {
                                    @Override
                                    public void before() {
                                        Intent intent = (Intent) getArg(1);
                                        if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_OFF)) {
                                            returnNull();
                                        }
                                    }
                                }
                            );
                        }
                    }
                });
            } catch (Throwable e) {
                logE("ScreenHelper", e);
            }
        }
    }
}
