package com.example.mybluetoothchat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class MyBluetoothChatService {
	// Debugging
	private String target;
	private String myDeviceName;
	private Map<String, BluetoothSocket> myMap = new HashMap<String, BluetoothSocket>();
	private static final String TAG = "BluetoothChatService";
	private static final boolean D = true;
	public static final String CODES = "fuck you budy 23333";

	// Name for the SDP record when creating server socket
	private static final String NAME = "BluetoothChatMulti";

	// Member fields
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	// ֻ��һ�������߳�
	private AcceptThread mAcceptThread;
	// һ���߳���ͼȥ���ӱ���
	private ConnectThread mConnectThread;
	// �ж����������߳�
	private ConnectedThread mConnectedThread;
	private int mState;

	private ArrayList<String> mDeviceAddresses;
	private ArrayList<ConnectedThread> mConnThreads;
	// �벻����İ취��
	private ArrayList<ConnectThread> mConnectThreads = new ArrayList<ConnectThread>();
	private ArrayList<BluetoothSocket> mSockets;
	/**
	 * A bluetooth piconet can support up to 7 connections. This array holds 7
	 * unique UUIDs. When attempting to make a connection, the UUID on the
	 * client must match one that the server is listening for. When accepting
	 * incoming connections server listens for all 7 UUIDs. When trying to form
	 * an outgoing connection, the client tries each UUID one at a time.
	 */
	private ArrayList<UUID> mUuids;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0; // we're doing nothing
	public static final int STATE_LISTEN = 1; // now listening for incoming
												// connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing
													// connection
	public static final int STATE_CONNECTED = 3; // now connected to a remote
													// device

	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 * 
	 * @param context
	 *            The UI Activity Context
	 * @param handler
	 *            A Handler to send messages back to the UI Activity
	 */
	public MyBluetoothChatService(Context context, Handler handler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		myDeviceName = mAdapter.getName();
		mState = STATE_NONE;
		mHandler = handler;
		mDeviceAddresses = new ArrayList<String>();
		mConnThreads = new ArrayList<ConnectedThread>();
		mSockets = new ArrayList<BluetoothSocket>();
		mUuids = new ArrayList<UUID>();
		// socket��connect��serverSocket��accept���Ǹ������ɵ�uuid������ģ�������TCP��port��
		// 7 randomly-generated UUIDs. These must match on both server and
		// client.
		mUuids.add(UUID.fromString("b7746a41-c758-4868-aa19-7ac6b3475dfc"));
		mUuids.add(UUID.fromString("1d64189d-5a2c-4511-a074-77f199fd0834"));
		mUuids.add(UUID.fromString("e441e09a-51f3-4a7b-91cb-f638491d1412"));
		mUuids.add(UUID.fromString("a82d6504-4536-49ee-a475-7d96d09439e4"));
		mUuids.add(UUID.fromString("aa92eab1-d8ad-448e-abdb-95ebba4a9b55"));
		mUuids.add(UUID.fromString("1d34da73-d0a4-4f40-ac38-917e0a9dee97"));
		mUuids.add(UUID.fromString("2e14d4df-9c8a-4db7-81e4-c937564c86e0"));
	}

	/**
	 * Set the current state of the chat connection
	 * 
	 * @param state
	 *            An integer defining the current connection state
	 */
	private synchronized void setState(int state) {
		if (D)
			Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;

		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1)
				.sendToTarget();
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
	}

	/**
	 * Start the chat service. Specifically start AcceptThread to begin a
	 * session in listening (server) mode. Called by the Activity onResume()
	 */
	public synchronized void start() {
		if (D)
			Log.d(TAG, "start");

		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to listen on a BluetoothServerSocket
		if (mAcceptThread == null) {
			mAcceptThread = new AcceptThread();
			mAcceptThread.start();
		}
		setState(STATE_LISTEN);
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param device
	 *            The BluetoothDevice to connect
	 */
	public synchronized void connect(BluetoothDevice device) {
		if (D)
			Log.d(TAG, "connect to: " + device);

		Log.d("Test", "��ͼ���ӣ�" + device.getName());
		//���ӵ�ʱ���������е����˵����ӵ��ǻ���Ҫ��ͼȥ���ӵ��߳�
		//������������Ǽ������ģ����õ��İɡ�������������ϢһС���£����˼�һ������
		//Ȼ������հ�
		// Cancel any thread attempting to make a connection
		if (mState == STATE_CONNECTING) {
			if (mConnectThreads != null) {
				try {
					Thread.sleep(100);
					for(ConnectThread thread:mConnectThreads){
						thread.cancel();
						thread=null;
					}
				} catch (InterruptedException e) {
					
					e.printStackTrace();
				}
			}
		}

		// ����Ӻ�Ӧ�ñ���ԭ�е����Ӱ�
		// Cancel any thread currently running a connection
		/*
		 * if (mConnectedThread != null) { mConnectedThread.cancel();
		 * mConnectedThread = null; }
		 */

		// Create a new thread and attempt to connect to each UUID one-by-one.
		for (int i = 0; i < 7; i++) {
			try {
				// ��Ҫ����7��ConnectThread���õ�7��UUID��ÿ��ConnectThreadЯ��һ��socket�ͶԷ�device
				// ��ֻ�ܻ���һ��seversocktֻ������һ��socket��Ҳ������һ��UUIDֻ���ṩһ������
				mConnectThread = new ConnectThread(device, mUuids.get(i));
				mConnectThread.start();
				mConnectThreads.add(mConnectThread);
				setState(STATE_CONNECTING);
			} catch (Exception e) {
			}
		}
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 * 
	 * @param socket
	 *            The BluetoothSocket on which the connection was made
	 * @param device
	 *            The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device) {
		if (D)
			Log.d(TAG, "connected");

		// Commented out all the cancellations of existing threads, since we
		// want multiple connections.
		// Ӧ����ԭ�����������������Щ���ӣ�����Ϊ����ɶ��̣߳�����Щȡ���̵߳ĳ���ȫ��ע�͵���
		/*
		 * // Cancel the thread that completed the connection if (mConnectThread
		 * != null) {mConnectThread.cancel(); mConnectThread = null;}
		 * 
		 * // Cancel any thread currently running a connection if
		 * (mConnectedThread != null) {mConnectedThread.cancel();
		 * mConnectedThread = null;}
		 * 
		 * // Cancel the accept thread because we only want to connect to one
		 * device if (mAcceptThread != null) {mAcceptThread.cancel();
		 * mAcceptThread = null;}
		 */

		// Start the thread to manage the connection and perform transmissions

		mConnectedThread = new ConnectedThread(socket, device);
		Log.d("Test",
				"�Ѿ�connected��" + device.getName() + "\n" + device.getAddress());
		mConnectedThread.start();
		//����Ϊ�������
		mConnThreads.add(mConnectedThread);
		Log.d("Test", device.getName() + "�Ѿ���ӵ�����ά�����ӵ��߳�mConnThreads");
		// Send the name of the connected device back to the UI Activity
		myMap.put(mConnectedThread.device.getName(),
				mConnectedThread.mmSocket);
		Message msg = mHandler
				.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();

		bundle.putString(MainActivity.DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		// ����Ѿ������˼�������
		Log.d("Test", "�Ѿ�������connected�߳���Ŀ��" + mConnThreads.size());

		setState(STATE_CONNECTED);
		// Add each connected thread to an array
		// �������Ӻõ����߸��̣߳����뼯��

		/*if (mConnThreads.size() > 1) {
			for (int j = 0; j < mConnThreads.size(); j++) {
				if (mConnectedThread.device != mConnThreads.get(j).device) {
					mConnThreads.add(mConnectedThread);
					// Send the name of the connected device back to the UI
					// Activity
					myMap.put(mConnThreads.get(j).device.getName(),
							mConnThreads.get(j).mmSocket);
					Message msg = mHandler
							.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
					Bundle bundle = new Bundle();
					bundle.putString(MainActivity.DEVICE_NAME, device.getName());
					msg.setData(bundle);
					mHandler.sendMessage(msg);
					Log.d("Test", device.getName() + "�Ѿ���ӵ�Map�����ҷ��ͻ����������Ϣ");
					j++;
				} else {
					try {
						mConnectedThread.mmSocket.close();
						mConnectedThread.mmInStream.close();
						mConnectedThread.mmOutStream.close();
						mConnectedThread = null;
					} catch (Exception e) {

						e.printStackTrace();
					}

				}
			}
		} else {
			mConnThreads.add(mConnectedThread);
			Log.d("Test", device.getName() + "�Ѿ���ӵ�����ά�����ӵ��߳�mConnThreads");
			// Send the name of the connected device back to the UI Activity
			myMap.put(mConnectedThread.device.getName(),
					mConnectedThread.mmSocket);
			Message msg = mHandler
					.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
			Bundle bundle = new Bundle();

			bundle.putString(MainActivity.DEVICE_NAME, device.getName());
			msg.setData(bundle);
			mHandler.sendMessage(msg);
		}*/
		
		
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		if (D)
			Log.d(TAG, "stop");
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		setState(STATE_NONE);
		mConnThreads.clear();
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 * 
	 * @param out
	 *            The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void write(byte[] out) {
		// When writing, try to write out to all connected threads
		// ���е�д�����ⶼ��������ֱ�����߸��߳�д��Ҫ���͵���Ϣ
		for (int i = 0; i < mConnThreads.size(); i++) {
			try {
				// Create temporary object
				ConnectedThread r;
				// Synchronize a copy of the ConnectedThread
				synchronized (this) {
					if (mState != STATE_CONNECTED)
						return;
					r = mConnThreads.get(i);
				}
				// Perform the write unsynchronized
				r.write(out);
			} catch (Exception e) {
			}
		}
	}

	// ������˽�Ի�
	public void write(byte[] out, String target) {
		// When writing, try to write out to all connected threads
		// ���е�д�����ⶼ��������ֱ�����߸��߳�д��Ҫ���͵���Ϣ
		for (int i = 0; i < mConnThreads.size(); i++) {
			try {
				// Create temporary object
				ConnectedThread r;
				// Synchronize a copy of the ConnectedThread
				synchronized (this) {
					if (mState != STATE_CONNECTED)
						return;
					r = mConnThreads.get(i);
				}

				String tem1 = new String(out);
				String tem2 = tem1 + CODES + target;

				byte[] mbuffer = tem2.getBytes();
				// Perform the write unsynchronized
				// if()
				r.write(mbuffer);
			} catch (Exception e) {
			}
		}
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed() {
		setState(STATE_LISTEN);
		// Commented out, because when trying to connect to all 7 UUIDs,
		// failures will occur
		// for each that was tried and unsuccessful, resulting in multiple
		// failure toasts.
		/*
		 * // Send a failure message back to the Activity Message msg =
		 * mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST); Bundle bundle =
		 * new Bundle(); bundle.putString(BluetoothChat.TOAST,
		 * "Unable to connect device"); msg.setData(bundle);
		 * mHandler.sendMessage(msg);
		 */
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost(ConnectedThread connectedThread) {
		setState(STATE_LISTEN);

		connect(connectedThread.device);
		Log.d("Test","��Ȼ���ʵ����ˣ������һ���Ҫȥ����"+connectedThread.getName());
		MainActivity.chatingDeviceList.remove(connectedThread.device.getName());
		MainActivity.chatingDeviceArrayAdapter.notifyDataSetChanged();
		mConnThreads.remove(connectedThread);
		//��ͼ��ȥ��������豸
		
		
		/*
		 * mSockets.remove(connectedThread.mmSocket);
		 *  try {
		 * connectedThread.mmInStream.close();
		 * connectedThread.mmOutStream.close();
		 * connectedThread.mmSocket.close(); connectedThread = null; } catch
		 * (Exception e) { e.printStackTrace(); }
		 */

		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(MainActivity.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}

	/**
	 * This thread runs while listening for incoming connections. It behaves
	 * like a server-side client. It runs until a connection is accepted (or
	 * until cancelled).
	 */
	private class AcceptThread extends Thread {
		BluetoothServerSocket serverSocket = null;

		public AcceptThread() {
			
		}

		public void run() {
			if (D)
				Log.d(TAG, "BEGIN mAcceptThread" + this);
			setName("AcceptThread");
			BluetoothSocket socket = null;
			try {
				// ������ʵ����ֻ��һ�������߳��������
				// Listen for all 7 UUIDs
				// ͨ�����߸�uuid�����߸�serverSocket��Ȼ����ȥaccept�߸�socket
				for (int i = 0; i < 7; i++) {
					serverSocket = mAdapter.listenUsingRfcommWithServiceRecord(
							NAME, mUuids.get(i));
					socket = serverSocket.accept();
					if (socket != null) {
						String address = socket.getRemoteDevice().getAddress();
						Log.d("Test","���յ�����:"+socket.getRemoteDevice().getName());
						mDeviceAddresses.add(address);
						mSockets.add(socket);
						connected(socket, socket.getRemoteDevice());
						Log.d("Test", "accept���µ�socket���Ѿ���"+mDeviceAddresses.size()+"������ǰ�ǵ�"+i+"��UUID");
						/*if (mDeviceAddresses.size()>1) {
							// �жϵ�ǰ���ӳɹ����߳��Ƿ��Ѿ�����
							//��ʱȡ�������һ��
							for (int j = 0; j < mDeviceAddresses.size(); j++) {
								if (address != mDeviceAddresses.get(j)) {
									mDeviceAddresses.add(address);
									mSockets.add(socket);
									connected(socket, socket.getRemoteDevice());
									j++;
								} else {
									//������Ѿ�������socket���ص���
									try {
										socket.close();
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
							}	
							Log.d("Test", "accept���µ�socket���Ѿ���"+mDeviceAddresses.size()+"������ǰ�ǵ�"+i+"��UUID");
						} else {
							mDeviceAddresses.add(address);
							connected(socket, socket.getRemoteDevice());
							Log.d("Test", "accept���µ�socket���Ѿ���"+mDeviceAddresses.size()+"������ǰ�ǵ�"+i+"��UUID");
						}*/
						
						
					}
				}
			} catch (IOException e) {
				Log.d(TAG, "accept() failed"+ e.getMessage());
			}
			if (D)
				Log.i(TAG, "END mAcceptThread");
		}

		public void cancel() {
			if (D)
				Log.d(TAG, "cancel " + this);
			try {
				serverSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of server failed", e);
			}
		}
	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or
	 * fails. ��7��Я����ѡ��device�ͱ��豸ĳһ��UUID���̣߳�����������ֻ��һ���߳��ܹ����ƥ�䡣
	 */
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		private UUID tempUuid;

		public ConnectThread(BluetoothDevice device, UUID uuidToTry) {
			mmDevice = device;
			BluetoothSocket tmp = null;
			tempUuid = uuidToTry;
			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				tmp = device.createRfcommSocketToServiceRecord(uuidToTry);
			} catch (IOException e) {
				e.printStackTrace();
			}
			mmSocket = tmp;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectThread");
			setName("ConnectThread");

			// Always cancel discovery because it will slow down a connection
			mAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				// һ����һ���ɹ��ˣ�Ӧ������ȡ������������ͼ���е�connect�߳�
				mmSocket.connect();
			} catch (IOException e) {
				if (tempUuid.toString().contentEquals(mUuids.get(6).toString())) {
					connectionFailed();
					Log.d("Test",
							"��ͼ���ӣ�" + mmDevice.getName() + "ʧ�ܣ���Ϊ��"
									+ e.getCause());
				}
				// Close the socket
				try {
					mmSocket.close();
				} catch (IOException e2) {
					Log.e(TAG,
							"unable to close() socket during connection failure",
							e2);
				}
				// �����װ��ģ����ѵ�����һֱ����listening mode��
				// Start the service over to restart listening mode
				MyBluetoothChatService.this.start();
				Log.d("Test", "��ͼ�������ӣ�" + mmDevice.getName());
				return;
			}

			// Start the connected thread
			connected(mmSocket, mmDevice);
			Log.d("Test", "�Ѿ����ӵ������ҿ�ʼ�����߳�" + mmDevice.getName() + "\n"
					+ mmDevice.getAddress());

			// Reset the ConnectThread because we're done
			// �κ�һ��socket�ɹ�����֮���߳��������
			// �գ��鷳�ˣ��������߳���ô��û����������������ֽ�����һ���豸�����˶��socket���ӵ����
			synchronized (MyBluetoothChatService.this) {
				onceSomebodeConnected(mConnectThread);
				mConnectThread = null;
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
		private final BluetoothDevice device;

		public ConnectedThread(BluetoothSocket socket, BluetoothDevice device) {
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			this.device = device;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			Log.d("Test", "�Ѿ�����ConnectedThread��" + device.getName());
			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[1024];
			int bytes;

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					// ��ȡ���е��ֽڣ�����һ���ֽ���
					bytes = mmInStream.read(buffer);
					String tem3 = new String(buffer);
					// ������ʽ
					String matchCodes = ".*" + CODES + ".*";
					if (tem3.matches(matchCodes)) {
						String[] devicMesages = tem3.split("CODES");
						target = devicMesages[1];
						String tempMessage = devicMesages[0];
						BluetoothSocket tempDevice = myMap.get(target);
						byte[] send = tempMessage.getBytes();
						if (target != myDeviceName) {
							// �������д���Լ��ģ�д��ȥ��д�����ˡѦ��
							OutputStream othersOutputStream = tempDevice
									.getOutputStream();
							othersOutputStream.write(buffer);
						} else {
							// �����д���Լ��ģ�����ԭ������ֻ�ǰ�û�õ���Ϣ��ȥ
							mHandler.obtainMessage(MainActivity.MESSAGE_READ,
									bytes, -1, send).sendToTarget();
						}
					} else {
						// Send the obtained bytes to the UI Activity
						// ĿǰΪֹ����������һ����ȡ�˵ĸ�����ʲô����
						mHandler.obtainMessage(MainActivity.MESSAGE_READ,
								bytes, -1, buffer).sendToTarget();
					}

				} catch (IOException e) {
					Log.d("Test", "һ������" + device.getName()
							+ "connected�̶߳�ʧ����Ϊ��" + e.getMessage());
					connectionLost(this);
					break;
				}
			}
		}

		/**
		 * Write to the connected OutStream.
		 * 
		 * @param buffer
		 *            The bytes to write
		 */
		public void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);

				// Share the sent message back to the UI Activity
				mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1,
						buffer).sendToTarget();
			} catch (IOException e) {
				Log.d("Test", "��targer��connected�߳�д��������Ϊ��" + e.getMessage());
			}
		}

		// ��˽�Ի�д��
		public void write(byte[] buffer, String target) {
			try {
				mmOutStream.write(buffer);
				// Share the sent message back to the UI Activity
				mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1,
						buffer).sendToTarget();
			} catch (IOException e) {
				Log.d("Test", "��target��connected�߳�д��������Ϊ��" + e.getMessage());
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * ����ȡ����Щ���Ѿ��������ӳɹ�֮����ͼȥ���ӵ��߳�
	 * 
	 * @param connectThread
	 *            �������ӳɹ��������߳�
	 */

	private synchronized void onceSomebodeConnected(ConnectThread connectThread) {
		if (mConnectThreads != null) {
			for (ConnectThread thread : mConnectThreads) {
				thread = null;
			}
		}

	}

}
