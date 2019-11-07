package com.cybercat3.minecraft_world_to_dedicated_server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecraftManager {
    private static final String MC_VERSIONS = "https://www.mcversions.net";
    private static List<MinecraftVersion> versions = null;
    private static HashMap<String, String> urlFromVersion = null;

    public static void URLToOutputStream(String _url, OutputStream os, boolean closeOSWhenDone) throws IOException {
        URL url;
        try {url = new URL(_url);}
        catch (MalformedURLException e) {throw new IOException(e);}

        try {
            URLConnection conn = url.openConnection();
            conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

            try (InputStream is = conn.getInputStream()) {
                int bufferSize = 16384;
                byte[] buffer = new byte[bufferSize];
                int bytesRead;

                while ( ( bytesRead = is.read(buffer) ) != -1 ) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            os.flush();
        } finally {
            if (closeOSWhenDone) os.close();
        }
    }

    private MinecraftManager() {}

    public static List<MinecraftVersion> getVersions() throws IOException {
        if (versions != null) return new ArrayList<>(versions);


        String file = "If this shows up, I better work on my control-flow";
        {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 400);) {
                URLToOutputStream(MC_VERSIONS, baos, false);
                    file = new String(baos.toByteArray());

            } catch (IOException e) {
                throw new IOException("Error in downloading version list");
            }
        }

        Pattern p = Pattern.compile("<a href=\"(.*?)\" class=\"(.*?)\" download=\"(.*?)\">Server Jar</a>");
        Pattern versionMatcher = Pattern.compile("minecraft_server-(\\d+\\.)+(\\d+)\\.jar");
        Matcher m = p.matcher(file);
        versions = new ArrayList<>();
        urlFromVersion = new HashMap<>();
        while (m.find()) {
            String link = m.group(1);
            String minecraftVersion = m.group(3);
            if (versionMatcher.matcher(minecraftVersion).find()) {
                MinecraftVersion mc = new MinecraftVersion(link, minecraftVersion);
                versions.add(mc);
                urlFromVersion.put(mc.toString(), mc.getLinkToDownload());
            }
        }
        versions.sort(Comparator.reverseOrder());
        
        return new ArrayList<>(versions);
    }
    public static HashMap<String, String> getUrlFromVersion() throws IOException {
        if (urlFromVersion == null) getVersions();
        return new HashMap<>(urlFromVersion);
    }

    public static class MinecraftVersion implements Comparable<MinecraftVersion> {
        private int[] version;
        private String linkToDownload;

        private MinecraftVersion(int... version) {
            this.version = version.clone();
        }
        private MinecraftVersion(String linkToDownload, int... version) {
            this(version);
            this.linkToDownload = linkToDownload;
        }
        private MinecraftVersion(String linkToDownload, String version) {
            this(linkToDownload, constructorHelper(version));
        }
        private static int[] constructorHelper(String version) {
            Pattern onlyVersion = Pattern.compile("(\\d+\\.)+(\\d+)");
            Matcher m = onlyVersion.matcher(version);
            if (m.find()) {
                String[] stringDigits = m.group().split("\\.");
                int[] digits = new int[stringDigits.length];
                for (int i = 0; i < digits.length; ++i) {
                    digits[i] = Integer.parseInt(stringDigits[i]);
                }
                return digits;
            }
            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
//            sb.append("MCVersion{");
            for (int i = 0; i < version.length; ++i) {
                sb.append(version[i]);
                if (i != version.length - 1)
                    sb.append(".");
            }
//            sb.append(", ");
//            sb.append(linkToDownload);
//            sb.append("}");
            return sb.toString();
        }

        public String getLinkToDownload() {
            return linkToDownload;
        }

        @Override
        public int compareTo(MinecraftVersion mc) {
            int largestArraySize = Math.max(this.version.length, mc.version.length);
            for (int i = 0; i < largestArraySize; ++i) {
                int x = getFromIntArray(this.version, i);
                int y = getFromIntArray(mc.version, i);

                int z = Integer.compare(x, y);
                if (z != 0) return z;
            }
            return 0;
        }

        private int getFromIntArray(int[] arr, int index) {
            if (index >= arr.length) return 0;
            return arr[index];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MinecraftVersion that = (MinecraftVersion) o;
            return Arrays.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(version);
        }
    }

}
