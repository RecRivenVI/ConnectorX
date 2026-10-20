package io.github.recrivenvi.connectorx.protocol;

import java.io.*;
import java.util.*;

/** Versioned, bounded screen-stream configuration; never accepts arbitrary server arguments. */
public final class MediaOptions {
    public static final int CURRENT_VERSION = 3;
    private static final Set<String> ORIENTATIONS = Set.of(
            "", "0", "90", "180", "270", "flip0", "flip90", "flip180", "flip270",
            "@", "@0", "@90", "@180", "@270", "@flip0", "@flip90", "@flip180", "@flip270");

    public final int videoMaxSize, minSizeAlignment, videoBitRate, videoBufferMs;
    public final float maxFps, angle;
    public final String videoCodec, videoEncoder, videoCodecOptions, crop, captureOrientation;
    public final boolean downsizeOnError, ignoreVideoEncoderConstraints;
    public final boolean audio, audioDup;
    public final int audioBitRate, audioBufferMs, audioOutputBufferMs;
    public final String audioCodec, audioSource, audioEncoder, audioCodecOptions;

    public MediaOptions(int videoMaxSize, int minSizeAlignment, float maxFps, int videoBitRate,
            String videoCodec, String videoEncoder, String videoCodecOptions, String crop,
            String captureOrientation, float angle, boolean downsizeOnError,
            boolean ignoreVideoEncoderConstraints, int videoBufferMs,
            boolean audio, int audioBitRate, String audioCodec, String audioSource, boolean audioDup,
            String audioEncoder, String audioCodecOptions, int audioBufferMs, int audioOutputBufferMs) {
        if ((videoMaxSize != -1 && videoMaxSize != 0 && (videoMaxSize < 128 || videoMaxSize > 8192))
                || !List.of(1, 2, 4, 8, 16).contains(minSizeAlignment)
                || !Float.isFinite(maxFps) || maxFps < 0 || maxFps > 240
                || videoBitRate < 100_000 || videoBitRate > 100_000_000
                || !Float.isFinite(angle) || angle < -360 || angle > 360
                || videoBufferMs < 0 || videoBufferMs > 2_000
                || audioBitRate < 16_000 || audioBitRate > 1_024_000
                || audioBufferMs < 0 || audioBufferMs > 2_000
                || audioOutputBufferMs < 1 || audioOutputBufferMs > 1_000) {
            throw new IllegalArgumentException("媒体参数超出范围");
        }
        if (!List.of("h264", "h265", "av1", "vp8", "vp9").contains(videoCodec)
                || !List.of("opus", "aac", "flac", "raw").contains(audioCodec)) {
            throw new IllegalArgumentException("不支持的编码格式");
        }
        if (!List.of("output", "playback", "mic", "mic-unprocessed", "mic-camcorder",
                "mic-voice-recognition", "mic-voice-communication", "voice-call",
                "voice-call-uplink", "voice-call-downlink", "voice-performance").contains(audioSource)) {
            throw new IllegalArgumentException("不支持的音频来源");
        }
        if (audioDup && !audioSource.equals("playback")) {
            throw new IllegalArgumentException("同时在被控端播放需要 playback 音源");
        }
        if (!ORIENTATIONS.contains(captureOrientation)) {
            throw new IllegalArgumentException("捕获方向无效");
        }
        validateCrop(crop);
        for (String encoder : List.of(videoEncoder, audioEncoder)) {
            if (encoder.length() > 200 || !encoder.matches("[A-Za-z0-9_.:-]*")) {
                throw new IllegalArgumentException("编码器名称无效");
            }
        }
        for (String options : List.of(videoCodecOptions, audioCodecOptions)) {
            if (options.length() > 1024 || !options.matches("[A-Za-z0-9_.,:=+ /-]*")) {
                throw new IllegalArgumentException("Codec 参数格式无效");
            }
        }
        this.videoMaxSize = videoMaxSize;
        this.minSizeAlignment = minSizeAlignment;
        this.maxFps = maxFps;
        this.videoBitRate = videoBitRate;
        this.videoCodec = videoCodec;
        this.videoEncoder = videoEncoder;
        this.videoCodecOptions = videoCodecOptions;
        this.crop = crop;
        this.captureOrientation = captureOrientation;
        this.angle = angle;
        this.downsizeOnError = downsizeOnError;
        this.ignoreVideoEncoderConstraints = ignoreVideoEncoderConstraints;
        this.videoBufferMs = videoBufferMs;
        this.audio = audio;
        this.audioBitRate = audioBitRate;
        this.audioCodec = audioCodec;
        this.audioSource = audioSource;
        this.audioDup = audioDup;
        this.audioEncoder = audioEncoder;
        this.audioCodecOptions = audioCodecOptions;
        this.audioBufferMs = audioBufferMs;
        this.audioOutputBufferMs = audioOutputBufferMs;
    }

