/*
 * Console Minecraft Launcher
 * Copyright (C) 2021-2022  MrShiehX <3553413882@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.mrshiehx.cmcl.options;

import com.mrshiehx.cmcl.ConsoleMinecraftLauncher;
import com.mrshiehx.cmcl.bean.arguments.Arguments;

import java.util.LinkedList;
import java.util.List;

public class HelpOption implements Option {
    @Override
    public void execute(Arguments arguments) {
        List<String> usageNames = new LinkedList<>();
        usageNames.add("TITLE");
        usageNames.add("CONFIG");
        usageNames.add("ACCOUNT");
        usageNames.add("VERSION");
        usageNames.add("VERSION_CONFIG");
        usageNames.add("JVM_ARGS");
        usageNames.add("GAME_ARGS");
        usageNames.add("INSTALL");
        usageNames.add("MOD");
        usageNames.add("MODPACK");
        usageNames.add("MOD2");
        usageNames.add("MODPACK2");
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < usageNames.size(); i++) {
            String usageName = usageNames.get(i);
            if (i != 0) {
                stringBuilder.append("  ");
            }
            stringBuilder.append(ConsoleMinecraftLauncher.getUsage(usageName));
            if (i != 0) {
                if (i + 1 < usageNames.size()) {
                    stringBuilder.append('\n');
                    stringBuilder.append("------------------------------------------------------------------------------------------------------");
                    stringBuilder.append('\n');
                }
            } else {
                stringBuilder.append('\n');
                stringBuilder.append("------------------------------------------------------------------------------------------------------");
                stringBuilder.append('\n');
            }
        }
        System.out.println(stringBuilder);
    }

    @Override
    public String getUsageName() {
        return null;
    }
}
