package com.cybercat3.minecraft_world_to_dedicated_server;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Controller {
    @FXML public Label workingText;
    @FXML public Label worldLabel;
    @FXML public Label serverLabel;
    @FXML public VBox workingBox;
    @FXML public ComboBox<String> versionPicker;
    @FXML public Button worldButton;
    @FXML public Button serverButton;
    @FXML public Button convertButton;

    private List<MinecraftManager.MinecraftVersion> versions;
    private HashMap<String, String> UrlFromVersion;
    private File world;
    private File server;

    private static final String START_BAT = "start.bat";
    private static final String START_BAT_NO_GUI = "start_nogui.bat";
    private static final String MINECRAFT_SERVER_JAR = "minecraft_server.jar";

    private static HashMap<String, String> colorMap = new HashMap<>();
    static {
        colorMap.put("lightGreen", "rgb(100,255,100)");
        colorMap.put("lightBlue", "lightBlue");
        colorMap.put("lightRed", "rgb(255,100,100)");
    }

    private boolean isConverting = false;

    public void initialize(Stage primaryStage) {

        // Getting versions
        Thread t = new Thread() {
            private boolean fetchVersions() {
                Platform.runLater(() -> setMessage("Fetching versions...", colorMap.get("lightBlue")));
                try {
                    versions = MinecraftManager.getVersions();
                    UrlFromVersion = MinecraftManager.getUrlFromVersion();
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
                Platform.runLater(() -> {
                    setMessage("Fetched versions!", colorMap.get("lightGreen"));
                    versionPicker.getItems().addAll(versions.stream().map(MinecraftManager.MinecraftVersion::toString).collect(Collectors.toList()));
                    versionPicker.setValue(versions.get(0).toString());
                    versionPicker.setDisable(false);
                });
                return true;
            }
            @Override
            public void run() {
                boolean lastOutcome = false;
                while (true) {
                    lastOutcome = fetchVersions();
                    if (lastOutcome) break;
                    Controller.sleep(1000);
                    for (int i = 10; i >= 1; --i) {
                        int finalI = i;
                        Platform.runLater(() -> setMessage("Version list fetch failed, trying again in "+finalI +" seconds...", colorMap.get("lightRed")));
                        Controller.sleep(1000);
                    }
                }
            }
        };
        t.setDaemon(true);
        t.start();

        // Sets up select source button
        worldButton.setOnAction(event -> {
            DirectoryChooser fc = new DirectoryChooser();
            fc.setTitle("Select World to Convert");
            if (world != null) {
                fc.setInitialDirectory(world.getParentFile());
            }
            File file = fc.showDialog(primaryStage);
            if (file != null) {
                world = file;
                worldLabel.setText(stringShorter(file.toString(),32));
                checkIfValidConfiguration();
            }
        });

        // Sets up target button
        serverButton.setOnAction(event -> {
            DirectoryChooser fc = new DirectoryChooser();
            fc.setTitle("Select World to Convert");
            if (server != null) {
                fc.setInitialDirectory(server.getParentFile());
            }
            File file = fc.showDialog(primaryStage);
            if (file != null) {
                server = file;
                serverLabel.setText(stringShorter(file.toString(),32));
                checkIfValidConfiguration();
            }
        });

        // Sets up convert button
        convertButton.setOnAction(event -> {
            convertButton.setDisable(true);
            isConverting = true;
            Thread t2 = new Thread(() -> {
                try {
                    Platform.runLater(() -> setMessage("Downloading Server File...", colorMap.get("lightBlue")));
                if (!downloadServerHelper()) return;
                    Platform.runLater(() -> setMessage("Copying World...", colorMap.get("lightBlue")));
                if (!copyDirectory(world, server)) return;
                    Platform.runLater(() -> setMessage("Creating launcher script...", colorMap.get("lightBlue")));
                    if (!createLauncherFileHelper()) return;
                    Platform.runLater(() -> setMessage("Initializing server...", colorMap.get("lightBlue")));
                    if (!initializeServer()) return;
                    Platform.runLater(() -> setMessage("Agreeing to EULA...", colorMap.get("lightBlue")));
                    if (!agreeToEula()) return;
                    Platform.runLater(() -> setMessage("Reinitializing server...", colorMap.get("lightBlue")));
                    if (!initializeServer()) return;
                    Platform.runLater(() -> setMessage("Finished!", colorMap.get("lightGreen")));
                } finally {
                    Platform.runLater(() -> {
                        convertButton.setDisable(false);
                        isConverting = false;
                    });
                }
            });
            t2.setDaemon(true);
            t2.setName("World to Dedicated Server Converter Thread");
            t2.start();
        });
    }

    private boolean agreeToEula() {
        try {
            Controller.sleep(1000);
            File eula = new File(server, "eula.txt");
            String eulaString = readFile(eula);
            System.out.println("eulaString = " + eulaString);
            eulaString = eulaString.replaceAll("false", "true");
            System.out.println("eulaString = " + eulaString);
            writeFile(eula, eulaString);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Platform.runLater(() -> setMessage("Couldn't agree to EULA.", colorMap.get("lightRed")));
            return false;
        }
    }
    private String readFile(File f) throws IOException {
        char[] characters = new char[(int) f.length()];
        int bytesRead;
        try (FileReader fr = new FileReader(f)) {
            bytesRead = fr.read(characters);
        }
        return new String(characters, 0, bytesRead);
    }
    private void writeFile(File f, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean initializeServer() {
        try {
            ProcessBuilder pb = new ProcessBuilder(new File(server, START_BAT_NO_GUI).getAbsolutePath());
            pb.directory(server);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process p = pb.start();

            InputStream pis = p.getInputStream();
            int currByte;

            LinkedList<Character> lastFourCharacters = new LinkedList<>();
            LinkedList<Character> done = new LinkedList<>(Arrays.asList('D', 'o', 'n', 'e'));

            while ( (currByte = pis.read()) != -1 ) {
                char byteChar = (char) currByte;
                System.out.print(byteChar);
                lastFourCharacters.add(byteChar);
                if (lastFourCharacters.size() > 4) {
                    lastFourCharacters.removeFirst();
                }
                if (lastFourCharacters.equals(done)) {
                        System.out.println("SERVER IS DONE");
                        Controller.sleep(500);
                        Platform.runLater(() -> setMessage("Stopping server...", colorMap.get("lightBlue")));
                    p.getOutputStream().write("stop\n".getBytes());
                    p.getOutputStream().flush();
                }
            }

            try {p.waitFor();}
            catch (InterruptedException ignored) {}

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Platform.runLater(() -> setMessage("Couldn't initialize server.", colorMap.get("lightRed")));
            return false;
        }
    }

    private boolean createLauncherFileHelper() {
        for (int i = 0; i <= 20; ++i) {
            Controller.sleep(1200);
            if (createLauncherFile() && createLauncherFileNoGUI()) return true;
        }
        Platform.runLater(() -> setMessage("Couldn't create launcher.", colorMap.get("lightRed")));
        return false;
    }
    private boolean createLauncherFile() {
        try (FileOutputStream fos = new FileOutputStream(new File(server, START_BAT))) {
            fos.write(("java -Xmx4G -Xms2048m -Xmn2048m -XX:+DisableExplicitGC -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+UseNUMA -XX:+CMSParallelRemarkEnabled -XX:MaxTenuringThreshold=15 -XX:MaxGCPauseMillis=30 -XX:GCPauseIntervalMillis=150 -XX:+UseAdaptiveGCBoundary -XX:-UseGCOverheadLimit -XX:+UseBiasedLocking -XX:SurvivorRatio=8 -XX:TargetSurvivorRatio=90 -XX:MaxTenuringThreshold=15 -Dfml.ignorePatchDiscrepancies=true -Dfml.ignoreInvalidMinecraftCertificates=true -XX:+UseFastAccessorMethods -XX:+UseCompressedOops -XX:+OptimizeStringConcat -XX:+AggressiveOpts -XX:ReservedCodeCacheSize=2048m -XX:+UseCodeCacheFlushing -XX:SoftRefLRUPolicyMSPerMB=20000 -XX:ParallelGCThreads=10 -jar "+MINECRAFT_SERVER_JAR).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean createLauncherFileNoGUI() {
        try (FileOutputStream fos = new FileOutputStream(new File(server, START_BAT_NO_GUI))) {
            fos.write(("java -Xmx4G -Xms2048m -Xmn2048m -XX:+DisableExplicitGC -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+UseNUMA -XX:+CMSParallelRemarkEnabled -XX:MaxTenuringThreshold=15 -XX:MaxGCPauseMillis=30 -XX:GCPauseIntervalMillis=150 -XX:+UseAdaptiveGCBoundary -XX:-UseGCOverheadLimit -XX:+UseBiasedLocking -XX:SurvivorRatio=8 -XX:TargetSurvivorRatio=90 -XX:MaxTenuringThreshold=15 -Dfml.ignorePatchDiscrepancies=true -Dfml.ignoreInvalidMinecraftCertificates=true -XX:+UseFastAccessorMethods -XX:+UseCompressedOops -XX:+OptimizeStringConcat -XX:+AggressiveOpts -XX:ReservedCodeCacheSize=2048m -XX:+UseCodeCacheFlushing -XX:SoftRefLRUPolicyMSPerMB=20000 -XX:ParallelGCThreads=10 -jar "+MINECRAFT_SERVER_JAR+" nogui").getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean downloadServerHelper() {
        boolean successfulDownload = false;
        for (int j = 0; j < 5; ++j) {
            successfulDownload = downloadServer(server);
            if (successfulDownload) return true;
            Controller.sleep(1000);
            for (int i = 10; i >= 1; --i) {
                int finalI = i;
                Platform.runLater(() -> setMessage("Couldn't get Minecraft server, trying again in "+finalI +" seconds...", colorMap.get("lightRed")));
                Controller.sleep(1000);
            }
        }
        Platform.runLater(() -> setMessage("Failed.", colorMap.get("lightRed")));
        return false;
    }
    private boolean downloadServer(File serverFolder) {
        try (FileOutputStream out = new FileOutputStream(new File(serverFolder, MINECRAFT_SERVER_JAR))) {
            String url = UrlFromVersion.get(versionPicker.getValue());
            System.out.println("url = " + url);
            MinecraftManager.URLToOutputStream(url, out, false);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean copyDirectory(File source, File target) {
        File worldFolderInServer = new File(target, "world");
        //noinspection ResultOfMethodCallIgnored
        worldFolderInServer.mkdirs();

        String[] files = source.list();
        if (files == null) {
            Platform.runLater(() -> setMessage("Source Folder Doesn't exist!", colorMap.get("lightRed")));
            return false;
        }

        Arrays.stream(files).parallel().forEach(f -> {
            copyDirectoryRecursiveHelper(source, worldFolderInServer, f);
            System.out.println(f);
        });
        return true;
    }
    private void copyDirectoryRecursiveHelper(File source, File target, String f) {
        File src = new File(source, f);
        File tgt = new File(target, f);
        if (src.isFile()) {
            tgt.getParentFile().mkdirs();
            copyFile(src, tgt);
        } else {
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectoryRecursiveHelper(src, tgt, child);
                }
            }
        }
    }
    private void copyFile(File source, File target) {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(target)) {

            int bufferSize = 8192;
            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            while ( ( bytesRead = fis.read(buffer) ) != -1 ) {
                fos.write(buffer, 0, bytesRead);
            }
        } catch (IOException ignored) {}
    }

    private void checkIfValidConfiguration() {
        if (isConverting) return;
        if (world == null || server == null) {
            convertButton.setDisable(true);
            return;
        }
        if (!world.exists() || !server.exists()) {
            convertButton.setDisable(true);
            return;
        }

        convertButton.setDisable(false);
    }

    private String stringShorter(String input, int charLimit) {
        return input.length() > charLimit ?
                "..." + input.substring(
                        input.length() - charLimit + 3,
                        input.length()
                ) :
                input;
    }

    private void setMessage(String message) {
        workingText.setText(message);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {}
    }

    private void setMessage(String message, String color) {
        workingBox.setStyle("-fx-background-color: " + color + ";");
        setMessage(message);
    }
}
