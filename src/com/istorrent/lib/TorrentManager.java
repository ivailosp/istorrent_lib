package com.istorrent.lib;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.Observable;
import java.util.HashMap;
import java.util.Map;
import com.turn.ttorrent.client.Client;
import java.sql.Timestamp;

public class TorrentManager extends Observable {

	private final Map<String, TorrentData> torrentDataList = new HashMap<>();
	private static TorrentManager instance = null;
	private final int SLEEP_TIME = 1000;
	private TorrentManagerCallback cb = null;
	private Thread looper = null;

	public interface TorrentManagerCallback {

		public void addTorrent(byte[] data, String sha, String savedir, Timestamp added);

		public void removeTorrent(String sha);

		public void updateTorrent(String sha, int status, Float percent);

		public Map<String, TorrentData> getAllTorrents();

		public void close();
	}

	protected TorrentManager() {
		start();
	}

	public final synchronized void start() {
		if (looper != null) {
			looper.interrupt();
			try {
				looper.join();
			} catch (InterruptedException ex) {
			}
		}
		looper = new Thread(new Runnable() {
			private volatile boolean execute;

			@Override
			@SuppressWarnings("SleepWhileInLoop")
			public void run() {
				execute = true;
				while (this.execute) {
					try {
						notifyTorrentObservers(false);
						Thread.sleep(SLEEP_TIME);
					} catch (InterruptedException e) {
						this.execute = false;
						//e.printStackTrace();
					}
				}
			}
		});
		looper.start();
	}

	public synchronized static TorrentManager getInstance() {
		if (instance == null) {
			instance = new TorrentManager();
		}
		return instance;
	}

	public synchronized void setCallback(TorrentManagerCallback cb) {
		this.cb = cb;
		if (cb != null) {
			torrentDataList.putAll(cb.getAllTorrents());
		}
	}

	private synchronized void notifyTorrentObservers(boolean forced) {
		if (forced == true || torrentDataList.size() > 0) {
			setChanged();
			notifyObservers();
		}
	}

	public synchronized String addNewTorrent(File torrentFile, File saveDir) throws IOException, IllegalArgumentException {
		byte[] data = new byte[(int) torrentFile.length()];
		new FileInputStream(torrentFile).read(data);
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		TorrentData td = new TorrentData(InetAddress.getLocalHost(), data, saveDir, ts);
		if (torrentDataList.containsKey(td.getHexInfoHash())) {
			throw new IllegalArgumentException();
		}
		torrentDataList.put(td.getHexInfoHash(), td);

		if (cb != null) {
			cb.addTorrent(data, td.getHexInfoHash(), saveDir.getAbsolutePath(), ts);
		}

		notifyTorrentObservers(true);
		return td.getHexInfoHash();
	}

	public synchronized void removeTorrent(String hash) {
		torrentDataList.remove(hash);

		if (cb != null) {
			cb.removeTorrent(hash);
		}

		notifyTorrentObservers(true);
	}

	public synchronized List<Object[]> getTorrentInfo(Object[] field, int mode) {
		List<Object[]> ret = new ArrayList<>();
		Object[] obj;

		for (Map.Entry<String, TorrentData> entry : torrentDataList.entrySet()) {
			TorrentData td = entry.getValue();
			switch (mode) {
				case 0:
					obj = getObjectInfo(field, td);
					ret.add(obj);
					break;
				case 1:
					if (td.getState() == Client.ClientState.WAITING
							|| td.getState() == Client.ClientState.SHARING) {
						obj = getObjectInfo(field, td);
						ret.add(obj);
					}
					break;
				case 2:
					if (td.getState() == Client.ClientState.SEEDING) {
						obj = getObjectInfo(field, td);
						ret.add(obj);
					}
					break;
				case 3:
					if (td.getState() == Client.ClientState.SEEDING
							|| td.getState() == Client.ClientState.DONE) {
						obj = getObjectInfo(field, td);
						ret.add(obj);
					}
					break;
				case 4:
					if (td.getState() == Client.ClientState.SEEDING
							|| td.getState() == Client.ClientState.SHARING
							|| td.getState() == Client.ClientState.VALIDATING) {
						obj = getObjectInfo(field, td);
						ret.add(obj);
					}
					break;
				case 5:
					if (td.getState() == Client.ClientState.WAITING
							|| td.getState() == Client.ClientState.ERROR
							|| td.getState() == Client.ClientState.DONE) {
						obj = getObjectInfo(field, td);
						ret.add(obj);
					}
					break;
			}

		}
		return ret;
	}

	private Object[] getObjectInfo(Object[] field, TorrentData td) {
		Object[] obj = new Object[field.length + 1];
		obj[0] = td.getHexInfoHash();
		for (int i = 0; i < field.length; ++i) {
			obj[i + 1] = td.getInfo(field[i]);
		}
		return obj;
	}

	private synchronized TorrentData getTorrentData(String hash) throws IllegalArgumentException {
		TorrentData td;
		if (torrentDataList.containsKey(hash)) {
			td = torrentDataList.get(hash);
		} else {
			throw new IllegalArgumentException();
		}
		return td;
	}

	public synchronized Object getTorrentInfo(Object field, String hash) throws IllegalArgumentException {
		TorrentData td;
		td = getTorrentData(hash);
		return td.getInfo(field);
	}

	public synchronized void startTorrent(String hash) throws IllegalArgumentException {
		if (torrentDataList.containsKey(hash)) {
			torrentDataList.get(hash).start();
			notifyTorrentObservers(false);
		} else {
			throw new IllegalArgumentException();
		}
	}

	public synchronized void stopTorrent(String hash) throws IllegalArgumentException {
		if (torrentDataList.containsKey(hash)) {
			torrentDataList.get(hash).stop(false);
			notifyTorrentObservers(false);
		} else {
			throw new IllegalArgumentException();
		}
	}

	public synchronized void shutdown() {
		if (looper != null) {
			looper.interrupt();
			try {
				looper.join();
			} catch (InterruptedException ex) {
			}
			looper = null;
		}
		for(Map.Entry<String, TorrentData> entry: torrentDataList.entrySet()) {
			TorrentData td = entry.getValue();
			if(cb != null) {
				cb.updateTorrent(entry.getKey(), td.getState().ordinal(), td.getCompletion());
			}
			td.stop(true);
		}
		if (cb != null) {
			cb.close();
		}
	}

	public synchronized List<String> getFilenamesFor(String hash) throws IllegalArgumentException {
		List<String> ret = null;
		if (torrentDataList.containsKey(hash)) {
			ret = torrentDataList.get(hash).getFilenames();
		} else {
			throw new IllegalArgumentException();
		}
		return ret;
	}
}
