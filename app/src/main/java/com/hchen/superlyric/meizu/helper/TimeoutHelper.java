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
package com.hchen.superlyric.meizu.helper;

import static com.hchen.superlyric.meizu.hook.LyricRelease.audioManager;
import static com.hchen.superlyric.meizu.hook.LyricRelease.sendStop;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 超时暂停歌词
 *
 * @author 焕晨HChen
 */
public class TimeoutHelper {
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

    private static void stop() {
        if (timer == null || !isRunning) return;

        timer.cancel();
        timer = null;
        isRunning = false;
    }
}
