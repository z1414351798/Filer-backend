package com.filer.service;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Service
public class VideoService {

    @Value("${filer.upload-dir:uploads}")
    private String uploadDir;

    @Value("${filer.output-dir:outputs}")
    private String outputDir;

    @Value("${filer.ffmpeg-path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    @Value("${filer.ffprobe-path:/usr/bin/ffprobe}")
    private String ffprobePath;

    public String extractThumbnail(String fileId, int second) throws IOException {
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".jpg";
        File outFile = new File(outputDir, outId);

        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFprobe ffprobe = new FFprobe(ffprobePath);
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(src.getAbsolutePath())
                .addExtraArgs("-ss", String.valueOf(second))
                .addOutput(outFile.getAbsolutePath())
                .setFrames(1)
                .setVideoFilter("scale=640:-1")
                .done();
        new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();
        return outId;
    }

    public String videoToGif(String fileId, int startSecond, int duration, int fps) throws IOException {
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".gif";
        File outFile = new File(outputDir, outId);

        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFprobe ffprobe = new FFprobe(ffprobePath);
        String vf = String.format("fps=%d,scale=480:-1:flags=lanczos", Math.min(fps, 15));
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(src.getAbsolutePath())
                .addExtraArgs("-ss", String.valueOf(startSecond))
                .addExtraArgs("-t", String.valueOf(Math.min(duration, 10)))
                .addOutput(outFile.getAbsolutePath())
                .setVideoFilter(vf)
                .done();
        new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();
        return outId;
    }

    public String extractAudio(String fileId) throws IOException {
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".mp3";
        File outFile = new File(outputDir, outId);

        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFprobe ffprobe = new FFprobe(ffprobePath);
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(src.getAbsolutePath())
                .addOutput(outFile.getAbsolutePath())
                .setAudioCodec("libmp3lame")
                .setAudioBitRate(192_000)
                .disableVideo()
                .done();
        new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();
        return outId;
    }
}
