package io.github.recrivenvi.connectorx.protocol;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.nio.*;
public class MediaOptionsTest {
    @Test public void defaultsRoundTripAndReachServer() {
        MediaOptions m=MediaOptions.decode(MediaOptions.defaults().encode());assertEquals(60,m.maxFps);assertEquals(8000000,m.videoBitRate);
        assertTrue(m.audio);assertEquals("opus",m.audioCodec);assertEquals(50,m.audioBufferMs);
        assertEquals(m.values(),MediaOptions.fromValues(m.values()).values());
        assertTrue(m.serverArguments().contains("audio=true"));assertTrue(m.serverArguments().contains("audio_bit_rate=128000"));
    }
    @Test public void selectedCodecsAreNotSilentlyReplaced() {
        for(String v:List.of("h264","h265","av1","vp8","vp9")) for(String a:List.of("opus","aac","flac","raw")) {
            Map<String,String> values=MediaOptions.defaults().values();values.put("videoCodec",v);values.put("audioCodec",a);values.put("maxFps","0");
            MediaOptions m=MediaOptions.fromValues(values);assertTrue(m.serverArguments().contains("video_codec="+v));assertTrue(m.serverArguments().contains("audio_codec="+a));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectUnboundedBitrate() {Map<String,String> m=MediaOptions.defaults().values();m.put("videoBitRate","2147483647");MediaOptions.fromValues(m);}
    @Test(expected=IllegalArgumentException.class) public void rejectCommandCharacters() {Map<String,String> m=MediaOptions.defaults().values();m.put("videoEncoder","x; reboot");MediaOptions.fromValues(m);}
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
