package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.BaseProtocolDecoder;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Checksum;
import org.traccar.helper.DataConverter;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.CellTower;
import org.traccar.model.Network;
import org.traccar.model.Position;
import org.traccar.model.WifiAccessPoint;
import org.traccar.session.DeviceSession;

import java.net.SocketAddress;
import java.util.Date;
import java.util.List;

public class Xexun2ProtocolDecoder extends BaseProtocolDecoder {
    public static final int FLAG = 0xfaaf;
    public static final int MSG_COMMAND = 0x07;
    public static final int MSG_LOGIN = 0x14;

    private final Logger LOGGER = LoggerFactory.getLogger(Xexun2ProtocolDecoder.class);

    public Xexun2ProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    private void sendResponse(Channel channel, int type, int index, ByteBuf imei, int responseByte, String checksumHex) {
        if (channel != null) {
            ByteBuf response = Unpooled.buffer();
            response.writeShort(FLAG);
            response.writeShort(type);
            response.writeShort(index);
            response.writeBytes(imei);
            response.writeShort(0x01); // length
            response.writeShort(Checksum.ip(Unpooled.wrappedBuffer(DataConverter.parseHex(checksumHex)).nioBuffer()));
            response.writeByte(responseByte);
            response.writeShort(FLAG);
            channel.writeAndFlush(new NetworkMessage(response, channel.remoteAddress()));
        }
    }

    private String decodeAlarm(long value) {
        if (BitUtil.check(value, 0)) {
            return Position.ALARM_SOS;
        }
        if (BitUtil.check(value, 1)) {
            return Position.ALARM_REMOVING;
        }
        if (BitUtil.check(value, 15)) {
            return Position.ALARM_FALL_DOWN;
        }
        return null;
    }

