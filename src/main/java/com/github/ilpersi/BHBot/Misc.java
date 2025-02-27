package com.github.ilpersi.BHBot;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * @author Betalord
 */
public class Misc {

    private static final Class<Misc> miscClass = Misc.class;
    static String machineName = "";

    /**
     * This is the lord of the Screenshots: one method to rule them all. All the screenshots can have a customized prefix
     * and the rest of the name is standard. It includes the date and optionally the machine name. This method will also
     * take care of conflicting filenames adding a _<num> suffix to the filename.
     *
     * @param prefix The prefix that will be included in the screenshot file name
     * @param subFolder A sub folder inside the screenshotPath one where the file will be saved. It can be null.
     * @param includeMachineName Set to true if you want to include machine name in screenshot file name.
     * @param img The image you want to save
     * @return The final path of the saved image. If it was not possible to save the image, null is returned instead.
     */
    synchronized static String saveScreen(String prefix, @Nullable String subFolder, boolean includeMachineName, BufferedImage img) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        // sub-folder logic management
        String screenshotPath = BHBotUnity.screenshotPath;
        if (subFolder != null) {
            String fullPathName = BHBotUnity.screenshotPath + subFolder;
            if (!fullPathName.endsWith("/")) fullPathName += "/";

            File subFolderPath = new File(fullPathName);
            if (!subFolderPath.exists()) {
                if (!subFolderPath.mkdirs()) {
                    BHBotUnity.logger.error("Impossible to create screenshot sub folder in " + subFolder);
                    return null;
                } else {
                    try {
                        BHBotUnity.logger.info("Created screenshot sub-folder " + subFolderPath.getCanonicalPath());
                    } catch (IOException e) {
                        BHBotUnity.logger.error("Error while getting Canonical Path for newly created screenshots sub-folder", e);
                    }
                }
            }
            screenshotPath += subFolder + "/";
        }

        String machineName = "";

        if (includeMachineName) {
            machineName = Misc.getMachineName();

            // We make sure that no weird characters are part of the file name
            machineName = machineName.replaceAll("[^a-zA-Z0-9.-]", "");

            // We add the "_" suffix so that in the final name the host name is separated from the date
            if (machineName.length() > 0)
                machineName += "_";
        }

        prefix += "_";

        Date date = new Date();

        String name = prefix + machineName + dateFormat.format(date) + ".png";
        int num = 0;
        File f = new File(screenshotPath + name);
        while (f.exists()) {
            num++;
            name = prefix + machineName + dateFormat.format(date) + "_" + num + ".png";
            f = new File(screenshotPath + name);
        }

        // save screen shot:
        try {
            ImageIO.write(img, "png", f);
        } catch (Exception e) {
            BHBotUnity.logger.error("Impossible to take a screenshot!");
        }

