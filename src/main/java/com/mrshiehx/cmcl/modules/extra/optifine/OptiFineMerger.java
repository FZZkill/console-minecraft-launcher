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
 *
 */

package com.mrshiehx.cmcl.modules.extra.optifine;

import com.mrshiehx.cmcl.ConsoleMinecraftLauncher;
import com.mrshiehx.cmcl.api.download.DownloadSource;
import com.mrshiehx.cmcl.bean.Pair;
import com.mrshiehx.cmcl.bean.SplitLibraryName;
import com.mrshiehx.cmcl.modules.extra.ExtraMerger;
import com.mrshiehx.cmcl.utils.ConsoleUtils;
import com.mrshiehx.cmcl.utils.FileUtils;
import com.mrshiehx.cmcl.utils.PercentageTextProgress;
import com.mrshiehx.cmcl.utils.Utils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static com.mrshiehx.cmcl.ConsoleMinecraftLauncher.getString;
import static com.mrshiehx.cmcl.ConsoleMinecraftLauncher.isEmpty;

public class OptiFineMerger implements ExtraMerger {
    private static final String EXTRA_NAME = "OptiFine";

    /**
     * 将 OptiFine 的JSON合并到原版JSON
     *
     * @return key: 如果无法安装 OptiFine，是否继续安装 value:如果成功合并，则为需要安装的依赖库集合，否则为空
     **/
    @Override
    public Pair<Boolean, List<JSONObject>> merge(String minecraftVersion, JSONObject headJSONObject, File jarFile, boolean askContinue, @Nullable String extraVersion) {
        JSONArray versions;
        try {
            versions = new JSONArray(Utils.get(Utils.addSlashIfMissing(DownloadSource.getProvider().thirdPartyOptiFine()) + minecraftVersion));
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println(getString("INSTALL_MODLOADER_FAILED_TO_GET_INSTALLABLE_VERSION", EXTRA_NAME));
            return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", EXTRA_NAME)), null);
        }
        if (versions.length() == 0) {
            System.out.println(getString("INSTALL_MODLOADER_NO_INSTALLABLE_VERSION", EXTRA_NAME));
            return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", EXTRA_NAME)), null);
        }
        Map<String, JSONObject> versionsMap = new HashMap<>();
        for (Object o : versions) {
            if (o instanceof JSONObject) {
                JSONObject version = (JSONObject) o;
                String type = version.optString("type");
                String patch = version.optString("patch");
                versionsMap.put(type + (isEmpty(patch) ? "" : "_" + patch), version);
            }
        }


        if (versionsMap.size() == 0) {
            System.out.println(getString("INSTALL_MODLOADER_NO_INSTALLABLE_VERSION", EXTRA_NAME));
            return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", EXTRA_NAME)), null);
        }

        String optifineVersionString;
        JSONObject optifineVersion;

        if (isEmpty(extraVersion)) {
            System.out.print('[');

            List<String> optifineVersionNames = new ArrayList<>(versionsMap.keySet());
            for (int i = optifineVersionNames.size() - 1; i >= 0; i--) {
                String versionName = optifineVersionNames.get(i);
                if (versionName.contains(" ")) versionName = "\"" + versionName + "\"";
                System.out.print(versionName);//legal
                if (i > 0) {
                    System.out.print(", ");
                }
            }
            System.out.println(']');


            String inputOFVersion = selectOptiFineVersion(getString("INSTALL_MODLOADER_SELECT", EXTRA_NAME), versionsMap);
            if (inputOFVersion == null)
                return new Pair<>(false, null);
            optifineVersionString = inputOFVersion;

            optifineVersion = versionsMap.get(inputOFVersion);
            if (optifineVersion == null)
                return new Pair<>(false, null);
        } else {
            optifineVersionString = extraVersion;

            optifineVersion = versionsMap.get(extraVersion);
            if (optifineVersion == null) {
                System.out.println(getString("INSTALL_MODLOADER_FAILED_NOT_FOUND_TARGET_VERSION", extraVersion).replace("${NAME}", "OptiFine"));
                return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", EXTRA_NAME)), null);
            }
        }


        try {
            return installInternal(headJSONObject, optifineVersion, jarFile, optifineVersionString);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", EXTRA_NAME)), null);
        }
    }

