package com.istorrent.lib;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Date;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.client.SharedTorrent;

public class SharedTorrentExt extends SharedTorrent {

	private final File destDir;

	public SharedTorrentExt(byte[] torrent, File destDir)
			throws FileNotFoundException, IOException {
		super(torrent, destDir);
		this.destDir = destDir;
	}

	@Override
	public synchronized void init() throws InterruptedException, IOException {
		try {
			super.init();
		} catch (IllegalStateException e) {
			//e.printStackTrace();
		}
	}

	public String getFullPath() {
		try {
			return new File(destDir.getCanonicalPath() + "/" + getName()).getCanonicalPath();
		} catch (IOException e) {
			return null;
		}
	}

	public String getCreationDate() {
		try {
			Field field = Torrent.class.getDeclaredField("creationDate");
			field.setAccessible(true);
			Object value = field.get(this);
			field.setAccessible(false);
			Date ret = (Date) value;
			return ret.toString();
		} catch (NoSuchFieldException | IllegalAccessException e) {
			//e.printStackTrace();
			return null;
		}
	}
}
