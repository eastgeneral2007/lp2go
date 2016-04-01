/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/* This file incorporates work covered by the following copyright and
 * permission notice:
 */

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.proest.lp2go3.UAVTalk.device;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import net.proest.lp2go3.H;
import net.proest.lp2go3.MainActivity;
import net.proest.lp2go3.UAVTalk.UAVTalkDeviceHelper;
import net.proest.lp2go3.UAVTalk.UAVTalkMessage;
import net.proest.lp2go3.UAVTalk.UAVTalkObject;
import net.proest.lp2go3.UAVTalk.UAVTalkObjectInstance;
import net.proest.lp2go3.UAVTalk.UAVTalkObjectTree;
import net.proest.lp2go3.UAVTalk.UAVTalkXMLObject;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class UAVTalkUsbDevice extends UAVTalkDevice {

    private final UsbDeviceConnection mDeviceConnection;
    private final UsbEndpoint mEndpointOut;
    private final UsbEndpoint mEndpointIn;
    private final WaiterThread mWaiterThread = new WaiterThread();
    private UsbRequest mOutRequest = null;
    private boolean connected = false;


    public UAVTalkUsbDevice(MainActivity activity, UsbDeviceConnection connection,
                            UsbInterface intf, HashMap<String, UAVTalkXMLObject> xmlObjects) {
        super(activity);

        //mActivity = activity;
        mDeviceConnection = connection;
        mObjectTree = new UAVTalkObjectTree();
        mObjectTree.setXmlObjects(xmlObjects);
        mActivity.setPollThreadObjectTree(mObjectTree);

        UsbEndpoint epOut = null;
        UsbEndpoint epIn = null;
        // look for our bulk endpoints
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    epOut = ep;
                } else {
                    epIn = ep;
                }
            }
        }
        if (epOut == null || epIn == null) {
            throw new IllegalArgumentException("not all endpoints found");
        }
        mEndpointOut = epOut;
        mEndpointIn = epIn;
    }

    @Override
    public void start() {
        this.connected = true;
        mWaiterThread.start();
    }

    @Override
    public void stop() {
        synchronized (mWaiterThread) {
            mWaiterThread.mStop = true;
        }
        this.connected = false;
    }

    @Override
    public boolean requestObject(String objectName) {
        return requestObject(objectName, 0);
    }

    @Override
    public boolean requestObject(String objectName, int instance) {
        UAVTalkXMLObject xmlObj = mObjectTree.getXmlObjects().get(objectName);

        if (xmlObj == null) {
            return false;
        }

        byte[] send = UAVTalkObject.getReqMsg((byte) 0x21, xmlObj.getId(), instance);
        mActivity.incTxObjects();
        writeByteArray(send);

        return true;
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public boolean isConnecting() {
        return this.connected;
    }

    @Override
    public boolean sendAck(String objectId, int instance) {
        byte[] send = mObjectTree.getObjectFromID(objectId).toMessage((byte) 0x23, instance, true);
        if (send != null) {
            mActivity.incTxObjects();

            writeByteArray(send);

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean sendSettingsObject(String objectName, int instance) {
        byte[] send;
        send = mObjectTree.getObjectFromName(objectName).toMessage((byte) 0x22, instance, false);

        if (send != null) {
            mActivity.incTxObjects();

            writeByteArray(send);

            requestObject(objectName, instance);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected boolean writeByteArray(byte[] bytes) {
        boolean retval = false;
        int psize = mEndpointOut.getMaxPacketSize() - 2;
        int toWrite = bytes.length;

        while (toWrite > 0) {
            int sendlen = toWrite - psize > 0 ? psize : toWrite;
            byte[] buffer = new byte[sendlen + 2];

            System.arraycopy(bytes, bytes.length - toWrite, buffer, 2, sendlen);
            buffer[0] = (byte) 0x02;//report id, is always 2. Period.
            buffer[1] = (byte) ((sendlen) & 0xff);//bytes to send, which is packet.size()-2

            if (mOutRequest == null) {
                mOutRequest = new UsbRequest();
                mOutRequest.initialize(mDeviceConnection, mEndpointOut);
            }

            retval = mOutRequest.queue(ByteBuffer.wrap(buffer), buffer.length);

            toWrite -= sendlen;
        }

        return retval;
    }

    /*
        @Override
        protected boolean writeByteArray(byte[] bytes) {
            int psize = mEndpointOut.getMaxPacketSize() - 2;
            int toWrite = bytes.length;

            while (toWrite > 0) {
                int sendlen = toWrite - psize > 0 ? psize : toWrite;
                byte[] buffer = new byte[sendlen + 2];

                System.arraycopy(bytes, bytes.length - toWrite, buffer, 2, sendlen);
                buffer[0] = (byte) 0x02;//report id, is always 2. Period.
                buffer[1] = (byte) ((sendlen) & 0xff);//bytes to send, which is packet.size()-2

                mDeviceConnection.bulkTransfer(mEndpointOut, buffer, buffer.length, 1000);

                toWrite -= sendlen;
            }

            return true;
        }
    */
    @Override
    public boolean sendSettingsObject(String objectName, int instance, String fieldName, int element, byte[] newFieldData, final boolean block) {
        if (block) mObjectTree.getObjectFromName(objectName).setWriteBlocked(true);
        UAVTalkDeviceHelper.updateSettingsObject(mObjectTree, objectName, instance, fieldName, element, newFieldData);

        sendSettingsObject(objectName, instance);

        if (block) mObjectTree.getObjectFromName(objectName).setWriteBlocked(false);
        return true;
    }

    private class WaiterThread extends Thread {

        public boolean mStop;

        public WaiterThread() {
            this.setName("LP2GoDeviceUsbWaiterThread");
        }

        public void run() {
            byte[] bytes;
            while (true) {
                if (mStop) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        //Thread wakes up
                    }
                    continue;
                }

                byte[] buffer = new byte[mEndpointIn.getMaxPacketSize()];

                int result = mDeviceConnection.bulkTransfer(mEndpointIn, buffer, buffer.length, 1000);

                if (result > 0 && mActivity.isReady && buffer.length > 3 && buffer[2] == 0x3C) {

                    bytes = buffer;

                    UAVTalkMessage cM = new UAVTalkMessage(bytes, 2);
                    if (cM.getLength() > 266) {
                        mActivity.incRxObjectsBad();
                        continue;
                    }

                    //TODO: crappy code...
                    if (cM.getLength() > mEndpointIn.getMaxPacketSize()) {
                        byte[] bigbuffer = new byte[mEndpointIn.getMaxPacketSize() * 2];
                        System.arraycopy(bytes, 0, bigbuffer, 0, mEndpointIn.getMaxPacketSize());
                        mDeviceConnection.bulkTransfer(mEndpointIn, buffer, buffer.length, 1000);
                        System.arraycopy(buffer, 2, bigbuffer, mEndpointIn.getMaxPacketSize(), mEndpointIn.getMaxPacketSize() - 2);
                        bytes = bigbuffer;
                        cM = new UAVTalkMessage(bytes, 2);
                    }

                    if (cM.getLength() > mEndpointIn.getMaxPacketSize() * 2) {
                        byte[] bigbuffer = new byte[mEndpointIn.getMaxPacketSize() * 3];
                        System.arraycopy(bytes, 0, bigbuffer, 0, mEndpointIn.getMaxPacketSize());
                        mDeviceConnection.bulkTransfer(mEndpointIn, buffer, buffer.length, 1000);
                        System.arraycopy(buffer, 2, bigbuffer, mEndpointIn.getMaxPacketSize(), mEndpointIn.getMaxPacketSize() - 2);
                        bytes = bigbuffer;
                        cM = new UAVTalkMessage(bytes, 2);
                    }

                    if (cM.getLength() > mEndpointIn.getMaxPacketSize() * 3) {
                        byte[] bigbuffer = new byte[mEndpointIn.getMaxPacketSize() * 4];
                        System.arraycopy(bytes, 0, bigbuffer, 0, mEndpointIn.getMaxPacketSize());
                        mDeviceConnection.bulkTransfer(mEndpointIn, buffer, buffer.length, 1000);
                        System.arraycopy(buffer, 2, bigbuffer, mEndpointIn.getMaxPacketSize(), mEndpointIn.getMaxPacketSize() - 2);
                        bytes = bigbuffer;
                        cM = new UAVTalkMessage(bytes, 2);
                    }

                    mActivity.incRxObjectsGood();

                    UAVTalkObject myObj = mObjectTree.getObjectFromID(H.intToHex(cM.getObjectId()));
                    UAVTalkObjectInstance myIns;

                    try {
                        myIns = myObj.getInstance(cM.getInstanceId());
                        myIns.setData(cM.getData());
                        myObj.setInstance(myIns);
                    } catch (Exception e) {
                        myIns = new UAVTalkObjectInstance(cM.getInstanceId(), cM.getData());
                        myObj.setInstance(myIns);
                    }
                    mObjectTree.updateObject(myObj);

                    switch (cM.getType()) {
                        case 0x20:
                            //handle default package, nothing to do
                            break;
                        case 0x21:
                            //handle request message, nobody should request from LP2Go (so we don't implement this)
                            Log.e("UAVTalk", "Received Object Request, but won't send any");
                            break;
                        case 0x22:
                            //handle object with ACK REQ, means send ACK
                            Log.d("UAVTalk", "Received Object with ACK Request for " + myObj.getId() + ":" + H.bytesToHex(cM.getData()));
                            sendAck(myObj.getId(), myIns.getId());

                            break;
                        case 0x23:
                            //handle received ACK, e.g. save in Object that it has been acknowledged
                            Log.w("UAVTalk", "Received ACK Object for " + myObj.getId() + ". Which is nice.");
                            break;
                        case 0x24:
                            //handle NACK, e.g. show warning
                            mActivity.incRxObjectsBad();
                            Log.w("UAVTalk", "Received NACK Object for " + myObj.getId() + ". Which is bad.");
                            break;
                        default:
                            mActivity.incRxObjectsBad();
                            Log.w("UAVTalk", "Received bad Object Type " + (cM.getType() & 0xff));
                            continue;
                    }

                    if (isLogging()) {
                        //TODO: CHECK IF CRC IS IN bytes!
                        log(bytes);
                    }
                }
            }
        }
    }
}
