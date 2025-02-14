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
package com.mrshiehx.cmcl;

import com.mrshiehx.cmcl.bean.arguments.Argument;
import com.mrshiehx.cmcl.bean.arguments.Arguments;
import com.mrshiehx.cmcl.constants.Constants;
import com.mrshiehx.cmcl.constants.Languages;
import com.mrshiehx.cmcl.interfaces.filters.StringFilter;
import com.mrshiehx.cmcl.options.HelpOption;
import com.mrshiehx.cmcl.options.Option;
import com.mrshiehx.cmcl.options.Options;
import com.mrshiehx.cmcl.options.StartOption;
import com.mrshiehx.cmcl.utils.DownloadUtils;
import com.mrshiehx.cmcl.utils.OperatingSystem;
import com.mrshiehx.cmcl.utils.PercentageTextProgress;
import com.mrshiehx.cmcl.utils.Utils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ConsoleMinecraftLauncher {
    public static final String CLIENT_ID = Constants.CLIENT_ID;
    public static final String CMCL_COPYRIGHT = Constants.COPYRIGHT;
    public static final String CMCL_VERSION = Constants.CMCL_VERSION;

    public static File gameDir;
    public static File assetsDir;
    public static File respackDir;
    public static File versionsDir;
    public static File librariesDir;
    public static File launcherProfiles;
    public static Process runningMc;
    public static boolean exitWithMinecraft;

    public static JSONObject configContent;
    public static String javaPath = "";

    private static String language;
    private static Locale locale;

    public static boolean isImmersiveMode;

    static {
        initConfig();
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (runningMc != null && exitWithMinecraft/*configContent.optBoolean("exitWithMinecraft")*/) {
                if (runningMc.isAlive()) {
                    runningMc.destroy();
                }
            }
        }));
        Arguments arguments = new Arguments(args, false, true);
        Argument arg = arguments.optArgument(0);
        if (arg != null) {
            console(arguments);
        } else {
            String version;
            JSONObject jsonObject = Utils.getConfig();
            version = jsonObject.optString("selectedVersion");
            if (isEmpty(version)) {
                System.out.println(getString("MESSAGE_FIRST_USE", Constants.CMCL_VERSION));
                System.out.print(getString("CONSOLE_ENTER_EXIT"));
                try {
                    new Scanner(System.in).nextLine();
                } catch (Exception ignore) {
                }
                return;
            }
            StartOption.start(version, jsonObject);
        }
    }

    private static void console(Arguments args) {
        Argument argument = args.optArgument(0);
        if (argument != null) {
            String key = argument.key;
            Option option = Options.MAP.get(key.toLowerCase());
            if (option != null) {
                Argument usage = args.optArgument(1);
                if (usage != null && !(option instanceof HelpOption)) {
                    if (usage.equals("usage") || usage.equals("help") || usage.equals("u") || usage.equals("h")) {
                        String name = option.getUsageName();
                        if (!isEmpty(name)) {
                            System.out.println(getUsage(name));
                        } else {
                            System.out.println(getUsage("TITLE"));
                        }
                    } else {
                        option.execute(args);
                    }
                } else {
                    option.execute(args);
                }
            } else {
                System.out.println(getString("CONSOLE_UNKNOWN_OPTION", key));
            }
        }
    }

    public static String getUsage(String usageName) {
        if (isEmpty(usageName)) return null;
        String t = ("zh".equalsIgnoreCase(getLanguage()) ? Languages.getZhUsage() : Languages.getEnUsage()).get(usageName);
        return isEmpty(t) ? "" : t;
    }

    public static JSONObject initConfig() {
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try {
                configContent = new JSONObject(Utils.readFileContent(configFile));
            } catch (Exception e) {
                e.printStackTrace();
                configContent = new JSONObject();
            }
            javaPath = configContent.optString("javaPath", Utils.getDefaultJavaPath());
            gameDir = new File(!isEmpty(configContent.optString("gameDir")) ? configContent.optString("gameDir") : ".minecraft");
            assetsDir = !isEmpty(configContent.optString("assetsDir")) ? new File(configContent.optString("assetsDir")) : new File(gameDir, "assets");
            respackDir = !isEmpty(configContent.optString("resourcesDir")) ? new File(configContent.optString("resourcesDir")) : new File(gameDir, "resourcepacks");
        } else {
            initDefaultDirs();
            configContent = new JSONObject();
            configContent.put("language", Locale.getDefault().getLanguage());
            configContent.put("javaPath", javaPath = Utils.getDefaultJavaPath());
            configContent.put("maxMemory", Utils.getDefaultMemory());
            configContent.put("windowSizeWidth", 854);
            configContent.put("windowSizeHeight", 480);
            configContent.put("exitWithMinecraft", false);
            try {
                Utils.createFile(configFile, false);
                FileWriter writer = new FileWriter(configFile, false);
                writer.write(configContent.toString(com.mrshiehx.cmcl.constants.Constants.INDENT_FACTOR));
                writer.close();
            } catch (IOException E) {
                E.printStackTrace();
            }
        }
        initChangelessDirs();
        initProxy(configContent);
        return configContent;
    }

    private static void initChangelessDirs() {
        versionsDir = new File(gameDir, "versions");
        librariesDir = new File(gameDir, "libraries");
        launcherProfiles = new File(gameDir, "launcher_profiles.json");
    }

    private static void initDefaultDirs() {
        gameDir = new File(".minecraft");
        assetsDir = new File(gameDir, "assets");
        respackDir = new File(gameDir, "resourcepacks");
    }

    private static void initProxy(JSONObject configContent) {
        String proxyHost = configContent.optString("proxyHost");
        String proxyPort = configContent.optString("proxyPort");
        if (isEmpty(proxyHost) || isEmpty(proxyPort)) return;
        Utils.setProxy(proxyHost, proxyPort, configContent.optString("proxyUsername"), configContent.optString("proxyPassword"));
    }


    public static void downloadFile(String url, File to) throws IOException {
        DownloadUtils.downloadFile(url, to, null);
    }

    public static void downloadFile(String url, File to, @Nullable PercentageTextProgress progressBar) throws IOException {
        DownloadUtils.downloadFile(url, to, progressBar);
    }

    public static byte[] downloadBytes(String url) throws IOException {
        return DownloadUtils.downloadBytes(url);
    }

    public static byte[] downloadBytes(String url, @Nullable PercentageTextProgress progressBar) throws IOException {
        return DownloadUtils.downloadBytes(url, progressBar);
    }


    public static boolean isEmpty(String s) {
        return null == s || s.length() == 0;
    }

    public static boolean isEmpty(JSONObject s) {
        return null == s || s.length() == 0;
    }


    public static void unZip(File zipFileSource, File to, @Nullable PercentageTextProgress progressBar) throws IOException {
        unZip(zipFileSource, to, progressBar, null);
    }

    public static void unZip(File zipFileSource, File to, @Nullable PercentageTextProgress progressBar, StringFilter filenameFilter) throws IOException {
        int BUFFER_SIZE = 2048;
        if (zipFileSource != null && zipFileSource.exists()) {
            ZipFile zipFile = new ZipFile(zipFileSource);

            int size = zipFile.size();
            if (progressBar != null)
                progressBar.setMaximum(size);
            Enumeration<?> entries = zipFile.entries();
            int progress = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (filenameFilter != null) {
                    if (!filenameFilter.accept(entry.getName())) {
                        continue;
                    }
                }

                File targetFile = new File(to, entry.getName());
                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    if (!targetFile.getParentFile().exists()) {
                        targetFile.getParentFile().mkdirs();
                    }
                    if (targetFile.exists()) targetFile.delete();
                    targetFile.createNewFile();
                    InputStream is = zipFile.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    int len;
                    byte[] buf = new byte[BUFFER_SIZE];
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.close();
                    is.close();
                }
                progress++;
                if (progressBar != null)
                    progressBar.setValue(progress);
            }
            zipFile.close();
            if (progressBar != null)
                progressBar.setValue(size);
        }
    }


    public static String getString(String name, Object... objects) {
        return String.format(getString(name), objects);
    }

    public static String getString(String name) {
        if ("zh".equalsIgnoreCase(getLanguage())) {
            String text = Languages.getZh().get(name);
            if (!Utils.isEmpty(text)) return text;
        }
        String text = Languages.getEn().get(name);
        if (!Utils.isEmpty(text)) return text;
        else return name;
    }

    public static String getLanguage() {
        if (language == null) {
            String lang = Utils.getConfig().optString("language");
            if (isEmpty(lang)) {
                configContent.put("language", language = Locale.getDefault().getLanguage());
                Utils.saveConfig(configContent);
            } else {
                language = lang;
            }
        }
        return language;
    }


    public static List<String> listVersions(File versionsDir) {
        List<String> versionsStrings = new ArrayList<>();
        if (versionsDir == null) return versionsStrings;
        File[] files = versionsDir.listFiles(pathname -> {
            if (!pathname.isDirectory()) return false;
            File[] files1 = pathname.listFiles();
            if (files1 == null || files1.length < 1) return false;
            return new File(pathname, pathname.getName() + ".json").exists() /*&& new File(pathname, pathname.getName() + ".jar").exists()*/;
        });
        if (files != null && files.length > 0) {
            for (File file : files) {
                versionsStrings.add(file.getName());
            }
        }

        return versionsStrings;
    }

    public static void createLauncherProfiles() {
        if (launcherProfiles.exists()) return;
        try {
            launcherProfiles.createNewFile();
            Utils.writeFile(launcherProfiles, "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}", false);
        } catch (Exception ignore) {
        }
    }

    public static void setLanguage(String language) {
        ConsoleMinecraftLauncher.language = language;
    }

    public static Locale getLocale() {
        if (locale == null) {
            locale = "zh".equalsIgnoreCase(getLanguage()) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        }
        return locale;
    }

    public static void setGameDirs() {
        gameDir = new File(!isEmpty(configContent.optString("gameDir")) ? configContent.optString("gameDir") : ".minecraft");
        assetsDir = !isEmpty(configContent.optString("assetsDir")) ? new File(configContent.optString("assetsDir")) : new File(gameDir, "assets");
        respackDir = !isEmpty(configContent.optString("resourcesDir")) ? new File(configContent.optString("resourcesDir")) : new File(gameDir, "resourcepacks");
        initChangelessDirs();
    }

    public static File getConfigFile() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            File inConfigDir = new File(System.getProperty("user.home"), ".config/cmcl/cmcl.json");
            if (inConfigDir.exists()) {
                return inConfigDir;
            }
            if (Constants.DEFAULT_CONFIG_FILE.exists()) {
                return Constants.DEFAULT_CONFIG_FILE;
            }
            return inConfigDir;
        }
        return Constants.DEFAULT_CONFIG_FILE;
    }
}