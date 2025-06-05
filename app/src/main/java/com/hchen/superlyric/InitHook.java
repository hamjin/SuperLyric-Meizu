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
package com.hchen.superlyric;

import static com.hchen.hooktool.HCInit.LOG_D;
import static com.hchen.hooktool.HCInit.LOG_I;

import androidx.annotation.NonNull;

import com.hchen.collect.CollectMap;
import com.hchen.dexkitcache.DexkitCache;
import com.hchen.hooktool.HCBase;
import com.hchen.hooktool.HCEntrance;
import com.hchen.hooktool.HCInit;
import com.hchen.hooktool.log.XposedLog;
import com.hchen.superlyric.hook.music.Api;
import com.hchen.superlyric.meizu.BuildConfig;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook 入口
 *
 * @author 焕晨HChen
 */
public class InitHook extends HCEntrance {
    private static final String TAG = "SuperLyric";
    private static final HashMap<String, HCBase> mCacheBaseHCMap = new HashMap<>();

    @NonNull
    @Override
    public HCInit.BasicData initHC(@NonNull HCInit.BasicData basicData) {
        return basicData
            .setTag(TAG)
            .setPrefsName("super_lyric_prefs")
            .setLogLevel(BuildConfig.DEBUG ? LOG_D : LOG_I)
            .setModulePackageName(BuildConfig.APPLICATION_ID)
            .setLogExpandPath("com.hchen.superlyric.hook")
            .setLogExpandIgnoreClassNames("BaseLyric");
    }

    @NonNull
    @Override
    public String[] ignorePackageNameList() {
        return new String[]{
            "com.miui.contentcatcher",
            "com.android.providers.settings",
            "com.android.server.telecom",
            "com.google.android.webview"
        };
    }

    @Override
    public void onLoadPackage(@NonNull XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        mCacheBaseHCMap.clear();
        if (!CollectMap.getAllPackageSet().contains(loadPackageParam.packageName)) {
            HCInit.initLoadPackageParam(loadPackageParam);
            new Api().onApplication().onLoadPackage();
            return;
        }

        try {
            // DexKitUtils.init(loadPackageParam);
            if (loadPackageParam.appInfo != null) {
                DexkitCache.init(
                    "superlyric",
                    loadPackageParam.classLoader,
                    loadPackageParam.appInfo.sourceDir,
                    loadPackageParam.appInfo.dataDir
                );
            }
            CollectMap.getOnLoadPackageList(loadPackageParam.packageName).forEach(new Consumer<String>() {
                @Override
                public void accept(String fullClass) {
                    try {
                        HCInit.initLoadPackageParam(loadPackageParam);
                        Class<?> clazz = getClass().getClassLoader().loadClass(fullClass);
                        HCBase hcBase = (HCBase) clazz.getDeclaredConstructor().newInstance();
                        hcBase.onApplication();
                        mCacheBaseHCMap.put(fullClass, hcBase);
                    } catch (Throwable ex) {
                        XposedLog.logE(TAG, "Failed to load class: " + fullClass, ex);
                    }
                }
            });

            CollectMap.getOnLoadPackageList(loadPackageParam.packageName).forEach(new Consumer<String>() {
                @Override
                public void accept(String fullClass) {
                    try {
                        if (mCacheBaseHCMap.get(fullClass) != null) {
                            HCBase hcBase = mCacheBaseHCMap.get(fullClass);
                            assert hcBase != null;
                            hcBase.onLoadPackage();
                        } else {
                            HCInit.initLoadPackageParam(loadPackageParam);
                            Class<?> clazz = getClass().getClassLoader().loadClass(fullClass);
                            HCBase hcBase = (HCBase) clazz.getDeclaredConstructor().newInstance();
                            hcBase.onLoadPackage();
                        }
                    } catch (Throwable ex) {
                        XposedLog.logE(TAG, "Failed to load class: " + fullClass, ex);
                    }
                }
            });
        } catch (Throwable e) {
            XposedLog.logE(TAG, "InitHook error: ", e);
        } finally {
            // DexKitUtils.close();
            DexkitCache.close();
        }
    }

    @Override
    public void onInitZygote(@NonNull StartupParam startupParam) throws Throwable {
        CollectMap.getOnZygoteList().values().forEach(new Consumer<List<String>>() {
            @Override
            public void accept(List<String> fullClassList) {
                fullClassList.forEach(new Consumer<String>() {
                    @Override
                    public void accept(String fullClass) {
                        try {
                            Class<?> hookClass = getClass().getClassLoader().loadClass(fullClass);
                            HCBase hcBase = (HCBase) hookClass.getDeclaredConstructor().newInstance();
                            hcBase.onZygote();
                        } catch (ClassNotFoundException | NoSuchMethodException |
                                 IllegalAccessException | InstantiationException |
                                 InvocationTargetException e) {
                            XposedLog.logE(TAG, "Failed to load class: " + fullClass, e);
                        }
                    }
                });
            }
        });
    }
}