    private void decodeGps(Position position, ByteBuf buf, ByteBuf remaining) {
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        setCoordinates(position, buf);
        position.setAltitude(buf.readFloat());
        position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
        position.set("signalAvg", buf.readUnsignedByte());
        position.setSpeed(UnitsConverter.knotsFromKph((double) buf.readUnsignedShort() / 10.0));
        position.setCourse((double) buf.readUnsignedShort() / 10.0);
        position.set("ephemerisSync", buf.readUnsignedByte());
        position.set("trackingSeconds", buf.readUnsignedByte());
        position.setAccuracy((double) buf.readUnsignedShort() / 10.0);
        byte[] satelliteSignals = new byte[4];
        buf.readBytes(satelliteSignals);
        position.set("satelliteSignals", bytesToHex(satelliteSignals));

        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeWifi(Position position, ByteBuf buf, ByteBuf remaining) {
        position.setTime(new Date(buf.readUnsignedInt() * 1000));

        Network network = new Network();
        int count = buf.readUnsignedByte();
        for (int i = 0; i < count; i++) {
            String mac = ByteBufUtil.hexDump(buf.readSlice(6)).replaceAll("(..)", "$1:");
            int signal = buf.readUnsignedByte();
            network.addWifiAccessPoint(WifiAccessPoint.from(mac, signal));
        }
        position.setNetwork(network);

        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeLbs(Position position, ByteBuf buf, ByteBuf remaining) {
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        int mcc = buf.readUnsignedShort();
        int mnc = buf.readUnsignedShort();
        int lac = buf.readInt();
        long cid = buf.readUnsignedInt();
        int rssi = buf.readUnsignedByte();
        CellTower cellTower = CellTower.from(mcc, mnc, lac, cid, rssi);
        if (position.getNetwork() == null) {
            position.setNetwork(new Network(CellTower.from(mcc, mnc, lac, cid, rssi)));
        } else {
            position.getNetwork().setCellTowers(List.of(cellTower));
        }

        if (buf.readableBytes() >= 8) {
            setCoordinates(position, buf);
        }

        if (position.getLatitude() != 0 || position.getLongitude() != 0) {
            position.setOutdated(false);
        }

        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void setCoordinates(Position position, ByteBuf buf) {
        if (buf.readableBytes() >= 8) {
            // Read coordinates as floats (default format)
            double latitude = buf.readFloat();
            double longitude = buf.readFloat();
            
            LOGGER.debug("Float coordinates: lat={}, lon={}", latitude, longitude);
            
            // Validate coordinate ranges before setting
            if (latitude >= -90.0 && latitude <= 90.0 && longitude >= -180.0 && longitude <= 180.0) {
                if (latitude != 0 || longitude != 0) {
                    position.setLatitude(latitude);
                    position.setLongitude(longitude);
                    position.setValid(true);
                } else {
                    position.setValid(false);
                }
            } else {
                LOGGER.warn("Invalid coordinates: lat={}, lon={}", latitude, longitude);
                position.setValid(false);
            }
        }
    }

    private void decodeHeartRate(Position position, ByteBuf buf, ByteBuf remaining) {
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        int heartRate = buf.readUnsignedByte();
        int systolicBp = buf.readUnsignedByte();
        int diastolicBp = buf.readUnsignedByte();
        int bloodOxygen = buf.readUnsignedByte();

        position.set(Position.KEY_HEART_RATE, heartRate);
        position.set("systolicBp", systolicBp);
        position.set("diastolicBp", diastolicBp);
        position.set("bloodOxygen", bloodOxygen);

        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeDeviceStatus(Position position, ByteBuf buf, ByteBuf remaining) {
        position.set(Position.KEY_RSSI, buf.readUnsignedByte());
        
        int batteryLevel = buf.readUnsignedByte();
        position.set(Position.KEY_BATTERY_LEVEL, batteryLevel);
        
        if (batteryLevel < 10 && batteryLevel > 0) {
            position.set(Position.KEY_ALARM, Position.ALARM_LOW_BATTERY);
        }
        
        int status = buf.readUnsignedByte();
        position.set(Position.KEY_STATUS, status);
        
        position.set(Position.KEY_MOTION, BitUtil.check(status, 1));
        position.set("deviceRemoved", BitUtil.check(status, 0));
        position.set("oldData", BitUtil.check(status, 2));
        position.set("vsimUsed", BitUtil.check(status, 3));
        position.set("fiberARemoved", BitUtil.check(status, 5));
        position.set("fiberBRemoved", BitUtil.check(status, 6));
        position.set(Position.KEY_CHARGE, BitUtil.check(status, 7));
        
        position.set("trackingSequence", buf.readUnsignedByte());
        position.set(Position.KEY_FUEL_LEVEL, buf.readUnsignedByte());
    
        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeMotion(Position position, ByteBuf buf, ByteBuf remaining) {
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        position.set("steps", buf.readUnsignedShort());
        position.set("temperature", buf.readFloat());

        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeAlarm(Position position, ByteBuf buf, ByteBuf remaining) {
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        long alarmType = buf.readUnsignedInt();
        position.set(Position.KEY_ALARM, decodeAlarm(alarmType));

        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeMessage(Position position, ByteBuf buf, ByteBuf remaining) {
        // Handle text messages and commands (data type 0x21)
        if (buf.readableBytes() > 0) {
            byte[] messageBytes = new byte[buf.readableBytes()];
            buf.readBytes(messageBytes);
            
            // Try to decode as ASCII text first
            String message = new String(messageBytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            
            // If it's not printable ASCII, log as hex
            boolean isPrintable = message.chars().allMatch(c -> c >= 32 && c <= 126);
            if (isPrintable && !message.isEmpty()) {
                position.set("message", message);
                LOGGER.info("Received text message: {}", message);
            } else {
                String hexMessage = ByteBufUtil.hexDump(messageBytes);
                position.set("messageHex", hexMessage);
                LOGGER.info("Received binary message: {}", hexMessage);
            }
        }
        
        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeTextData(Position position, ByteBuf buf, ByteBuf remaining) {
        // Handle data type 0xFF - usually text messages or status info
        if (buf.readableBytes() > 0) {
            byte[] textBytes = new byte[buf.readableBytes()];
            buf.readBytes(textBytes);
            
            // Try to decode as ASCII text
            String text = new String(textBytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            
            // Filter out non-printable characters and keep readable text
            String cleanText = text.replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", "");
            
            if (!cleanText.isEmpty()) {
                position.set("textData", cleanText);
                LOGGER.info("Received text data: {}", cleanText);
            } else {
                String hexData = ByteBufUtil.hexDump(textBytes);
                position.set("textDataHex", hexData);
                LOGGER.debug("Received binary text data: {}", hexData);
            }
        }
        
        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeDeviceInfo(Position position, ByteBuf buf, ByteBuf remaining) {
        // Handle data type 0x20 - device configuration/info data
        if (buf.readableBytes() > 0) {
            byte[] infoBytes = new byte[buf.readableBytes()];
            buf.readBytes(infoBytes);
            
            // Try to decode as ASCII text (device info is usually text)
            String info = new String(infoBytes, java.nio.charset.StandardCharsets.US_ASCII);
            
            // Extract readable parts (device info often contains mixed binary/text)
            String[] parts = info.split("\\|");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.length() > 3 && part.matches(".*[A-Za-z0-9].*")) {
                    position.set("deviceInfo" + (i > 0 ? i : ""), part);
                }
            }
            
            LOGGER.debug("Received device info: {}", info.replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", ""));
        }
        
        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        buf.skipBytes(2); // flag

        int type = buf.readUnsignedShort();
        int index = buf.readUnsignedShort();

        ByteBuf imei = buf.readSlice(8);
        DeviceSession deviceSession = getDeviceSession(
                channel, remoteAddress, ByteBufUtil.hexDump(imei).substring(0, 15));
        if (deviceSession == null) {
            return null;
        }

        int length = buf.readUnsignedShort() & 0x03ff; // extract only the lower 10 bits
        int checksum = buf.readUnsignedShort();

        LOGGER.debug("Message parsing: type=0x{:02X}, index={}, length={}, checksum=0x{:04X}", 
                    type, index, length, checksum);

        if (checksum != Checksum.ip(buf.nioBuffer(buf.readerIndex(), length))) {
            LOGGER.warn("Checksum mismatch: expected=0x{:04X}, calculated=0x{:04X}", 
                       checksum, Checksum.ip(buf.nioBuffer(buf.readerIndex(), length)));
            return null;
        }

        if (type == MSG_LOGIN) {
            sendResponse(channel, type, index, imei, 0x02, "02");
            return null;
        } else {
            sendResponse(channel, type, index, imei, 0x01, "01");
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        decodeData(position, buf);

        if (position.getLatitude() == 0 && position.getLongitude() == 0) {
            getLastLocation(position, null);
        }

        return position;
    }

    private void decodeData(Position position, ByteBuf buf) {
        int readableByte = buf.readableBytes();

        if (readableByte < 2) {
            LOGGER.info("Unknown Data: {}", ByteBufUtil.hexDump(buf.readBytes(readableByte)));
            return;
        }

        int dataType = buf.readUnsignedByte();
        int dataLength = buf.readUnsignedByte();

        LOGGER.debug("Data parsing: readable={}, dataType=0x{:02X}, dataLength={}", 
                    readableByte, dataType, dataLength);

        if (readableByte < dataLength + 2) {
            if (dataLength > 50 || dataLength < 0) {
                // If data length seems unreasonable, might be corrupted data
                LOGGER.warn("Suspicious data length: {} bytes, skipping. DataType: 0x{:02X}, Remaining data: {}", 
                           dataLength, dataType, ByteBufUtil.hexDump(buf.readBytes(Math.min(readableByte, 20))));
                return;
            }
            LOGGER.warn("Insufficient data: readable={}, required={}, dataType=0x{:02X}, dataLength={}", 
                       readableByte, dataLength + 2, dataType, dataLength);
            return;
        }

        LOGGER.debug("Processing data type: 0x{:02X} with length: {}", dataType, dataLength);

        switch (dataType) {
            case 0x00:
                decodeGps(position, buf.readSlice(dataLength), buf);
                break;
            case 0x01:
                decodeWifi(position, buf.readSlice(dataLength), buf);
                break;
            case 0x02:
                decodeLbs(position, buf.readSlice(dataLength), buf);
                break;
            case 0x04:
                decodeAlarm(position, buf.readSlice(dataLength), buf);
                break;
            case 0x05:
                decodeHeartRate(position, buf.readSlice(dataLength), buf);
                break;
            case 0x06:
                decodeDeviceStatus(position, buf.readSlice(dataLength), buf);
                break;
            case 0x08:
                decodeMotion(position, buf.readSlice(dataLength), buf);
                break;
            case 0x20:
                decodeDeviceInfo(position, buf.readSlice(dataLength), buf);
                break;
            case 0x21:
                decodeMessage(position, buf.readSlice(dataLength), buf);
                break;
            case 0xFF:
                decodeTextData(position, buf.readSlice(dataLength), buf);
                break;
            default:
                LOGGER.info("Unknown Data Type: {} with length {}", dataType, dataLength);
                buf.skipBytes(dataLength);
                if (buf.readableBytes() > 0) {
                    decodeData(position, buf);
                }
                break;
        }
    }
}
