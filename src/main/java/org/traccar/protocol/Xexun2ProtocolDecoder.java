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
        // Based on the protocol documentation
        if (BitUtil.check(value, 0)) {
            return Position.ALARM_SOS;
        }
        if (BitUtil.check(value, 9)) {
            return Position.ALARM_REMOVING; // Strap removal
        }
        if (BitUtil.check(value, 10)) {
            return "strapConnection";
        }
        if (BitUtil.check(value, 12)) {
            return "curfewAnchorMotion";
        }
        if (BitUtil.check(value, 22)) {
            return Position.ALARM_POWER_OFF; // Car external power failure
        }
        if (BitUtil.check(value, 23)) {
            return Position.ALARM_FALL_DOWN; // Fall alarm
        }
        if (BitUtil.check(value, 24)) {
            return "accStartAlarm";
        }
        if (BitUtil.check(value, 25)) {
            return "doorAlarm";
        }
        if (BitUtil.check(value, 26)) {
            return "ephemerisDownloadFail";
        }
        return null;
    }

    private void decodeGps(Position position, ByteBuf buf, ByteBuf remaining) {
        if (buf.readableBytes() < 4) {
            LOGGER.warn("GPS data too short: {} bytes", buf.readableBytes());
            return;
        }
        
        LOGGER.debug("GPS data length: {}, content: {}", buf.readableBytes(), 
                    ByteBufUtil.hexDump(buf.slice(buf.readerIndex(), Math.min(buf.readableBytes(), 32))));
        
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        
        // Check if we have coordinate data
        if (buf.readableBytes() >= 8) {
            setCoordinates(position, buf);
        } else {
            LOGGER.debug("No coordinate data in GPS packet, only {} bytes remaining", buf.readableBytes());
        }
        
        // Read additional GPS data if available
        if (buf.readableBytes() >= 4) {
            position.setAltitude(buf.readFloat());
        }
        if (buf.readableBytes() >= 1) {
            position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
        }
        if (buf.readableBytes() >= 1) {
            position.set("signalAvg", buf.readUnsignedByte());
        }
        if (buf.readableBytes() >= 2) {
            position.setSpeed(UnitsConverter.knotsFromKph((double) buf.readUnsignedShort() / 10.0));
        }
        if (buf.readableBytes() >= 2) {
            position.setCourse((double) buf.readUnsignedShort() / 10.0);
        }
        if (buf.readableBytes() >= 1) {
            position.set("ephemerisSync", buf.readUnsignedByte());
        }
        if (buf.readableBytes() >= 1) {
            position.set("trackingSeconds", buf.readUnsignedByte());
        }
        if (buf.readableBytes() >= 2) {
            position.setAccuracy((double) buf.readUnsignedShort() / 10.0);
        }
        if (buf.readableBytes() >= 4) {
            byte[] satelliteSignals = new byte[4];
            buf.readBytes(satelliteSignals);
            position.set("satelliteSignals", bytesToHex(satelliteSignals));
        }

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
        if (buf.readableBytes() < 4) {
            LOGGER.warn("LBS data too short: {} bytes", buf.readableBytes());
            return;
        }
        
        LOGGER.debug("LBS data length: {}, content: {}", buf.readableBytes(), 
                    ByteBufUtil.hexDump(buf.slice(buf.readerIndex(), Math.min(buf.readableBytes(), 32))));
        
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        
        if (buf.readableBytes() >= 13) { // MCC(2) + MNC(2) + LAC(4) + CID(4) + RSSI(1)
            int mcc = buf.readUnsignedShort();
            int mnc = buf.readUnsignedShort();
            int lac = buf.readInt();
            long cid = buf.readUnsignedInt();
            int rssi = buf.readUnsignedByte();
            
            LOGGER.debug("LBS cell tower: MCC={}, MNC={}, LAC={}, CID={}, RSSI={}", 
                        mcc, mnc, lac, cid, rssi);
            
            CellTower cellTower = CellTower.from(mcc, mnc, lac, cid, rssi);
            if (position.getNetwork() == null) {
                position.setNetwork(new Network(cellTower));
            } else {
                position.getNetwork().setCellTowers(List.of(cellTower));
            }
        }

        // Only try to read coordinates if there's exactly 8 bytes left AND it looks like coordinate data
        if (buf.readableBytes() == 8) {
            LOGGER.debug("LBS has coordinate data, attempting to decode");
            setCoordinates(position, buf);
        } else if (buf.readableBytes() > 8) {
            LOGGER.debug("LBS has extra data ({} bytes), skipping coordinate parsing to avoid corruption", buf.readableBytes());
            // Skip remaining bytes as they might not be coordinates
            buf.skipBytes(buf.readableBytes());
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
            // Store original position for debugging
            int originalReaderIndex = buf.readerIndex();
            
            // Log the 8 bytes we're about to interpret as coordinates
            ByteBuf coordBuf = buf.slice(buf.readerIndex(), 8);
            LOGGER.debug("Coordinate bytes: {}", ByteBufUtil.hexDump(coordBuf));
            
            // Try reading as float first
            double latitude = buf.readFloat();
            double longitude = buf.readFloat();
            
            LOGGER.debug("Raw float coordinates: lat={}, lon={}", latitude, longitude);
            
            // Check if coordinates are in valid range
            if (latitude >= -90.0 && latitude <= 90.0 && longitude >= -180.0 && longitude <= 180.0) {
                if (latitude != 0 || longitude != 0) {
                    position.setLatitude(latitude);
                    position.setLongitude(longitude);
                    position.setValid(true);
                    return;
                }
            }
            
            // If float coordinates are invalid, try as fixed point coordinates
            // Reset buffer position to try different format
            buf.readerIndex(originalReaderIndex);
            
            // Try reading as 32-bit integers (degrees * 10^6 format)
            int latInt = buf.readInt();
            int lonInt = buf.readInt();
            
            double latDegrees = latInt / 1000000.0;
            double lonDegrees = lonInt / 1000000.0;
            
            LOGGER.debug("Fixed point coordinates: lat={}, lon={} (raw: {}, {})", 
                        latDegrees, lonDegrees, latInt, lonInt);
            
            if (latDegrees >= -90.0 && latDegrees <= 90.0 && lonDegrees >= -180.0 && lonDegrees <= 180.0) {
                if (latDegrees != 0 || lonDegrees != 0) {
                    position.setLatitude(latDegrees);
                    position.setLongitude(lonDegrees);
                    position.setValid(true);
                    return;
                }
            }
            
            // If still invalid, try as little-endian format
            buf.readerIndex(originalReaderIndex);
            int latLE = Integer.reverseBytes(buf.readInt());
            int lonLE = Integer.reverseBytes(buf.readInt());
            
            double latLE_degrees = latLE / 1000000.0;
            double lonLE_degrees = lonLE / 1000000.0;
            
            LOGGER.debug("Little-endian coordinates: lat={}, lon={} (raw: {}, {})", 
                        latLE_degrees, lonLE_degrees, latLE, lonLE);
            
            if (latLE_degrees >= -90.0 && latLE_degrees <= 90.0 && lonLE_degrees >= -180.0 && lonLE_degrees <= 180.0) {
                if (latLE_degrees != 0 || lonLE_degrees != 0) {
                    position.setLatitude(latLE_degrees);
                    position.setLongitude(lonLE_degrees);
                    position.setValid(true);
                    return;
                }
            }
            
            // All formats failed - log detailed information but don't mark as invalid
            LOGGER.warn("All coordinate formats failed - Float: lat={}, lon={}, Fixed: lat={}, lon={}, LE: lat={}, lon={}", 
                       latitude, longitude, latDegrees, lonDegrees, latLE_degrees, lonLE_degrees);
            
            // Skip the 8 bytes we couldn't interpret
            buf.readerIndex(originalReaderIndex + 8);
            position.setValid(false);
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
        // Handle data type 0x20 - Version Data (device configuration/info data)
        if (buf.readableBytes() > 0) {
            // Read status byte
            int status = buf.readUnsignedByte();
            position.set("deviceStatus", status);
            position.set("autoRestart", BitUtil.check(status, 0));
            position.set("manualRestart", BitUtil.check(status, 1));
            position.set("requestServerSync", BitUtil.check(status, 3));
            
            // Read version info (32 bytes)
            if (buf.readableBytes() >= 32) {
                byte[] versionBytes = new byte[32];
                buf.readBytes(versionBytes);
                String version = new String(versionBytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
                String[] versionParts = version.split("\\|");
                if (versionParts.length >= 2) {
                    position.set("upperVersion", versionParts[0]);
                    position.set("lowerVersion", versionParts[1]);
                }
            }
            
            // Read ICCID (10 bytes BCD)
            if (buf.readableBytes() >= 10) {
                byte[] iccidBytes = new byte[10];
                buf.readBytes(iccidBytes);
                String iccid = ByteBufUtil.hexDump(iccidBytes);
                position.set("iccid", iccid);
            }
            
            // Read product model length and data
            if (buf.readableBytes() >= 1) {
                int modelLength = buf.readUnsignedByte();
                if (buf.readableBytes() >= modelLength) {
                    byte[] modelBytes = new byte[modelLength];
                    buf.readBytes(modelBytes);
                    String model = new String(modelBytes, java.nio.charset.StandardCharsets.US_ASCII);
                    position.set("productModel", model);
                }
            }
            
            LOGGER.info("Device info received - Status: {}", status);
        }
        
        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeTof(Position position, ByteBuf buf, ByteBuf remaining) {
        // Handle data type 0x03 - TOF (Time of Flight) data
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        long relatedId = buf.readUnsignedInt();
        int distance = buf.readUnsignedShort(); // Distance in cm
        int power = buf.readUnsignedShort();
        
        position.set("relatedId", relatedId);
        position.set("distance", distance);
        position.set("power", power);
        
        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void decodeFingerprint(Position position, ByteBuf buf, ByteBuf remaining) {
        // Handle data type 0x07 - Fingerprint punch-in data
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        long fingerprintId = buf.readUnsignedInt();
        position.set("fingerprintId", fingerprintId);
        
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

        LOGGER.debug("Message parsing: type=0x{}, index={}, length={}, checksum=0x{}", 
                    String.format("%02X", type), index, length, String.format("%04X", checksum));

        // Create a slice for checksum calculation to avoid reading beyond message boundary
        ByteBuf checksumBuf = buf.slice(buf.readerIndex(), Math.min(length, buf.readableBytes()));
        int calculatedChecksum = Checksum.ip(checksumBuf.nioBuffer());
        
        if (checksum != calculatedChecksum) {
            LOGGER.warn("Checksum mismatch: expected=0x{}, calculated=0x{}, length={}, data={}", 
                       String.format("%04X", checksum), String.format("%04X", calculatedChecksum), 
                       length, ByteBufUtil.hexDump(checksumBuf.slice(0, Math.min(32, checksumBuf.readableBytes()))));
            // Don't return null, continue processing as data might still be valid
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
        // Check if we have enough bytes to continue
        if (buf.readableBytes() < 2) {
            // If there are remaining bytes but less than 2, log them and return
            if (buf.readableBytes() > 0) {
                LOGGER.debug("Remaining data too short: {} bytes - {}", 
                           buf.readableBytes(), ByteBufUtil.hexDump(buf.readBytes(buf.readableBytes())));
            }
            return;
        }

        // Check if we've reached the end flag (0xFAAF) - check both possible positions
        if (buf.readableBytes() >= 2) {
            int possibleFlag = buf.getUnsignedShort(buf.readerIndex());
            if (possibleFlag == FLAG) {
                LOGGER.debug("Reached end flag, stopping data parsing");
                return;
            }
        }
        
        // Also check if the first byte is 0xFA (start of end flag)
        int firstByte = buf.getUnsignedByte(buf.readerIndex());
        if (firstByte == 0xFA) {
            // Check if this could be the start of the end flag
            if (buf.readableBytes() >= 2) {
                int possibleFlag = buf.getUnsignedShort(buf.readerIndex());
                if (possibleFlag == FLAG) {
                    LOGGER.debug("Reached end flag (0xFAAF), stopping data parsing");
                    return;
                }
            } else if (buf.readableBytes() == 1) {
                // Only one byte left and it's 0xFA, likely incomplete end flag
                LOGGER.debug("Found partial end flag (0xFA), stopping data parsing");
                return;
            }
        }

        int readableByte = buf.readableBytes();
        int dataType = buf.readUnsignedByte();
        
        // Additional safety check - if dataType is 0xFA, it's likely the start of end flag
        if (dataType == 0xFA) {
            // Check if next byte is 0xAF (completing the 0xFAAF flag)
            if (buf.readableBytes() >= 1 && buf.getUnsignedByte(buf.readerIndex()) == 0xAF) {
                LOGGER.debug("Detected end flag bytes (0xFAAF), stopping data parsing");
                buf.readerIndex(buf.readerIndex() - 1); // Reset reader to before 0xFA
                return;
            }
        }
        
        int dataLength = buf.readUnsignedByte();

        LOGGER.debug("Data parsing: readable={}, dataType=0x{}, dataLength={}", 
                    readableByte, String.format("%02X", dataType), dataLength);
        
        // Log the next few bytes to help debug data structure issues
        if (buf.readableBytes() >= 4) {
            ByteBuf tempBuf = buf.slice(buf.readerIndex(), Math.min(buf.readableBytes(), 16));
            LOGGER.debug("Next bytes: {}", ByteBufUtil.hexDump(tempBuf));
        }

        // Validate data length first
        if (dataLength > 150 || dataLength < 0) {
            // If data length seems unreasonable, might be corrupted data
            LOGGER.warn("Suspicious data length: {} bytes, skipping. DataType: 0x{}, Remaining data: {}", 
                       dataLength, String.format("%02X", dataType), ByteBufUtil.hexDump(buf.readBytes(Math.min(buf.readableBytes(), 20))));
            return;
        }
        
        // Check if we have enough bytes for the data payload (we already read 2 bytes for type and length)
        if (buf.readableBytes() < dataLength) {
            LOGGER.warn("Insufficient data: readable={}, required={}, dataType=0x{}, dataLength={}", 
                       buf.readableBytes(), dataLength, String.format("%02X", dataType), dataLength);
            return;
        }

        LOGGER.debug("Processing data type: 0x{} with length: {}", String.format("%02X", dataType), dataLength);
        
        // Log the actual data being processed for debugging
        if (buf.readableBytes() >= dataLength) {
            ByteBuf dataBuf = buf.slice(buf.readerIndex(), dataLength);
            LOGGER.debug("Data content: {}", ByteBufUtil.hexDump(dataBuf));
        }

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
            case 0x03:
                decodeTof(position, buf.readSlice(dataLength), buf);
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
            case 0x07:
                decodeFingerprint(position, buf.readSlice(dataLength), buf);
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
