package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;

import java.io.File;
import java.util.UUID;

@Service
public class VideoService {

    @Value("${filer.upload-dir:uploads}")
    private String uploadDir;

    @Value("${filer.output-dir:outputs}")
    private String outputDir;

    public String extractThumbnail(String fileId, int second) throws Exception {
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".jpg";
        File outFile = new File(outputDir, outId);

        VideoAttributes video = new VideoAttributes();
        video.setCodec("mjpeg");
        video.setFrameRate(1);
        video.setBitRate(800_000);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("image2");
        attrs.setOffset((float) second);
        attrs.setDuration(1.0f);
        attrs.setVideoAttributes(video);

        new Encoder().encode(new MultimediaObject(src), outFile, attrs);
        return outId;
    }

    public String videoToGif(String fileId, int startSecond, int duration, int fps) throws Exception {
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".gif";
        File outFile = new File(outputDir, outId);

        VideoAttributes video = new VideoAttributes();
        video.setFrameRate(Math.min(fps, 15));
        video.setSize(new ws.schild.jave.info.VideoSize(480, -1));

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("gif");
        attrs.setOffset((float) startSecond);
        attrs.setDuration((float) Math.min(duration, 10));
        attrs.setVideoAttributes(video);

        new Encoder().encode(new MultimediaObject(src), outFile, attrs);
        return outId;
    }

    public String extractAudio(String fileId) throws Exception {
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".mp3";
        File outFile = new File(outputDir, outId);

        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libmp3lame");
        audio.setBitRate(192_000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp3");
        attrs.setAudioAttributes(audio);

        new Encoder().encode(new MultimediaObject(src), outFile, attrs);
        return outId;
    }
}