    private static Pair<Boolean, List<JSONObject>> installInternal(JSONObject headJSONObject, JSONObject optifineVersion, File jarFile, String optiFineVersion) throws Exception {

        String patch = optifineVersion.optString("patch");
        String type = optifineVersion.optString("type");
        String mcversion = optifineVersion.optString("mcversion");

        String url = DownloadSource.getProvider().thirdPartyOptiFine() + mcversion + "/" + type + "/" + patch;

        File installer = new SplitLibraryName("optifine", "OptiFine", mcversion + "_" + type + "_" + patch, "installer").getPhysicalFile()/*new File("cmcl","OptiFine_"+mcversion+"_"+optiFineVersion+".jar")*/;

        System.out.print(getString("INSTALL_MODLOADER_DOWNLOADING_FILE"));
        try {
            ConsoleMinecraftLauncher.downloadFile(url, installer, new PercentageTextProgress());
        } catch (Exception e) {
            throw new Exception(getString("INSTALL_MODLOADER_FAILED_DOWNLOAD", EXTRA_NAME) + ": " + e);
        }


        JSONArray librariesArray = headJSONObject.optJSONArray("libraries");
        if (librariesArray == null) headJSONObject.put("libraries", librariesArray = new JSONArray());

        SplitLibraryName optifineFileName = new SplitLibraryName("optifine", "OptiFine", mcversion + "_" + type + "_" + patch);/**lib add to json*/
        librariesArray.put(new JSONObject().put("name", optifineFileName.toString()));

        File optifineFile = optifineFileName.getPhysicalFile();

        /*FileSystem installerFileSystem=null;
        for (FileSystemProvider fileSystemProvider : FileSystemProvider.installedProviders()) {
            if (fileSystemProvider.getScheme().equalsIgnoreCase("jar")) {
                try {
                    installerFileSystem=fileSystemProvider.newFileSystem(installer.toPath(), new HashMap<>());
                    break;
                } catch (IOException e) {
                    System.out.println(getString("INSTALL_MODLOADER_FAILED_WITH_REASON", MODLOADER_NAME, getString("EXCEPTION_READ_FILE")));
                    return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", MODLOADER_NAME)), null);

                }
            }
        }
        if (installerFileSystem == null) {
            System.out.println(getString("INSTALL_MODLOADER_FAILED_WITH_REASON", MODLOADER_NAME, getString("EXCEPTION_READ_FILE")));
            return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", MODLOADER_NAME)), null);
        }*/

        /*JarFile installerJar;
        try {
            installerJar = new JarFile(installer);
        }catch (Exception e){
            System.out.println(getString("INSTALL_MODLOADER_FAILED_WITH_REASON", MODLOADER_NAME, getString("EXCEPTION_READ_FILE_WITH_PATH",installer.getAbsolutePath())));
            return new Pair<>(askContinue && ConsoleUtils.yesOrNo(getString("INSTALL_MODLOADER_UNABLE_DO_YOU_WANT_TO_CONTINUE", MODLOADER_NAME)), null);
        }*/

        boolean containsLW = false;


        try (JarFile installerJar = new JarFile(installer)) {
            if (installerJar.getEntry("optifine/Patcher.class") != null) {

                List<String> command = new ArrayList<>(7);
                command.add(Utils.getDefaultJavaPath());
                command.add("-cp");
                command.add(installer.getAbsolutePath());
                command.add("optifine.Patcher");
                command.add(jarFile.getAbsolutePath());
                command.add(installer.getAbsolutePath());
                command.add(optifineFile.getAbsolutePath());

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
                try {
                    int waitFor = processBuilder.start().waitFor();
                    if (waitFor != 0) {
                        throw new Exception(getString("INSTALL_MODLOADER_FAILED_WITH_REASON", EXTRA_NAME, getString("EXCEPTION_EXECUTE_COMMAND")));
                    }
                } catch (Exception e) {
                    throw new Exception(getString("INSTALL_MODLOADER_FAILED_WITH_REASON", EXTRA_NAME, e));
                }
            } else {
                Utils.copyFile(installer, optifineFile);
            }


            ZipEntry lw2_0 = installerJar.getEntry("launchwrapper-2.0.jar");
            if (lw2_0 != null) {
                SplitLibraryName s = new SplitLibraryName("optifine", "launchwrapper", "2.0");/**lib add to json*/
                librariesArray.put(new JSONObject().put("name", s.toString()));
                FileUtils.inputStream2File(installerJar.getInputStream(lw2_0), s.getPhysicalFile());
                containsLW = true;
            }


            ZipEntry lwOfTxt = installerJar.getEntry("launchwrapper-of.txt");
            if (lwOfTxt != null) {
                String launchWrapperVersion = Utils.inputStream2String(installerJar.getInputStream(lwOfTxt)).trim();
                ZipEntry launchWrapperJar = installerJar.getEntry("launchwrapper-of-" + launchWrapperVersion + ".jar");


                if (launchWrapperJar != null) {

                    SplitLibraryName s = new SplitLibraryName("optifine", "launchwrapper-of", launchWrapperVersion);/**lib add to json*/
                    librariesArray.put(new JSONObject().put("name", s.toString()));
                    FileUtils.inputStream2File(installerJar.getInputStream(launchWrapperJar), s.getPhysicalFile());
                    containsLW = true;
                }
            }


            ZipEntry buildofText = installerJar.getEntry("buildof.txt");
            if (buildofText != null) {
                String buildof = Utils.inputStream2String(installerJar.getInputStream(buildofText)).trim();


                if ("cpw.mods.bootstraplauncher.BootstrapLauncher".equals(headJSONObject.optString("mainClass"))) {
                    try {
                        String[] s = buildof.split("-");
                        if (s.length >= 2) {
                            if (Integer.parseInt(s[0]) < 20210924 || (Integer.parseInt(s[0]) == 20210924 && Integer.parseInt(s[1]) < 190833)) {
                                throw new Exception(getString("INSTALL_OPTIFINE_INCOMPATIBLE_WITH_FORGE_17"));
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception(getString("INSTALL_MODLOADER_FAILED_WITH_REASON", EXTRA_NAME, e));
        }


        List<JSONObject> returns = new LinkedList<>();
        if (!containsLW) {
            SplitLibraryName s = new SplitLibraryName("net.minecraft", "launchwrapper", "1.12");
            JSONObject library = new JSONObject().put("name", s.toString());
            librariesArray.put(library);
            if (s.getPhysicalFile().length() == 0)
                returns.add(library);
        }


        String minecraftArguments = headJSONObject.optString("minecraftArguments");
        if (!isEmpty(minecraftArguments)) {
            headJSONObject.put("minecraftArguments", "--tweakClass optifine.OptiFineTweaker " + minecraftArguments);
        } else {
            JSONObject arguments = headJSONObject.optJSONObject("arguments");
            if (arguments == null) headJSONObject.put("arguments", arguments = new JSONObject());
            JSONArray game = arguments.optJSONArray("game");
            if (game == null) arguments.put("game", game = new JSONArray());
            game.put("--tweakClass").put("optifine.OptiFineTweaker");
        }

        String mainClass = headJSONObject.optString("mainClass");
        if (!"cpw.mods.bootstraplauncher.BootstrapLauncher".equals(mainClass)
                && !"cpw.mods.modlauncher.Launcher".equals(mainClass))
            headJSONObject.put("mainClass", "net.minecraft.launchwrapper.Launch");
        JSONObject optifine = new JSONObject();
        optifine.put("version", optiFineVersion);
        optifine.put("jarUrl", url);
        headJSONObject.put("optifine", optifine);
        return new Pair<>(true, returns);
    }


    private static String selectOptiFineVersion(String text, Map<String, JSONObject> optiFines) {
        System.out.print(text);//legal
        Scanner scanner = new Scanner(System.in);
        try {
            String s = scanner.nextLine();
            if (!isEmpty(s)) {
                JSONObject jsonObject = optiFines.get(s);
                if (jsonObject != null) return s;
                return selectOptiFineVersion(getString("INSTALL_MODLOADER_SELECT_NOT_FOUND", s, EXTRA_NAME), optiFines);
            } else {
                return selectOptiFineVersion(text, optiFines);
            }
        } catch (NoSuchElementException ignore) {
            return null;
        }
    }
}