    private static void validateCrop(String crop) {
        if (crop.isEmpty()) return;
        String[] parts = crop.split(":", -1);
        if (parts.length != 4) throw new IllegalArgumentException("裁切格式应为 width:height:x:y");
        try {
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            if (w < 1 || w > 8192 || h < 1 || h > 8192 || x < 0 || x > 8191 || y < 0 || y > 8191) {
                throw new IllegalArgumentException("裁切参数超出范围");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("裁切格式应为 width:height:x:y", e);
        }
    }

    public static MediaOptions defaults() {
        return new MediaOptions(-1, 1, 60, 8_000_000, "h264", "", "", "", "", 0,
                true, false, 0, true, 128_000, "opus", "output", false, "", "", 50, 10);
    }

    public static MediaOptions legacy(boolean app) {
        return new MediaOptions(-1, 1, 60, app ? 12_000_000 : 6_000_000, "h264", "", "", "", "", 0,
                true, false, 0, false, 128_000, "opus", "output", false, "", "", 50, 10);
    }

    /** -1 preserves ConnectorX defaults: device mirror 1600, virtual app at native size. */
    public int maxSizeFor(boolean app) {
        return videoMaxSize == -1 ? (app ? 0 : 1600) : videoMaxSize;
    }

    public void write(DataOutputStream out) throws IOException {
        write(out, CURRENT_VERSION);
    }

    public void write(DataOutputStream out, int version) throws IOException {
        if (version < 1 || version > CURRENT_VERSION) throw new IOException("不支持的媒体参数版本");
        if (version == 1 && videoMaxSize != -1) throw new IOException("被控端不支持视频分辨率参数");
        if (version < 3 && requiresVersion3()) throw new IOException("被控端不支持高级传输参数");
        out.writeInt(version);
        if (version >= 2) out.writeInt(videoMaxSize);
        out.writeInt(Math.round(maxFps));
        out.writeInt(videoBitRate);
        out.writeUTF(videoCodec);
        out.writeUTF(videoEncoder);
        out.writeUTF(videoCodecOptions);
        out.writeBoolean(audio);
        out.writeInt(audioBitRate);
        out.writeUTF(audioCodec);
        out.writeUTF(audioSource);
        out.writeBoolean(audioDup);
        out.writeUTF(audioEncoder);
        out.writeUTF(audioCodecOptions);
        out.writeInt(audioBufferMs);
        if (version >= 3) {
            out.writeInt(minSizeAlignment);
            out.writeFloat(maxFps);
            out.writeUTF(crop);
            out.writeUTF(captureOrientation);
            out.writeFloat(angle);
            out.writeBoolean(downsizeOnError);
            out.writeBoolean(ignoreVideoEncoderConstraints);
            out.writeInt(videoBufferMs);
            out.writeInt(audioOutputBufferMs);
        }
    }

    private boolean requiresVersion3() {
        MediaOptions d = defaults();
        return minSizeAlignment != d.minSizeAlignment || maxFps != Math.round(maxFps)
                || !crop.isEmpty() || !captureOrientation.isEmpty() || angle != 0
                || downsizeOnError != d.downsizeOnError
                || ignoreVideoEncoderConstraints != d.ignoreVideoEncoderConstraints
                || videoBufferMs != d.videoBufferMs || audioOutputBufferMs != d.audioOutputBufferMs;
    }

    public static MediaOptions read(DataInputStream in) throws IOException {
        int version = in.readInt();
        if (version < 1 || version > CURRENT_VERSION) throw new IOException("不支持的媒体参数版本");
        try {
            int maxSize = version == 1 ? -1 : in.readInt();
            int legacyFps = in.readInt();
            int videoBitRate = in.readInt();
            String videoCodec = in.readUTF();
            String videoEncoder = in.readUTF();
            String videoCodecOptions = in.readUTF();
            boolean audio = in.readBoolean();
            int audioBitRate = in.readInt();
            String audioCodec = in.readUTF();
            String audioSource = in.readUTF();
            boolean audioDup = in.readBoolean();
            String audioEncoder = in.readUTF();
            String audioCodecOptions = in.readUTF();
            int audioBufferMs = in.readInt();
            int alignment = version >= 3 ? in.readInt() : 1;
            float maxFps = version >= 3 ? in.readFloat() : legacyFps;
            String crop = version >= 3 ? in.readUTF() : "";
            String orientation = version >= 3 ? in.readUTF() : "";
            float angle = version >= 3 ? in.readFloat() : 0;
            boolean downsize = version < 3 || in.readBoolean();
            boolean ignoreConstraints = version >= 3 && in.readBoolean();
            int videoBufferMs = version >= 3 ? in.readInt() : 0;
            int audioOutputBufferMs = version >= 3 ? in.readInt() : 10;
            return new MediaOptions(maxSize, alignment, maxFps, videoBitRate, videoCodec, videoEncoder,
                    videoCodecOptions, crop, orientation, angle, downsize, ignoreConstraints,
                    videoBufferMs, audio, audioBitRate, audioCodec, audioSource, audioDup,
                    audioEncoder, audioCodecOptions, audioBufferMs, audioOutputBufferMs);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public String encode() {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            write(new DataOutputStream(b));
            return Base64.getEncoder().encodeToString(b.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static MediaOptions decode(String text) {
        if (text.length() > 8192) throw new IllegalArgumentException("媒体参数过长");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text)));
            MediaOptions result = read(in);
            if (in.available() != 0) throw new IOException("Trailing data");
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public List<String> serverArguments() {
        List<String> args = new ArrayList<>(List.of(
                "min_size_alignment=" + minSizeAlignment,
                "max_fps=" + maxFps,
                "video_bit_rate=" + videoBitRate,
                "video_codec=" + videoCodec,
                "downsize_on_error=" + downsizeOnError,
                "ignore_video_encoder_constraints=" + ignoreVideoEncoderConstraints,
                "audio=" + audio));
        if (!crop.isEmpty()) args.add("crop=" + crop);
        if (!captureOrientation.isEmpty()) args.add("capture_orientation=" + captureOrientation);
        if (angle != 0) args.add("angle=" + angle);
        if (!videoEncoder.isEmpty()) args.add("video_encoder=" + videoEncoder);
        if (!videoCodecOptions.isEmpty()) args.add("video_codec_options=" + videoCodecOptions);
        if (audio) {
            args.addAll(List.of("audio_bit_rate=" + audioBitRate, "audio_codec=" + audioCodec,
                    "audio_source=" + audioSource, "audio_dup=" + audioDup));
            if (!audioEncoder.isEmpty()) args.add("audio_encoder=" + audioEncoder);
            if (!audioCodecOptions.isEmpty()) args.add("audio_codec_options=" + audioCodecOptions);
        }
        return args;
    }

    public Map<String, String> values() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("videoMaxSize", "" + videoMaxSize);
        m.put("minSizeAlignment", "" + minSizeAlignment);
        m.put("maxFps", "" + maxFps);
        m.put("videoBitRate", "" + videoBitRate);
        m.put("videoCodec", videoCodec);
        m.put("videoEncoder", videoEncoder);
        m.put("videoCodecOptions", videoCodecOptions);
        m.put("crop", crop);
        m.put("captureOrientation", captureOrientation);
        m.put("angle", "" + angle);
        m.put("downsizeOnError", "" + downsizeOnError);
        m.put("ignoreVideoEncoderConstraints", "" + ignoreVideoEncoderConstraints);
        m.put("videoBufferMs", "" + videoBufferMs);
        m.put("audio", "" + audio);
        m.put("audioBitRate", "" + audioBitRate);
        m.put("audioCodec", audioCodec);
        m.put("audioSource", audioSource);
        m.put("audioDup", "" + audioDup);
        m.put("audioEncoder", audioEncoder);
        m.put("audioCodecOptions", audioCodecOptions);
        m.put("audioBufferMs", "" + audioBufferMs);
        m.put("audioOutputBufferMs", "" + audioOutputBufferMs);
        return m;
    }

    public static MediaOptions fromValues(Map<String, String> m) {
        MediaOptions d = defaults();
        return new MediaOptions(
                Integer.parseInt(m.getOrDefault("videoMaxSize", "" + d.videoMaxSize)),
                Integer.parseInt(m.getOrDefault("minSizeAlignment", "" + d.minSizeAlignment)),
                Float.parseFloat(m.getOrDefault("maxFps", "" + d.maxFps)),
                Integer.parseInt(m.getOrDefault("videoBitRate", "" + d.videoBitRate)),
                m.getOrDefault("videoCodec", d.videoCodec),
                m.getOrDefault("videoEncoder", d.videoEncoder),
                m.getOrDefault("videoCodecOptions", d.videoCodecOptions),
                m.getOrDefault("crop", d.crop),
                m.getOrDefault("captureOrientation", d.captureOrientation),
                Float.parseFloat(m.getOrDefault("angle", "" + d.angle)),
                Boolean.parseBoolean(m.getOrDefault("downsizeOnError", "" + d.downsizeOnError)),
                Boolean.parseBoolean(m.getOrDefault("ignoreVideoEncoderConstraints", "" + d.ignoreVideoEncoderConstraints)),
                Integer.parseInt(m.getOrDefault("videoBufferMs", "" + d.videoBufferMs)),
                Boolean.parseBoolean(m.getOrDefault("audio", "" + d.audio)),
                Integer.parseInt(m.getOrDefault("audioBitRate", "" + d.audioBitRate)),
                m.getOrDefault("audioCodec", d.audioCodec),
                m.getOrDefault("audioSource", d.audioSource),
                Boolean.parseBoolean(m.getOrDefault("audioDup", "" + d.audioDup)),
                m.getOrDefault("audioEncoder", d.audioEncoder),
                m.getOrDefault("audioCodecOptions", d.audioCodecOptions),
                Integer.parseInt(m.getOrDefault("audioBufferMs", "" + d.audioBufferMs)),
                Integer.parseInt(m.getOrDefault("audioOutputBufferMs", "" + d.audioOutputBufferMs)));
    }

    public static String videoMime(int codec) {
        return switch (codec) {
            case 0x68323634 -> "video/avc";
            case 0x68323635 -> "video/hevc";
            case 0x00617631 -> "video/av01";
            case 0x00767038 -> "video/x-vnd.on2.vp8";
            case 0x00767039 -> "video/x-vnd.on2.vp9";
            default -> throw new IllegalArgumentException("未知视频编码");
        };
    }
}
