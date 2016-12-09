package com.conveyal.r5.analyst.cluster;

import com.conveyal.r5.publish.StaticSiteRequest;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class to read and write origin format files (used to send instantaneous accessibility distributions from the worker
 * to the assembler via SQS)
 */
public class Origin {
    public static final Logger LOG = LoggerFactory.getLogger(Origin.class);

    /** The version of the origin file format supported by this class */
    public static final int ORIGIN_VERSION = 0;

    /** X coordinate of origin within regional analysis */
    public int x;

    /** Y coordinate of origin within regional analysis */
    public int y;

    /** The instantaneous accessibility for each iteration */
    public int[] accessibilityPerIteration;

    /** Construct an origin given a grid request and the instantaneous accessibility computed for each iteration */
    public Origin (GridRequest request, int[] accessibilityPerIteration) {
        this.x = request.x;
        this.y = request.y;
        this.accessibilityPerIteration = accessibilityPerIteration;
    }

    /** allow construction of blank origin for static read method */
    private Origin() {
        /* do nothing */
    }

    public void write(OutputStream out) throws IOException {
        LittleEndianDataOutputStream data = new LittleEndianDataOutputStream(out);

        // Write the header
        for (char c : "ORIGIN".toCharArray()) {
            data.writeByte((byte) c);
        }

        // version
        data.writeInt(ORIGIN_VERSION);

        data.writeInt(x);
        data.writeInt(y);

        // write the number of iterations
        data.writeInt(accessibilityPerIteration.length);

        for (int i : accessibilityPerIteration) {
            // don't bother to delta code, these are small and we're not gzipping
            data.writeInt(i);
        }

        data.close();
    }

    public static Origin read (InputStream inputStream) throws IOException {
        LittleEndianDataInputStream data = new LittleEndianDataInputStream(inputStream);

        // ensure that it starts with ORIGIN
        char[] header = new char[6];
        for (int i = 0; i < 6; i++) {
            header[i] = (char) data.readByte();
        }

        if (!"ORIGIN".equals(new String(header))) {
            throw new IllegalArgumentException("Origin not in proper format");
        }

        int version = data.readInt();

        if (version != ORIGIN_VERSION) {
            LOG.error("Origin version mismatch , expected {}, found {}", ORIGIN_VERSION, version);
            throw new IllegalArgumentException("Origin version mismatch, expected " + ORIGIN_VERSION + ", found " + version);
        }

        Origin origin = new Origin();

        origin.x = data.readInt();
        origin.y = data.readInt();

        origin.accessibilityPerIteration = new int[data.readInt()];

        for (int iteration = 0, prev = 0; iteration < origin.accessibilityPerIteration.length; iteration++) {
            // de-delta-code the origin
            origin.accessibilityPerIteration[iteration] = (prev += data.readInt());
        }

        data.close();

        return origin;
    }
}