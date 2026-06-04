package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class VideoService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path extractThumbnail(Path src, int second) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".jpg");
        VideoAttributes video = new VideoAttributes();
        video.setCodec("mjpeg");
        video.setFrameRate(1);
        video.setBitRate(800_000);
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("image2");
        attrs.setOffset((float) second);
        attrs.setDuration(1.0f);
        attrs.setVideoAttributes(video);
        new Encoder().encode(new MultimediaObject(src.toFile()), out.toFile(), attrs);
        return out;
    }

    public Path videoToGif(Path src, int startSecond, int duration, int fps) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".gif");
        VideoAttributes video = new VideoAttributes();
        video.setFrameRate(Math.min(fps, 15));
        // No forced size — let JAVE2 use source dimensions at reduced frame rate
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("gif");
        attrs.setOffset((float) startSecond);
        attrs.setDuration((float) Math.min(duration, 10));
        attrs.setVideoAttributes(video);
        new Encoder().encode(new MultimediaObject(src.toFile()), out.toFile(), attrs);
        return out;
    }

    public Path extractAudio(Path src) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp3");
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libmp3lame");
        audio.setBitRate(192_000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp3");
        attrs.setAudioAttributes(audio);
        new Encoder().encode(new MultimediaObject(src.toFile()), out.toFile(), attrs);
        return out;
    }

    /** Convert audio to a different format. Supported targets: mp3, wav, ogg, aac, flac, m4a. */
    public Path convertAudio(Path src, String targetFormat) throws Exception {
        String fmt = targetFormat == null || targetFormat.isBlank() ? "mp3" : targetFormat.toLowerCase();
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + fmt);
        AudioAttributes audio = new AudioAttributes();
        String codec = switch (fmt) {
            case "mp3"       -> "libmp3lame";
            case "ogg"       -> "libvorbis";
            case "aac","m4a" -> "aac";
            case "flac"      -> "flac";
            default          -> "pcm_s16le"; // wav
        };
        audio.setCodec(codec);
        audio.setBitRate(192_000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat(fmt.equals("m4a") ? "ipod" : fmt);
        attrs.setAudioAttributes(audio);
        new Encoder().encode(new MultimediaObject(src.toFile()), out.toFile(), attrs);
        return out;
    }

    /** Trim video: extract [startSec, startSec+durationSec]. */
    public Path trimVideo(Path src, int startSec, int durationSec) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp4");
        VideoAttributes video = new VideoAttributes();
        video.setCodec("libx264");
        video.setBitRate(1_500_000);
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("aac");
        audio.setBitRate(128_000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp4");
        attrs.setOffset((float) startSec);
        attrs.setDuration((float) Math.max(1, durationSec));
        attrs.setVideoAttributes(video);
        attrs.setAudioAttributes(audio);
        new Encoder().encode(new MultimediaObject(src.toFile()), out.toFile(), attrs);
        return out;
    }

    /** Re-encode video at lower bitrate. quality 0-100; lower = smaller file. */
    public Path compressVideo(Path src, int quality) throws Exception {
        // quality maps 0-100 → bitrate 200k-4000k
        int bitrate = 200_000 + (int)(quality / 100.0 * 3_800_000);
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp4");
        VideoAttributes video = new VideoAttributes();
        video.setCodec("libx264");
        video.setBitRate(bitrate);
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("aac");
        audio.setBitRate(128_000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp4");
        attrs.setVideoAttributes(video);
        attrs.setAudioAttributes(audio);
        new Encoder().encode(new MultimediaObject(src.toFile()), out.toFile(), attrs);
        return out;
    }

    /** Convert any video to MP4 (H.264 + AAC). */
    public Path convertToMp4(Path src) throws Exception {
        return compressVideo(src, 70);
    }

    /**
     * Extract one frame every {@code intervalSec} seconds as PNG images, zip them.
     * Uses JAVE2 image2 format.
     */
    public List<Path> extractFrames(Path src, int intervalSec) throws Exception {
        int fps = Math.max(1, intervalSec) == 1 ? 1 : 1; // 1 frame per intervalSec
        Path frameDir = Paths.get(outputDir, "frames-" + UUID.randomUUID());
        java.nio.file.Files.createDirectories(frameDir);
        // Use ffmpeg via ProcessBuilder for frame extraction (JAVE2 doesn't support output patterns)
        List<String> cmd = List.of(
            "ffmpeg", "-y", "-i", src.toAbsolutePath().toString(),
            "-vf", "fps=1/" + Math.max(1, intervalSec),
            frameDir.resolve("frame-%04d.png").toString()
        );
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) throw new java.io.IOException("ffmpeg frame extract failed: " + log);
        return java.nio.file.Files.list(frameDir)
                .filter(f -> f.toString().endsWith(".png"))
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Overlay a text watermark on a video using ffmpeg drawtext filter.
     */
    public Path addVideoWatermark(Path src, String text) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp4");
        String safeText = (text == null || text.isBlank()) ? "WATERMARK" : text.replace("'", "\\'");
        List<String> cmd = List.of(
            "ffmpeg", "-y", "-i", src.toAbsolutePath().toString(),
            "-vf", "drawtext=text='" + safeText + "':fontcolor=white:fontsize=36:alpha=0.6:x=(w-text_w)/2:y=h-th-20",
            "-codec:a", "copy",
            out.toAbsolutePath().toString()
        );
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) throw new java.io.IOException("ffmpeg watermark failed: " + log);
        return out;
    }

    /**
     * Change video playback speed. speed > 1.0 = faster, < 1.0 = slower.
     * Uses ffmpeg setpts + atempo filters.
     */
    public Path changeSpeed(Path src, float speed) throws Exception {
        if (speed <= 0) speed = 1.0f;
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp4");
        float vSpeed = speed;
        float aSpeed = Math.min(Math.max(speed, 0.5f), 2.0f); // atempo only works 0.5-2.0
        // For atempo outside 0.5-2.0, chain multiple filters
        String atempoFilter;
        if (aSpeed >= 0.5f && aSpeed <= 2.0f) {
            atempoFilter = "atempo=" + aSpeed;
        } else if (aSpeed > 2.0f) {
            atempoFilter = "atempo=2.0,atempo=" + (aSpeed / 2.0f);
        } else {
            atempoFilter = "atempo=0.5,atempo=" + (aSpeed / 0.5f);
        }
        List<String> cmd = List.of(
            "ffmpeg", "-y", "-i", src.toAbsolutePath().toString(),
            "-filter_complex", "[0:v]setpts=" + (1.0f/vSpeed) + "*PTS[v];[0:a]" + atempoFilter + "[a]",
            "-map", "[v]", "-map", "[a]",
            out.toAbsolutePath().toString()
        );
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) throw new java.io.IOException("ffmpeg speed change failed: " + log);
        return out;
    }

    /**
     * Split audio file at {@code splitAtSec} seconds into two MP3 files, zipped.
     */
    public List<Path> splitAudio(Path src, int splitAtSec) throws Exception {
        Path part1 = Paths.get(outputDir, UUID.randomUUID() + ".mp3");
        Path part2 = Paths.get(outputDir, UUID.randomUUID() + ".mp3");
        // Part 1: from start to splitAtSec
        AudioAttributes a1 = new AudioAttributes();
        a1.setCodec("libmp3lame"); a1.setBitRate(192_000); a1.setChannels(2); a1.setSamplingRate(44100);
        EncodingAttributes ea1 = new EncodingAttributes();
        ea1.setOutputFormat("mp3"); ea1.setDuration((float) splitAtSec); ea1.setAudioAttributes(a1);
        new Encoder().encode(new MultimediaObject(src.toFile()), part1.toFile(), ea1);
        // Part 2: from splitAtSec to end
        AudioAttributes a2 = new AudioAttributes();
        a2.setCodec("libmp3lame"); a2.setBitRate(192_000); a2.setChannels(2); a2.setSamplingRate(44100);
        EncodingAttributes ea2 = new EncodingAttributes();
        ea2.setOutputFormat("mp3"); ea2.setOffset((float) splitAtSec); ea2.setAudioAttributes(a2);
        new Encoder().encode(new MultimediaObject(src.toFile()), part2.toFile(), ea2);
        return List.of(part1, part2);
    }

    /** Concatenate multiple video files into one MP4 using ffmpeg concat filter. */
    public Path concatVideos(List<Path> sources) throws Exception {
        if (sources.size() == 1) return convertToMp4(sources.get(0));
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp4");
        List<String> cmd = new java.util.ArrayList<>(List.of("ffmpeg", "-y"));
        for (Path s : sources) { cmd.add("-i"); cmd.add(s.toAbsolutePath().toString()); }
        StringBuilder fc = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) fc.append("[").append(i).append(":v][").append(i).append(":a]");
        fc.append("concat=n=").append(sources.size()).append(":v=1:a=1[v][a]");
        cmd.addAll(List.of("-filter_complex", fc.toString(), "-map", "[v]", "-map", "[a]", out.toAbsolutePath().toString()));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IOException("ffmpeg concat failed: " + log);
        return out;
    }

    /** Resize video to given dimensions (use -1 to preserve aspect ratio). */
    public Path resizeVideo(Path src, int width, int height) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp4");
        int w = width  > 0 ? width  : -2;
        int h = height > 0 ? height : -2;
        List<String> cmd = List.of("ffmpeg", "-y", "-i", src.toAbsolutePath().toString(),
            "-vf", "scale=" + w + ":" + h,
            "-c:v", "libx264", "-c:a", "copy",
            out.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IOException("ffmpeg resize failed: " + log);
        return out;
    }

    /** Normalize audio loudness using ffmpeg loudnorm filter. */
    public Path normalizeAudio(Path src) throws Exception {
        String ext = src.getFileName().toString().replaceFirst(".*\\.", "");
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        List<String> cmd = List.of("ffmpeg", "-y", "-i", src.toAbsolutePath().toString(),
            "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
            out.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IOException("ffmpeg normalize failed: " + log);
        return out;
    }

    /** Add fade-in and/or fade-out to audio file. */
    public Path fadeAudio(Path src, int fadeInSec, int fadeOutSec) throws Exception {
        String ext = src.getFileName().toString().replaceFirst(".*\\.", "");
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        StringBuilder af = new StringBuilder();
        if (fadeInSec > 0)  af.append("afade=t=in:d=").append(fadeInSec);
        if (fadeInSec > 0 && fadeOutSec > 0) af.append(",");
        if (fadeOutSec > 0) af.append("afade=t=out:st=0:d=").append(fadeOutSec);
        if (af.length() == 0) af.append("anull");
        List<String> cmd = List.of("ffmpeg", "-y", "-i", src.toAbsolutePath().toString(),
            "-af", af.toString(),
            out.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IOException("ffmpeg fade failed: " + log);
        return out;
    }

    /** Adjust audio volume by a multiplier (e.g. 2.0 = double, 0.5 = half). */
    public Path adjustVolume(Path src, float factor) throws Exception {
        if (factor <= 0) factor = 1.0f;
        String ext = src.getFileName().toString().replaceFirst(".*\\.", "");
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        List<String> cmd = List.of("ffmpeg", "-y", "-i", src.toAbsolutePath().toString(),
            "-af", "volume=" + factor,
            out.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IOException("ffmpeg volume failed: " + log);
        return out;
    }

    /** Concatenate multiple audio files into one MP3 using FFmpeg filter_complex. */
    public Path mergeAudio(List<Path> sources) throws Exception {
        if (sources.size() == 1) return convertAudio(sources.get(0), "mp3");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".mp3");
        // Build: ffmpeg -i f1 -i f2 ... -filter_complex "[0:a][1:a]concat=n=N:v=0:a=1[out]" -map "[out]" out.mp3
        List<String> cmd = new java.util.ArrayList<>(List.of("ffmpeg", "-y"));
        for (Path s : sources) { cmd.add("-i"); cmd.add(s.toAbsolutePath().toString()); }
        StringBuilder fc = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) fc.append("[").append(i).append(":a]");
        fc.append("concat=n=").append(sources.size()).append(":v=0:a=1[out]");
        cmd.addAll(List.of("-filter_complex", fc.toString(), "-map", "[out]", out.toAbsolutePath().toString()));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String log = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) throw new IOException("ffmpeg merge failed (code " + code + "): " + log);
        return out;
    }
}
