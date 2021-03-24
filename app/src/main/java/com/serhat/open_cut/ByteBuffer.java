package com.serhat.open_cut;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;



public class ByteBuffer{
    private byte[] mBuffer = null;
    private UniversalDetector mCharsetDetector = null;

    public ByteBuffer(){
        mCharsetDetector = new UniversalDetector(null);
    }

    public ByteBuffer(byte[] buffer){
        mCharsetDetector = new UniversalDetector(null);
        setData(buffer);
    }

    public void replace(byte[] search, byte[] replace){
        int idx = indexOf(search),
                slen = search.length,
                rlen = replace.length;
        byte[] left, right;

        ByteBuffer buffer;

        while(idx != -1){
            left = Arrays.copyOfRange(mBuffer, 0, idx);
            right = Arrays.copyOfRange(mBuffer, idx + slen, mBuffer.length);
            buffer = new ByteBuffer(left);

            buffer.append(replace, rlen);
            buffer.append(right, right.length);

            mBuffer = buffer.mBuffer;

            idx = indexOf(search);
        }
    }

    public void append(byte[] buffer, int length){
        byte[] chunk = Arrays.copyOfRange(buffer, 0, length),
                reallcd;
        int i, j;

        mCharsetDetector.handleData(buffer, 0, length);

        if(mBuffer == null)
            mBuffer = chunk;

        else{
            reallcd = new byte[mBuffer.length + length];

            for(i = 0; i < mBuffer.length; i++)
                reallcd[i] = mBuffer[i];

            for(j = 0; j < length; i++, j++)
                reallcd[i] = chunk[j];

            mBuffer = reallcd;
        }

    }


    public int indexOf(byte[] pattern, int start){
        int i, j, plen = pattern.length, stop = mBuffer.length - plen;

        loop:
        for(i = start; i < stop; i++){
            if(pattern[0] == mBuffer[i]){
                for(j = 1; j < plen; j++){
                    if(pattern[j] != mBuffer[i + j])
                        continue loop;
                }

                return i;
            }
        }

        return -1;
    }


    public int indexOf(byte[] pattern){
        return indexOf(pattern, 0);
    }

    public String toString(){
        mCharsetDetector.dataEnd();

        try{
            if(isEmpty())
                return "";

            else{
                String charset = mCharsetDetector.getDetectedCharset();

                return new String(mBuffer, charset != null ? charset : "UTF-8");
            }
        } catch(UnsupportedEncodingException e){
            System.errorLogging(e);
        }

        return new String(mBuffer);
    }

    public byte[] getData(){
        return mBuffer;
    }

    public void setData(byte[] buffer){
        mBuffer = buffer;
        mCharsetDetector.handleData(buffer, 0, buffer.length);
    }

    public int getLength(){
        return (mBuffer == null ? 0 : mBuffer.length);
    }

    public boolean isEmpty(){
        return getLength() == 0;
    }
}
