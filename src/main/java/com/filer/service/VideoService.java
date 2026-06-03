package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;

import java.nio.file.Path;
import java.nio.file.Paths;
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
}