        return f.getPath();
    }

    /**
     * Use this method if you want to take multiple screenshots with a delay.
     * This is useful for changing cues as the generated output screens can be used together with CueCompare class
     *
     * @param prefix The prefix that the continous shots will share
     * @param duration for how long should we take screenshots? Unit is milliseconds
     * @param delay how much time should we wait between each screenshot
     * @param bot a BrowserManager used to capture the screenshots
     */
    synchronized static void saveContinuousShot(String prefix, long duration, int delay, BrowserManager bot) {
        int shotCnt = 0;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String startDate = simpleDateFormat.format(new Date());

        long timeout = Misc.getTime() + duration;

        do {
            shotCnt++;
            bot.readScreen();
            Misc.saveScreen(startDate + "-" + prefix + "-" + shotCnt, "continuous-screenshots", BHBotUnity.includeMachineNameInScreenshots, bot.getImg());
            Misc.sleep(delay);
        } while (Misc.getTime() <= timeout);
    }

    static final class Durations {
        static final int SECOND = 1000;
        static final int MINUTE = 60 * SECOND;
        static final int HOUR = 60 * MINUTE;
        static final int DAY = 24 * HOUR;
        static final int WEEK = 7 * DAY;
    }

    static final class BarOffsets {
        static final Point Badge = new Point(0, 8);
        static final Point DUNGEON = new Point(0, 21);
        static final Point PVP = new Point(0, 6);
        static final Point RAID = new Point(0, 9);
        static final Point TG = new Point(0, 23);
        static final Point WB = new Point(0, 26);
    }

    static final class BoundsOffsets {
        static final Bounds Difficulty = Bounds.fromWidthHeight(20, 32, 90, 31);
        static final Bounds Cost = Bounds.fromWidthHeight(0, 41, 34, 22);
    }

    static final Bounds SIGNATURE_BOUNDS = Bounds.fromWidthHeight(90, 400, 70, 60);

    /**
     * Return time in milliseconds from the start of the system. Can have a negative value.
     */
    static long getTime() {
        return System.currentTimeMillis();
    }

    static String getStackTrace() {
        StringBuilder r = new StringBuilder();

        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            r.append(ste).append("\n");
        }

        return r.toString();
    }

    static List<String> fileToList(String file) throws FileNotFoundException {
        List<String> lines = new ArrayList<>();
        BufferedReader br;

        br = new BufferedReader(new FileReader(file));
        try {
            String line = br.readLine();
            while (line != null) {
                lines.add(line);
                line = br.readLine();
            }
            br.close();

            return lines;
        } catch (IOException e) {
            BHBotUnity.logger.error("Impossible to read file: " + file, e);
            return null;
        }
    }

    /**
     * Returns true on success.
     */
    static boolean saveTextFile(String file, String contents) {
        BufferedWriter bw;

        try {
            File f = new File(file);
            // create parent folder(s) if needed:
            File parent = f.getParentFile();
            if (parent != null && !parent.exists())
                if (!parent.mkdirs()) {
                    BHBotUnity.logger.error("Error with parent.mkdirs() in saveTetFile!");
                    return false;
                }

            bw = new BufferedWriter(new FileWriter(f));
            try {
                bw.write(contents);
            } finally {
                bw.close();
            }
        } catch (IOException e) {
            BHBotUnity.logger.error("saveTextFile could not save contents in file: " + file, e);
            return false;
        }
        return true;
    }

    static String millisToHumanForm(Long millis) {

        // milliseconds
        long millisecs = millis % 1000;
        // seconds
        long seconds = millis / 1000;
        // minutes
        long minutes = seconds / 60;
        seconds = seconds % 60;
        // hours
        long hours = minutes / 60;
        minutes = minutes % 60;
        // days
        long days = hours / 24;
        hours = hours % 24;

        if (millisecs == 0 && seconds == 0 && minutes == 0 && hours == 0 && days == 0)
            return "0s";

        StringBuilder humanStringBuilder = new StringBuilder();
        if (days > 0) humanStringBuilder.append(String.format("%dd", days));
        if (hours > 0) humanStringBuilder.append(String.format(" %dh", hours));
        if (minutes > 0) humanStringBuilder.append(String.format(" %dm", minutes));
        if (seconds > 0) humanStringBuilder.append(String.format(" %ds", seconds));
        if (millisecs > 0) humanStringBuilder.append(String.format(".%dms", millisecs));

        return humanStringBuilder.toString().trim();
    }

    static int max(int... values) {
        int max = Integer.MIN_VALUE;
        for (int value : values)
            if (value > max)
                max = value;
        return max;
    }

    static int min(int... values) {
        int min = Integer.MAX_VALUE;
        for (int value : values)
            if (value < min)
                min = value;
        return min;
    }

    /**
     * Returns index of closest match from the 'values' array.
     */
    static int findClosestMatch(int[] values, int value) {
        int best = Integer.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - value) < best) {
                best = Math.abs(values[i] - value);
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    static String listToString(EnumSet<?> list) {
        StringBuilder r = new StringBuilder();
        for (Object e : list)
            r.append(e).append(", ");
        r = new StringBuilder(r.substring(0, r.length() - 2));
        return r.toString();
    }

    static String encodeFileToBase64Binary(File toEncode) {

        byte[] encoded;
        try {
            encoded = Base64.getEncoder().encode(Files.readAllBytes(Paths.get(toEncode.getAbsolutePath())));
        } catch (IOException e) {
            BHBotUnity.logger.error("Error in encodeFileToBase64Binary", e);
            return "";
        }
        return new String(encoded, StandardCharsets.US_ASCII);
    }

    static int[][] convertTo2D(BufferedImage image) {

        final int w = image.getWidth();
        final int h = image.getHeight();

        int[][] pixels = new int[w][h];

        for (int i = 0; i < w; i++)
            for (int j = 0; j < h; j++)
                pixels[i][j] = image.getRGB(i, j);

        return pixels;
    }

    static long classBuildTimeMillis() throws URISyntaxException, IllegalStateException, IllegalArgumentException {
        URL resource = miscClass.getResource(miscClass.getSimpleName() + ".class");
        if (resource == null) {
            throw new IllegalStateException("Failed to find class file for class: " +
                    miscClass.getName());
        }

        if (resource.getProtocol().equals("file")) {

            return new File(resource.toURI()).lastModified();

        } else if (resource.getProtocol().equals("jar")) {

            String path = resource.getPath();
            return new File(path.substring(5, path.indexOf("!"))).lastModified();

        } else {

            throw new IllegalArgumentException("Unhandled url protocol: " +
                    resource.getProtocol() + " for class: " +
                    miscClass.getName() + " resource: " + resource);
        }
    }

    static Properties getGITInfo() {
        Properties properties = new Properties();
        try {
            InputStream gitResource = miscClass.getClassLoader().getResourceAsStream("git.properties");
            if (gitResource != null) {
                properties.load(gitResource);
            }
        } catch (IOException e) {
            BHBotUnity.logger.error("Impossible to get GIT information", e);
        }
        return properties;
    }

    static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            BHBotUnity.logger.debug("Interrupting sleep");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * This method is intended to be used by developers to fastly get all the positions of a scrolling bar.
     * This can be used when new content is released and there is the need to re-calculate bar positions
     *
     * @param bot An initialized BHBot instance
     * @param takeScreen should we take a screen of the current bar position?
     * @author ilpersi
     */
    static void findScrollBarPositions(BHBotUnity bot, boolean takeScreen) {
        int lastPosition = -1;

        ArrayList<Integer> positions = new ArrayList<>();

        bot.browser.readScreen(0);

        MarvinSegment segDropDown = MarvinSegment.fromCue("DropDownDown", 5 * Misc.Durations.SECOND, null, bot.browser);
        if (segDropDown == null) {
            BHBotUnity.logger.error("Error: unable to find down arrow findScrollBarPositions!");
            return;
        }

        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("StripScrollerTopPos"), 2 * Misc.Durations.SECOND, bot.browser);
        Cue topPos = seg == null ? BHBotUnity.cues.get("SettingsScrollerTopPos") : BHBotUnity.cues.get("StripScrollerTopPos");

        int posCnt = 0;

        while (true) {
            MarvinSegment scrollTopSeg = MarvinSegment.fromCue(topPos, 2 * Misc.Durations.SECOND, bot.browser);

            if (scrollTopSeg == null) {
                BHBotUnity.logger.error("Error: unable to find scroller in findScrollBarPositions!");
                return;
            }

            if (scrollTopSeg.y1 == lastPosition) {
                break;
            } else {
                lastPosition = scrollTopSeg.y1;
                positions.add(scrollTopSeg.y1);
                posCnt += 1;

                if (takeScreen) {
                    String screenName = String.format("scroller_%02d", posCnt);
                    Misc.saveScreen(screenName, "ScrollBarPositions", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
                }

                bot.browser.clickOnSeg(segDropDown);
                bot.browser.moveMouseAway();
                bot.browser.readScreen(Durations.SECOND / 2);
            }
        }

        StringBuilder posOutput = new StringBuilder("{");
        for (Integer pos : positions) {
            if (posOutput.length() > 1) posOutput.append(", ");

            posOutput.append(pos);
        }
        posOutput.append("}");
        BHBotUnity.logger.info(posOutput.toString());

        if (takeScreen) {
            BHBotUnity.logger.info("Saved " + posCnt + " screens for scrollbar positions");
        }

    }

    /**
     * This method is taking care of managing image contributions.
     * Image contributions are used to get cues that are difficult to gather, e.g.: rune cues, familiar cues
     * When calling this method, always make sure not to pass an image containing sensitive data
     *  @param img     The BufferedImage to be contributed to the project
     * @param imgName The name the buffered image will have once it is uploaded
     * @param subArea If you only want to specify a sub area of the image, pass the subArea parameter,
     * @return true if contribution was successful
     */
    static boolean contributeImage(BufferedImage img, String imgName, Bounds subArea) {

        // we generate a sub image based on the bounds
        BufferedImage subImg;
        if (subArea != null)
            subImg = img.getSubimage(subArea.x1, subArea.y1, subArea.width, subArea.height);
        else
            subImg = img;

        // We strip any png extension to avoid weird names
        imgName = imgName.replace(".png", "");


        File nameImgFile = new File(imgName + "-ctb.png");
        try {
            ImageIO.write(subImg, "png", nameImgFile);
        } catch (IOException e) {
            BHBotUnity.logger.error("Error while creating rune contribution file", e);
        }

        String encodedContent = Misc.encodeFileToBase64Binary(nameImgFile);

        if ("".equals(encodedContent)) {
            BHBotUnity.logger.debug("It was impossible to contribute image: " + imgName);
            return false;
        }

        HashMap<Object, Object> data = new HashMap<>();
        data.put("mimeType", "image/png");
        data.put("name", nameImgFile.getName());
        data.put("data", encodedContent);
        data.put("MD5", Misc.imgToMD5(subImg));
        data.put("isUnityEngine", true);

        String postBody = Misc.formEncode(data);

        // Follow redirects does not work with HTTP 2.0
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        // We make sure to pass the proper content-type
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(postBody))
                .uri(URI.create("https://script.google.com/macros/s/AKfycby-tCXZ6MHt_ZSUixCcNbYFjDuri6WvljomLgGy_m5lLZw1y5fZ/exec"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try {
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            BHBotUnity.logger.error("Exception while contributing Image " + imgName, e);
        }

        if (!nameImgFile.delete()) {
            BHBotUnity.logger.error("Impossible to delete contribution image: " + nameImgFile.getAbsolutePath());
        }

        return true;
    }

    /**
     * This method will take care of formatting hashmaps into encoded form data
     *
     * @param data The HashMap to be encoded
     * @return HTTP encoded string in the format key1=value1 ready to be used in HTTP requests
     */
    static String formEncode(HashMap<Object, Object> data) {
        StringBuilder postBody = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (postBody.length() > 0) postBody.append("&");

            postBody.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }

        return postBody.toString();
    }

    /**
     * @param img The BufferedImage that you want to get the hash for
     * @return an array of bytes that contains the MD5 hash
     */
    static String imgToMD5(BufferedImage img) {
        if (img == null) return "";

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", outputStream);
        } catch (IOException e) {
            BHBotUnity.logger.error("imgToMd5: impossible to write image to outputStream", e);
            return "";
        }
        byte[] data = outputStream.toByteArray();

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            BHBotUnity.logger.error("imgToMd5: error while digesting MD5 hash", e);
            return "";
        }
        md.update(data);
        return Base64.getEncoder().encodeToString(md.digest());
    }

    /**
     * Get the current hostname and cache it
     *
     * @return The hostname of the machine the bot is running on.
     */
    static String getMachineName() {

        if ("".equals(Misc.machineName)) {
            final int loopLimit = 10;
            int loopCnt = 0;

            // Sometimes more than one attempt is required to correctly get the machine name.
            while ("".equals(Misc.machineName) && loopCnt < loopLimit) {

                try {
                    InetAddress localMachine = InetAddress.getLocalHost();
                    Misc.machineName = localMachine.getHostName();
                } catch (UnknownHostException e) {
                    BHBotUnity.logger.warn("Impossible to get local host information.", e);
                }

                loopCnt++;

            }
        }

        return Misc.machineName;
    }
}
