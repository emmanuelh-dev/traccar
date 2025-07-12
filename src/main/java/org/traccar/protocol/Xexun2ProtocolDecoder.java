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

    private static int calculateChecksum(byte[] data, int len) {
        int sum = 0;
        int j = 0;
        
        for (; len > 1; len--) {
            sum += data[j++] & 0xff;
            if ((sum & 0x80000000) > 0) {
                sum = (sum & 0xffff) + (sum >> 16);
            }
        }
        
        if (len == 1) {
            sum += data[data.length - 1] & 0xff;
        }
        
        while ((sum >> 16) > 0) {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        
        sum = (sum == 0xffff) ? sum & 0xffff : (~sum) & 0xffff;
        return sum;
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
        
        // According to protocol documentation for GPS Data Type ID: 00
        // 1. Timestamp (U32, 4 bytes)
        position.setTime(new Date(buf.readUnsignedInt() * 1000));
        
        // 2. Latitude (FLOAT, 4 bytes) + 3. Longitude (FLOAT, 4 bytes)
        if (buf.readableBytes() >= 8) {
            setCoordinates(position, buf);
        }
        
        // 4. Altitude (FLOAT, 4 bytes)
        if (buf.readableBytes() >= 4) {
            position.setAltitude(buf.readFloat());
        }
        
        // 5. Number of satellites (U8, 1 byte)
        if (buf.readableBytes() >= 1) {
            position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
        }
        
        // 6. Average signal-to-noise ratio (U8, 1 byte)
        if (buf.readableBytes() >= 1) {
            position.set("signalAvg", buf.readUnsignedByte());
        }
        
        // 7. Speed, 10*(km/h) (U16, 2 bytes)
        if (buf.readableBytes() >= 2) {
            position.setSpeed(UnitsConverter.knotsFromKph((double) buf.readUnsignedShort() / 10.0));
        }
        
        // 8. Angle, 10*(degree) (U16, 2 bytes)
        if (buf.readableBytes() >= 2) {
            position.setCourse((double) buf.readUnsignedShort() / 10.0);
        }
        
        // 9. Ephemeris synchronization flag (U8, 1 byte)
        if (buf.readableBytes() >= 1) {
            position.set("ephemerisSync", buf.readUnsignedByte());
        }
        
        // 10. Seconds to successful location (U8, 1 byte)
        if (buf.readableBytes() >= 1) {
            position.set("trackingSeconds", buf.readUnsignedByte());
        }
        
        // 11. Dilution of Precision (U16, 2 bytes)
        if (buf.readableBytes() >= 2) {
            position.setAccuracy((double) buf.readUnsignedShort() / 10.0);
        }
        
        // 12. Strongest signal-to-noise ratio of four satellites (U8[4], 4 bytes)
        if (buf.readableBytes() >= 4) {
            byte[] satelliteSignals = new byte[4];
            buf.readBytes(satelliteSignals);
            position.set("satelliteSignals", bytesToHex(satelliteSignals));
        }
        
        // 13. GPS status (U8, 1 byte) - 1=normal, 2=modified, 4=differential, 5=sub-meter
        if (buf.readableBytes() >= 1) {
            int gpsStatus = buf.readUnsignedByte();
            position.set("gpsStatus", gpsStatus);
            position.setValid(gpsStatus >= 1); // Valid if status is 1 or higher
        }
        
        // 14. Differential delay (U8, 1 byte) - only for RTK
        if (buf.readableBytes() >= 1) {
            position.set("differentialDelay", buf.readUnsignedByte());
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

        // Skip any remaining bytes in LBS data - don't try to interpret as coordinates
        // Coordinates should only come from GPS data blocks (type 0x00)
        if (buf.readableBytes() > 0) {
            LOGGER.debug("Skipping {} bytes of additional LBS data", buf.readableBytes());
            buf.skipBytes(buf.readableBytes());
        }

        if (remaining.readableBytes() > 0) {
            decodeData(position, remaining);
        }
    }

    private void setCoordinates(Position position, ByteBuf buf) {
        if (buf.readableBytes() >= 8) {
            // Log the raw bytes to understand the actual format
            byte[] coordBytes = new byte[8];
            buf.getBytes(buf.readerIndex(), coordBytes);
            LOGGER.info("Raw coordinate bytes: {}", ByteBufUtil.hexDump(coordBytes));
            
            // Try multiple coordinate formats
            buf.markReaderIndex();
            
            // Format 1: IEEE 754 FLOAT (original assumption)
            double lat1 = buf.readFloat();
            double lon1 = buf.readFloat();
            buf.resetReaderIndex();
            
            // Format 2: Fixed point (degrees * 1000000)
            int latFixed = buf.readInt();
            int lonFixed = buf.readInt();
            double lat2 = latFixed / 1000000.0;
            double lon2 = lonFixed / 1000000.0;
            buf.resetReaderIndex();
            
            // Format 3: NMEA format (DDMM.MMMM * 10000)
            int latNmea = buf.readInt();
            int lonNmea = buf.readInt();
            double lat3 = convertNmeaToDecimal(latNmea);
            double lon3 = convertNmeaToDecimal(lonNmea);
            buf.resetReaderIndex();
            
            // Format 4: Hexadecimal BCD format
            double lat4 = convertHexBcdToDecimal(coordBytes, 0);
            double lon4 = convertHexBcdToDecimal(coordBytes, 4);
            
            // Consume the 8 bytes
            buf.skipBytes(8);
            
            LOGGER.info("Coordinate formats - Float: lat={}, lon={}, Fixed: lat={}, lon={}, NMEA: lat={}, lon={}, HexBCD: lat={}, lon={}", 
                       lat1, lon1, lat2, lon2, lat3, lon3, lat4, lon4);
            
            // Try to determine which format is valid for Mexico region
            if (isValidMexicoCoordinate(lat1, lon1)) {
                position.setLatitude(lat1);
                position.setLongitude(lon1);
                position.setValid(true);
                LOGGER.info("Using FLOAT coordinates: lat={}, lon={}", lat1, lon1);
                return;
            } else if (isValidMexicoCoordinate(lat2, lon2)) {
                position.setLatitude(lat2);
                position.setLongitude(lon2);
                position.setValid(true);
                LOGGER.info("Using FIXED coordinates: lat={}, lon={}", lat2, lon2);
                return;
            } else if (isValidMexicoCoordinate(lat3, lon3)) {
                position.setLatitude(lat3);
                position.setLongitude(lon3);
                position.setValid(true);
                LOGGER.info("Using NMEA coordinates: lat={}, lon={}", lat3, lon3);
                return;
            } else if (isValidMexicoCoordinate(lat4, lon4)) {
                position.setLatitude(lat4);
                position.setLongitude(lon4);
                position.setValid(true);
                LOGGER.info("Using HexBCD coordinates: lat={}, lon={}", lat4, lon4);
                return;
            }
            
            // If Mexico coordinates fail, try general validation
            if (isValidCoordinate(lat1, lon1)) {
                position.setLatitude(lat1);
                position.setLongitude(lon1);
                position.setValid(true);
                LOGGER.info("Using FLOAT coordinates (general): lat={}, lon={}", lat1, lon1);
                return;
            } else if (isValidCoordinate(lat2, lon2)) {
                position.setLatitude(lat2);
                position.setLongitude(lon2);
                position.setValid(true);
                LOGGER.info("Using FIXED coordinates (general): lat={}, lon={}", lat2, lon2);
                return;
            } else if (isValidCoordinate(lat3, lon3)) {
                position.setLatitude(lat3);
                position.setLongitude(lon3);
                position.setValid(true);
                LOGGER.info("Using NMEA coordinates (general): lat={}, lon={}", lat3, lon3);
                return;
            } else if (isValidCoordinate(lat4, lon4)) {
                position.setLatitude(lat4);
                position.setLongitude(lon4);
                position.setValid(true);
                LOGGER.info("Using HexBCD coordinates (general): lat={}, lon={}", lat4, lon4);
                return;
            }
            
            LOGGER.warn("All coordinate formats failed - Raw bytes: {}", ByteBufUtil.hexDump(coordBytes));
        } else {
            LOGGER.debug("Insufficient data for GPS coordinates: {} bytes available", buf.readableBytes());
        }
    }

    private boolean isValidCoordinate(double lat, double lon) {
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0 && 
               (lat != 0.0 || lon != 0.0) && !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    private boolean isValidMexicoCoordinate(double lat, double lon) {
        // Mexico coordinates: latitude roughly 14-33, longitude roughly -118 to -86
        return lat >= 14.0 && lat <= 33.0 && lon >= -118.0 && lon <= -86.0 && 
               !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    private double convertNmeaToDecimal(int nmeaValue) {
        // Convert DDMM.MMMM format to decimal degrees
        // Example: 1905.84765625 -> 19 degrees + 5.84765625 minutes
        double value = Math.abs(nmeaValue) / 100.0;
        int degrees = (int) value;
        double minutes = (value - degrees) * 100.0;
        double result = degrees + minutes / 60.0;
        return nmeaValue < 0 ? -result : result;
    }

    private double convertHexBcdToDecimal(byte[] data, int offset) {
        // Convert 4 bytes of hexadecimal BCD to decimal coordinate
        if (offset + 4 > data.length) return 0.0;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%02X", data[offset + i] & 0xFF));
        }
        
        try {
            long value = Long.parseLong(sb.toString(), 16);
            return value / 1000000.0;
        } catch (NumberFormatException e) {
            return 0.0;
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
        
        // Log the complete raw message for analysis
        LOGGER.info("Raw message received (length={}): {}", buf.readableBytes(), 
                   ByteBufUtil.hexDump(buf.slice(0, Math.min(buf.readableBytes(), 200))));

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
        
        // Use the protocol-specific checksum algorithm
        byte[] checksumData = new byte[checksumBuf.readableBytes()];
        checksumBuf.getBytes(0, checksumData);
        int calculatedChecksum = calculateChecksum(checksumData, checksumData.length);
        
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

        ByteBuf tempBuf = buf.duplicate(); // Create a copy for scanning
        boolean hasValidGps = false;
        
        // Scan for GPS data first
        while (tempBuf.readableBytes() >= 2) {
            // Check for end flag
            if (tempBuf.readableBytes() >= 2) {
                int possibleFlag = tempBuf.getUnsignedShort(tempBuf.readerIndex());
                if (possibleFlag == FLAG) {
                    break;
                }
            }
            
            int dataType = tempBuf.readUnsignedByte();
            if (tempBuf.readableBytes() < 1) break;
            
            int dataLength = tempBuf.readUnsignedByte();
            if (dataLength > tempBuf.readableBytes()) break;
            
            if (dataType == 0x00 && dataLength >= 12) { // GPS data with timestamp + coordinates
                LOGGER.debug("Found GPS data block with length {}", dataLength);
                ByteBuf gpsData = tempBuf.readSlice(dataLength);
                if (gpsData.readableBytes() >= 12) { // timestamp(4) + lat(4) + lon(4)
                    position.setTime(new Date(gpsData.readUnsignedInt() * 1000));
                    setCoordinates(position, gpsData);
                    if (position.getLatitude() != 0 || position.getLongitude() != 0) {
                        hasValidGps = true;
                        LOGGER.debug("Extracted valid GPS coordinates in first pass: lat={}, lon={}", 
                                   position.getLatitude(), position.getLongitude());
                        break; // Use the first valid GPS data found
                    }
                }
            } else {
                tempBuf.skipBytes(dataLength);
            }
        }

        // Now process all data blocks normally
        decodeData(position, buf);

        // If no valid GPS was found in the main decode, use last known location
        if (!hasValidGps && (position.getLatitude() == 0 && position.getLongitude() == 0)) {
            LOGGER.debug("No valid GPS found, attempting to get last known location");
            getLastLocation(position, null);
            if (position.getLatitude() != 0 || position.getLongitude() != 0) {
                LOGGER.info("Used last known location: lat={}, lon={}", 
                           position.getLatitude(), position.getLongitude());
            }
        }

        // Log final coordinates for debugging
        if (position.getLatitude() != 0 || position.getLongitude() != 0) {
            LOGGER.info("Final position: lat={}, lon={}, valid={}, time={}", 
                       position.getLatitude(), position.getLongitude(), 
                       position.getValid(), position.getFixTime());
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
