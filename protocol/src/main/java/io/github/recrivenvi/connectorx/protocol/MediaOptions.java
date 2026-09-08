package io.github.recrivenvi.connectorx.protocol;

import java.io.*;
import java.util.*;

/** Versioned, bounded media configuration; never accepts arbitrary scrcpy/server arguments. */
public final class MediaOptions {
    public final int maxFps, videoBitRate, audioBitRate, audioBufferMs;
    public final String videoCodec, videoEncoder, videoCodecOptions, audioCodec, audioSource, audioEncoder, audioCodecOptions;
    public final boolean audio, audioDup;
    public MediaOptions(int maxFps,int videoBitRate,String videoCodec,String videoEncoder,String videoCodecOptions,
            boolean audio,int audioBitRate,String audioCodec,String audioSource,boolean audioDup,String audioEncoder,String audioCodecOptions,int audioBufferMs) {
        if(maxFps<0||maxFps>240||videoBitRate<100000||videoBitRate>100000000||audioBitRate<16000||audioBitRate>1024000||audioBufferMs<20||audioBufferMs>1000)
            throw new IllegalArgumentException("媒体参数超出范围");
        if(!List.of("h264","h265","av1","vp8","vp9").contains(videoCodec)||!List.of("opus","aac","flac","raw").contains(audioCodec))
            throw new IllegalArgumentException("不支持的编码格式");
        if(!List.of("output","playback","mic","mic-unprocessed","mic-camcorder","mic-voice-recognition","mic-voice-communication",
            "voice-call","voice-call-uplink","voice-call-downlink","voice-performance").contains(audioSource)) throw new IllegalArgumentException("不支持的音频来源");
        if(audioDup&&!audioSource.equals("playback")) throw new IllegalArgumentException("同时在被控端播放需要 playback 音源");
        for(String encoder:List.of(videoEncoder,audioEncoder)) if(encoder.length()>200||!encoder.matches("[A-Za-z0-9_.:-]*")) throw new IllegalArgumentException("编码器名称无效");
        for(String options:List.of(videoCodecOptions,audioCodecOptions)) {
            if(options.length()>1024||!options.matches("[A-Za-z0-9_.,:=+ /-]*")) throw new IllegalArgumentException("Codec 参数格式无效");
        }
        this.maxFps=maxFps;this.videoBitRate=videoBitRate;this.videoCodec=videoCodec;this.videoEncoder=videoEncoder;this.videoCodecOptions=videoCodecOptions;
        this.audio=audio;this.audioBitRate=audioBitRate;this.audioCodec=audioCodec;this.audioSource=audioSource;this.audioDup=audioDup;
        this.audioEncoder=audioEncoder;this.audioCodecOptions=audioCodecOptions;this.audioBufferMs=audioBufferMs;
    }
    public static MediaOptions defaults() {return new MediaOptions(60,8000000,"h264","","",true,128000,"opus","output",false,"","",50);}
    public static MediaOptions legacy(boolean app) {return new MediaOptions(60,app?12000000:6000000,"h264","","",false,128000,"opus","output",false,"","",50);}
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(1);out.writeInt(maxFps);out.writeInt(videoBitRate);out.writeUTF(videoCodec);out.writeUTF(videoEncoder);out.writeUTF(videoCodecOptions);
        out.writeBoolean(audio);out.writeInt(audioBitRate);out.writeUTF(audioCodec);out.writeUTF(audioSource);out.writeBoolean(audioDup);
        out.writeUTF(audioEncoder);out.writeUTF(audioCodecOptions);out.writeInt(audioBufferMs);
    }
    public static MediaOptions read(DataInputStream in) throws IOException {
        if(in.readInt()!=1) throw new IOException("不支持的媒体参数版本");
        try {return new MediaOptions(in.readInt(),in.readInt(),in.readUTF(),in.readUTF(),in.readUTF(),in.readBoolean(),in.readInt(),in.readUTF(),in.readUTF(),in.readBoolean(),in.readUTF(),in.readUTF(),in.readInt());}
        catch(IllegalArgumentException e) {throw new IOException(e.getMessage(),e);}
    }
    public String encode() {
        try {ByteArrayOutputStream b=new ByteArrayOutputStream();write(new DataOutputStream(b));return Base64.getEncoder().encodeToString(b.toByteArray());}
        catch(IOException e) {throw new IllegalStateException(e);}
    }
    public static MediaOptions decode(String text) {
        if(text.length()>8192) throw new IllegalArgumentException("媒体参数过长");
        try {DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text)));MediaOptions result=read(in);if(in.available()!=0) throw new IOException("Trailing data");return result;}
        catch(IOException e) {throw new IllegalArgumentException(e.getMessage(),e);}
    }
    public List<String> serverArguments() {
        List<String> args=new ArrayList<>(List.of("max_fps="+maxFps,"video_bit_rate="+videoBitRate,"video_codec="+videoCodec,"audio="+audio));
        if(!videoEncoder.isEmpty()) args.add("video_encoder="+videoEncoder);
        if(!videoCodecOptions.isEmpty()) args.add("video_codec_options="+videoCodecOptions);
        if(audio) {
            args.addAll(List.of("audio_bit_rate="+audioBitRate,"audio_codec="+audioCodec,"audio_source="+audioSource,"audio_dup="+audioDup));
            if(!audioEncoder.isEmpty()) args.add("audio_encoder="+audioEncoder);
            if(!audioCodecOptions.isEmpty()) args.add("audio_codec_options="+audioCodecOptions);
        }
        return args;
    }
    public Map<String,String> values() {
        Map<String,String> m=new LinkedHashMap<>();
        m.put("maxFps",""+maxFps);m.put("videoBitRate",""+videoBitRate);m.put("videoCodec",videoCodec);m.put("videoEncoder",videoEncoder);m.put("videoCodecOptions",videoCodecOptions);
        m.put("audio",""+audio);m.put("audioBitRate",""+audioBitRate);m.put("audioCodec",audioCodec);m.put("audioSource",audioSource);m.put("audioDup",""+audioDup);
        m.put("audioEncoder",audioEncoder);m.put("audioCodecOptions",audioCodecOptions);m.put("audioBufferMs",""+audioBufferMs);return m;
    }
    public static MediaOptions fromValues(Map<String,String> m) {
        return new MediaOptions(Integer.parseInt(m.get("maxFps")),Integer.parseInt(m.get("videoBitRate")),m.get("videoCodec"),m.get("videoEncoder"),m.get("videoCodecOptions"),
            Boolean.parseBoolean(m.get("audio")),Integer.parseInt(m.get("audioBitRate")),m.get("audioCodec"),m.get("audioSource"),Boolean.parseBoolean(m.get("audioDup")),m.get("audioEncoder"),m.get("audioCodecOptions"),Integer.parseInt(m.get("audioBufferMs")));
    }
    public static String videoMime(int codec) {return switch(codec) {case 0x68323634->"video/avc";case 0x68323635->"video/hevc";case 0x00617631->"video/av01";case 0x00767038->"video/x-vnd.on2.vp8";case 0x00767039->"video/x-vnd.on2.vp9";default->throw new IllegalArgumentException("未知视频编码");};}
}
