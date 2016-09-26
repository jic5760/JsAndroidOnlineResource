package kr.jclab.javautils;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class JsByteBuilder {
    private byte[] array;
    private int size;

    private int pagesize = 1048576;

    public JsByteBuilder() {
        this(10);
    }

    public JsByteBuilder(int capacity) {
        this.array = new byte[capacity];
        this.size = 0;
    }

    public JsByteBuilder(byte[] array) {
        this.array = Arrays.copyOf(this.array, this.array.length * 2);
        this.size = this.array.length;
    }

    public byte[] getArray() {
        return this.array;
    }

    public void setPageSize(int pagesize) {
        this.pagesize = pagesize;
    }

    public int getPageSize() {
        return this.pagesize;
    }

    public void ensureCapacity(int capacity) {
        if (capacity > this.array.length) {
            this.array = Arrays.copyOf(this.array, capacity);
        }
    }

    private void testAddition(int addition) {
        while (this.size + addition >= this.array.length) {
            this.array = Arrays.copyOf(this.array, this.array.length + 1048576);
        }
    }

    public int capacity() {
        return this.array.length;
    }

    public int size() {
        return this.size;
    }

    public void truncate(int size) {
        this.array = Arrays.copyOf(this.array, size);
    }

    public byte[] subSequence(int start, int end) {
        return Arrays.copyOfRange(this.array, start, end);
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(this.array, this.size);
    }

    private static String toStringCutNull(byte[] rawdata) {
        String str = new String(rawdata);
        int p = str.indexOf(0);
        if (p >= 0)
            str = str.substring(0, p);
        return str;
    }

    private static String toStringCutNull(byte[] rawdata, String encoding) throws UnsupportedEncodingException {
        String str = new String(rawdata, encoding);
        int p = str.indexOf(0);
        if (p >= 0)
            str = str.substring(0, p);
        return str;
    }

    @Override
    public String toString() {
        return toStringCutNull(this.array);
    }

    public String toString(String encoding) throws UnsupportedEncodingException {
        return toStringCutNull(this.array, encoding);
    }

    public void CopyToBuffer(int bufferoffset, byte[] value, int offset, int len) {
        if ((offset + len) > value.length)
            throw new ArrayIndexOutOfBoundsException();
        int addititon = (bufferoffset + len) - this.array.length;
        int addititon2 = (bufferoffset + len) - this.size;
        if (addititon > 0)
            ensureCapacity(this.array.length + addititon);
        System.arraycopy(value, offset, this.array, bufferoffset, len);
        if (addititon2 > 0)
            this.size += addititon2;
    }

    public JsByteBuilder append(byte value) {
        testAddition(1);
        this.array[this.size++] = value;
        return this;
    }

    public JsByteBuilder append(byte[] value) {
        return this.append(value, 0, value.length);
    }

    public JsByteBuilder append(byte[] value, final int offset, final int len) {
        if (offset + len > value.length)
            throw new ArrayIndexOutOfBoundsException();

        testAddition(len);
        for (int i = 0; i < len; i++) {
            this.array[this.size + (i)] = value[i + offset];
        }
        this.size += len;
        return this;
    }

    public JsByteBuilder append(char value) {
        testAddition(1);
        this.array[this.size++] = (byte) value;
        return this;
    }

    public JsByteBuilder append(char[] value) {
        return this.append(value, 0, value.length);
    }

    public JsByteBuilder append(char[] value, final int offset, final int len) {
        if (offset + len > value.length)
            throw new ArrayIndexOutOfBoundsException();

        testAddition(len);
        for (int i = 0; i < len; i++) {
            this.array[size + (i)] = (byte) value[i + offset];
        }
        size += len;
        return this;
    }

    public JsByteBuilder append(JsByteBuilder value) {
        testAddition(value.size);
        return this.append(value.array, 0, value.size);
    }
}