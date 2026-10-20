package io.github.recrivenvi.connectorx.protocol;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.nio.*;
public class MediaOptionsTest {
    @Test public void defaultsRoundTripAndReachServer() {
        MediaOptions m=MediaOptions.decode(MediaOptions.defaults().encode());assertEquals(-1,m.videoMaxSize);assertEquals(60f,m.maxFps,0f);assertEquals(8000000,m.videoBitRate);
        assertEquals(1600,m.maxSizeFor(false));assertEquals(0,m.maxSizeFor(true));
        assertTrue(m.audio);assertEquals("opus",m.audioCodec);assertEquals(50,m.audioBufferMs);assertEquals(10,m.audioOutputBufferMs);
        assertEquals(m.values(),MediaOptions.fromValues(m.values()).values());
        assertTrue(m.serverArguments().contains("audio=true"));assertTrue(m.serverArguments().contains("audio_bit_rate=128000"));
    }
    @Test public void readsAndWritesVersionOneDefaults() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();MediaOptions.defaults().write(new DataOutputStream(bytes),1);
        MediaOptions result=MediaOptions.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(-1,result.videoMaxSize);assertEquals(60f,result.maxFps,0f);
    }
    @Test public void selectedCodecsAreNotSilentlyReplaced() {
        for(String v:List.of("h264","h265","av1","vp8","vp9")) for(String a:List.of("opus","aac","flac","raw")) {
            Map<String,String> values=MediaOptions.defaults().values();values.put("videoCodec",v);values.put("audioCodec",a);values.put("maxFps","0");
            MediaOptions m=MediaOptions.fromValues(values);assertTrue(m.serverArguments().contains("video_codec="+v));assertTrue(m.serverArguments().contains("audio_codec="+a));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectUnboundedBitrate() {Map<String,String> m=MediaOptions.defaults().values();m.put("videoBitRate","2147483647");MediaOptions.fromValues(m);}
    @Test(expected=IllegalArgumentException.class) public void rejectInvalidMaxSize() {Map<String,String> m=MediaOptions.defaults().values();m.put("videoMaxSize","64");MediaOptions.fromValues(m);}
    @Test(expected=java.io.IOException.class) public void oldPeerRejectsResolutionOverride() throws Exception {Map<String,String> m=MediaOptions.defaults().values();m.put("videoMaxSize","1920");MediaOptions.fromValues(m).write(new DataOutputStream(new ByteArrayOutputStream()),1);}
    @Test(expected=IllegalArgumentException.class) public void rejectCommandCharacters() {Map<String,String> m=MediaOptions.defaults().values();m.put("videoEncoder","x; reboot");MediaOptions.fromValues(m);}
    @Test(expected=IllegalArgumentException.class) public void rejectMalformedCrop() {Map<String,String> m=MediaOptions.defaults().values();m.put("crop","1080x1920");MediaOptions.fromValues(m);}
    @Test public void advancedTransportOptionsReachServerAndRoundTrip() {
        Map<String,String> values=MediaOptions.defaults().values();
        values.put("minSizeAlignment","8");values.put("maxFps","59.94");values.put("crop","1080:1920:0:0");
        values.put("captureOrientation","@90");values.put("angle","12.5");values.put("downsizeOnError","false");
        values.put("ignoreVideoEncoderConstraints","true");values.put("videoBufferMs","25");values.put("audioOutputBufferMs","20");
        MediaOptions m=MediaOptions.decode(MediaOptions.fromValues(values).encode());
        assertEquals(values,m.values());
        assertTrue(m.serverArguments().contains("capture_orientation=@90"));
        assertTrue(m.serverArguments().contains("ignore_video_encoder_constraints=true"));
    }
    @Test(expected=java.io.IOException.class) public void oldPeerRejectsAdvancedOptions() throws Exception {
        Map<String,String> m=MediaOptions.defaults().values();m.put("crop","100:100:0:0");
        MediaOptions.fromValues(m).write(new DataOutputStream(new ByteArrayOutputStream()),2);
    }
    @Test(expected=IllegalArgumentException.class) public void duplicationRequiresPlayback() {Map<String,String> m=MediaOptions.defaults().values();m.put("audioDup","true");MediaOptions.fromValues(m);}
    @Test public void disabledAudioDoesNotCreateAudioArguments() {Map<String,String> m=MediaOptions.defaults().values();m.put("audio","false");assertFalse(MediaOptions.fromValues(m).serverArguments().stream().anyMatch(x->x.startsWith("audio_codec=")));}
    @Test public void ivfPreservesPacketTimeAndDimensions() {
        ByteBuffer h=ByteBuffer.wrap(MediaFraming.ivfHeader(0x00767039,1080,1920)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1080,h.getShort(12));assertEquals(1920,h.getShort(14));
        ByteBuffer p=ByteBuffer.wrap(MediaFraming.ivfPacket(new byte[]{1,2},12345)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(2,p.getInt());assertEquals(12345,p.getLong());
    }
    @Test(expected=IllegalArgumentException.class) public void malformedFlacRejected() {MediaFraming.flacHeader(new byte[33]);}
}
