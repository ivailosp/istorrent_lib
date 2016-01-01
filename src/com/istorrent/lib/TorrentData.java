package com.istorrent.lib;

import java.util.*;
import java.net.*;
import java.io.*;
import com.turn.ttorrent.client.peer.SharingPeer;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class TorrentData {

	private ClientExt client;
	private final InetAddress address;
	private final SharedTorrentExt torrent;
	private int lolcalState = 0;
	private float localCompletion = 0.f;
	private final Timestamp added;

	public static final String NAME = "Name";
	public static final String SIZE = "Size";
	public static final String STATUS = "Status";
	public static final String DOWN_SPEED = "Down Speed";
	public static final String UP_SPEED = "Up Speed";
	public static final String ADDED = "Added";
	public static final String FULL_PATH = "Save as";
	public static final String CREATION_DATE = "Creation date";
	public static final String HASH = "Hash";
	public static final String COMMENT = "Comment";

	public TorrentData(InetAddress address, byte[] torrentData, File saveDir, Timestamp added) throws IOException {
		this.torrent = new SharedTorrentExt(torrentData, saveDir);
		this.address = address;
		this.added = added;
	}

	public void start() {
		if (client == null) {
			try {
				client = new ClientExt(address, torrent);
			} catch (Exception e) {
				//e.printStackTrace();
				return;
			}
		}
		client.share();
	}

	public void stop(boolean wait) {
		if (client != null) {
			final ClientExt stopClient = client;
			if (client.getState() == ClientExt.ClientState.DONE
					|| client.getState() == ClientExt.ClientState.SEEDING) {
				lolcalState = ClientExt.ClientState.DONE.ordinal();
			} else {
				lolcalState = ClientExt.ClientState.WAITING.ordinal();
			}
			client = null;
			if (wait == true) {
				stopClient.stop();
			} else {
				new Thread() {
					@Override
					public void run() {
						stopClient.stop();
					}
				}.start();
			}
		}
	}

	public String getHexInfoHash() {
		return torrent.getHexInfoHash();
	}

	public void setLolcalState(int lolcalState) {
		this.lolcalState = lolcalState;
	}

	public ClientExt.ClientState getState() {
		return ClientExt.ClientState.values()[getStateInt()];
	}

	private int getStateInt() {
		if (client == null) {
			return lolcalState;
		} else {
			return client.getState().ordinal();
		}
	}

	private static String hexToASCII(String hexValue) {
		StringBuilder output = new StringBuilder("");
		for (int i = 0; i < hexValue.length(); i += 2) {
			String str = hexValue.substring(i, i + 2);
			output.append((char) Integer.parseInt(str, 16));
		}
		return output.toString();
	}

	private Object getPeerInfo(String data, SharingPeer peer) {
		Object ret = null;
		switch (data) {
			case "IP":
				ret = peer.getIp() + ":" + peer.getPort();
				break;
			case "Client":
				String peerId = hexToASCII(peer.getHexPeerId());
				if (peerId.length() > 2) {
					int index = peerId.indexOf("-", 2);
					ret = peerId.substring(1, index);
				}
				break;
			case "%":
				int pieceCount = torrent.getPieceCount();
				if (pieceCount > 0) {
					float pr = (100.f * peer.getAvailablePieces().cardinality()) / pieceCount;
					ret = new PercentType(pr);
				}
				break;
			case "Down Speed":
				float dl = peer.getDLRate().get();
				ret = new SpeedType(dl);
				break;
			case "Up Speed":
				float ul = peer.getULRate().get();
				ret = new SpeedType(ul);
				break;
		}
		return ret;
	}

	private PeerData getPeersInfo(PeerDataFlags dataFlags) {
		PeerData ret = new PeerData();
		if (client != null) {
			Object[] obj;
			Set<SharingPeer> peers = client.getConnectedPeers();
			Object[] flags = dataFlags.getFlags();
			for (SharingPeer peer : peers) {
				obj = new Object[flags.length];
				for (int i = 0; i < flags.length; ++i) {
					obj[i] = getPeerInfo(flags[i].toString(), peer);
				}
				ret.addData(obj);
			}
		}
		return ret;
	}

	private Object getInfoData(String data) {
		Object ret = null;
		switch (data) {
			case NAME:
				ret = torrent.getName();
				break;
			case SIZE:
				ret = new SizeType(torrent.getSize());
				break;
			case STATUS:
				ret = new StateType(new PercentType(getCompletion()), getState());
				break;
			case DOWN_SPEED:
				float dl = 0.f;
				if (client != null) {
					Set<SharingPeer> peers = client.getConnectedPeers();
					for (SharingPeer peer : peers) {
						dl += peer.getDLRate().get();
					}
				}
				ret = new SpeedType(dl);
				break;
			case UP_SPEED:
				float ul = 0.f;
				if (client != null) {
					Set<SharingPeer> peers = client.getConnectedPeers();
					for (SharingPeer peer : peers) {
						ul += peer.getULRate().get();
					}
				}
				ret = new SpeedType(ul);
				break;
			case ADDED:
				SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
				ret = dateFormat.format(added);
				break;
			case FULL_PATH:
				ret = torrent.getFullPath();
				break;
			case CREATION_DATE:
				ret = torrent.getCreationDate();
				break;
			case HASH:
				ret = torrent.getHexInfoHash();
				break;
			case COMMENT:
				ret = torrent.getComment();
				break;
		}
		return ret;
	}

	public Object getInfo(Object info) {
		Object ret = null;
		if (info instanceof PeerDataFlags) {
			ret = getPeersInfo((PeerDataFlags) info);
		} else if (info instanceof String) {
			ret = getInfoData((String) info);
		}
		return ret;
	}

	public void setLocalCompletion(float localCompletion) {
		this.localCompletion = localCompletion;
	}

	public float getCompletion() {
		if (torrent.isInitialized()) {
			return torrent.getCompletion();
		} else {
			return localCompletion;
		}
	}

	public List<String> getFilenames() {
		return torrent.getFilenames();
	}

	public static class PeerData {

		private final List<Object[]> peerData;

		public PeerData() {
			peerData = new ArrayList<>();
		}

		public void addData(Object[] obj) {
			peerData.add(obj);
		}

		public List<Object[]> getPeerData() {
			return peerData;
		}
	}

	public static class PeerDataFlags {

		private final Object[] flags;

		public PeerDataFlags(Object[] flags) {
			this.flags = flags;
		}

		public Object[] getFlags() {
			return flags;
		}
	}

	public static class PercentType implements Comparable<PercentType> {

		public float value;

		public PercentType(float value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return String.format("%.1f%%", value);
		}

		@Override
		public int compareTo(PercentType n) {
			if (value == n.value) {
				return 0;
			}
			return value > n.value ? 1 : -1;
		}
	}

	public static class SizeType implements Comparable<SizeType> {

		public float value;

		public SizeType(float value) {
			this.value = value;
		}

		@Override
		public String toString() {
			float size = value;
			if (size <= 1000.) {
				return String.format("%.2f B", size);
			}
			size /= 1024.;
			if (size <= 1000.) {
				return String.format("%.2f kB", size);
			}
			size /= 1024.;
			if (size <= 1000.) {
				return String.format("%.2f MB", size);
			}
			size /= 1024.;
			return String.format("%.2f GB", size);
		}

		@Override
		public int compareTo(SizeType n) {
			if (value == n.value) {
				return 0;
			}
			return value > n.value ? 1 : -1;
		}
	}

	public static class SpeedType extends SizeType {

		public SpeedType(float value) {
			super(value);
		}

		@Override
		public String toString() {
			if (value > 0) {
				return super.toString() + "/s";
			} else {
				return null;
			}
		}
	}

	public static class StateType implements Comparable<StateType> {

		public PercentType pr;
		public ClientExt.ClientState state;

		public StateType(PercentType pr, ClientExt.ClientState state) {
			this.pr = pr;
			this.state = state;
		}

		@Override
		public String toString() {
			String ret = null;
			switch (state) {
				case WAITING:
					if (pr.value != 100.) {
						ret = "Stopped " + pr.toString();
					} else {
						ret = "Finished";
					}
					break;
				case VALIDATING:
					ret = "Checking...";
					break;
				case SHARING:
					ret = "Downloading " + pr.toString();
					break;
				case SEEDING:
					ret = "Seeding";
					break;
				case ERROR:
					ret = "Error " + pr.toString();
					break;
				case DONE:
					ret = "Finished";
					break;
			}
			return ret;
		}

		@Override
		public int compareTo(StateType n) {
			return pr.compareTo(n.pr);
		}
	}

}
