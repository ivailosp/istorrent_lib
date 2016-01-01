package com.istorrent.lib;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentMap;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.lang.reflect.Field;
import com.turn.ttorrent.client.peer.SharingPeer;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;

public class ClientExt extends Client {

	public ClientExt(InetAddress address, SharedTorrent torrent)
			throws UnknownHostException, IOException {
		super(address, torrent);
	}

	@SuppressWarnings("unchecked")
	public Set<SharingPeer> getConnectedPeers() {
		try {
			Field field = Client.class.getDeclaredField("connected");
			field.setAccessible(true);
			Object value = field.get(this);
			field.setAccessible(false);
			ConcurrentMap<String, SharingPeer> connected = (ConcurrentMap<String, SharingPeer>) value;
			return new HashSet<>(connected.values());
		} catch (NoSuchFieldException | IllegalAccessException e) {
			//e.printStackTrace();
			return null;
		}
	}
}
