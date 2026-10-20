package io.github.recrivenvi.connectorx.protocol;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Container adapters for packetized scrcpy elementary streams; no transcoding or capture. */
public final class MediaFraming {
    private MediaFraming() {}
    public static byte[] flacHeader(byte[] info) {
        if(info.length!=34) throw new IllegalArgumentException("Invalid FLAC STREAMINFO");
        return ByteBuffer.allocate(42).put(new byte[]{102,76,97,67,(byte)128,0,0,34}).put(info).array();
    }
    public static byte[] adts(byte[] frame,byte[] config) {
        if(config.length<2) throw new IllegalArgumentException("Missing AAC AudioSpecificConfig");
        int object=(config[0]&255)>>3, frequency=((config[0]&7)<<1)|((config[1]&255)>>7), channels=(config[1]>>3)&15;
        int size=frame.length+7;
        if(object<1||object>4||frequency>=13||channels<1||channels>7||size>8191) throw new IllegalArgumentException("AAC config cannot be represented by ADTS");
        byte[] b=new byte[size];b[0]=(byte)255;b[1]=(byte)241;b[2]=(byte)(((object-1)<<6)|(frequency<<2)|(channels>>2));
        b[3]=(byte)(((channels&3)<<6)|(size>>11));b[4]=(byte)(size>>3);b[5]=(byte)(((size&7)<<5)|31);b[6]=(byte)252;
        System.arraycopy(frame,0,b,7,frame.length);return b;
    }
    public static byte[] ivfHeader(int codec,int width,int height) {
        if(width<1||width>8192||height<1||height>8192) throw new IllegalArgumentException("Invalid IVF size");
        byte[] fourcc=(codec==0x00767038?"VP80":"VP90").getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{68,75,73,70}).putShort((short)0).putShort((short)32)
            .put(fourcc).putShort((short)width).putShort((short)height).putInt(1000000).putInt(1).putInt(0).putInt(0).array();
    }
    public static byte[] ivfPacket(byte[] bytes,long pts) {return ByteBuffer.allocate(bytes.length+12).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.length).putLong(pts).put(bytes).array();}
    public static final class OggOpus {
        private int sequence;private long granule;
        public byte[] header(byte[] config) {
            if(config.length<19||!Arrays.equals(Arrays.copyOf(config,8),"OpusHead".getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException("Invalid OpusHead");
            byte[] first=page(config,2,0);byte[] tags=page(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .put("OpusTags".getBytes(StandardCharsets.US_ASCII)).putInt(0).putInt(0).array(),0,0);
            return ByteBuffer.allocate(first.length+tags.length).put(first).put(tags).array();
        }
        public byte[] packet(byte[] bytes) {
            if(bytes.length<1) throw new IllegalArgumentException("Empty Opus packet");
            int toc=bytes[0]&255,config=toc>>3;
            int samples=config>=16?120<<(config&3):config>=12?480<<(config&1):(config&3)==3?2880:480<<(config&3);
            int frames=switch(toc&3) {case 0->1;case 1,2->2;default->{if(bytes.length<2) throw new IllegalArgumentException("Invalid Opus packet");yield bytes[1]&63;}};
            if(frames==0||samples*frames>5760) throw new IllegalArgumentException("Invalid Opus duration");
            granule+=samples*frames;return page(bytes,0,granule);
        }
        private byte[] page(byte[] packet,int flags,long position) {
            int segments=packet.length/255+1;if(segments>255) throw new IllegalArgumentException("Ogg packet too large");
            ByteBuffer b=ByteBuffer.allocate(27+segments+packet.length).order(ByteOrder.LITTLE_ENDIAN);
            b.put("OggS".getBytes(StandardCharsets.US_ASCII)).put((byte)0).put((byte)flags).putLong(position).putInt(0x43584f50).putInt(sequence++).putInt(0).put((byte)segments);
            for(int i=0;i<segments;i++) b.put((byte)Math.min(255,packet.length-i*255));b.put(packet);
            byte[] data=b.array();int crc=0;
            for(byte value:data) {crc^=(value&255)<<24;for(int bit=0;bit<8;bit++) crc=(crc<<1)^((crc&0x80000000)!=0?0x04c11db7:0);}
            b.putInt(22,crc);return data;
        }
    }
}
