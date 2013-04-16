package com.bitmixr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.spongycastle.util.encoders.Hex;

import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Script;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VerificationException;

public class HashChecker {

	public static void main(String[] args) throws IOException, ProtocolException, VerificationException {
		final Set<byte[]> hashSet = allTractionHashes(NetworkParameters.prodNet());

		final AtomicInteger hashesPerSecond = new AtomicInteger(0);

		Thread reporter = new Thread() {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						throw new RuntimeException(e.getMessage(), e);
					}
					System.out.println(hashesPerSecond.getAndSet(0));
				}
			}
		};
		reporter.setDaemon(true);
		reporter.start();
		final ReentrantLock lock = new ReentrantLock();
		try (final FileWriter writer = new FileWriter("goodWord", true)) {
			for (File dir : new File("d:\\hashes").listFiles()) {
				for (final File f : dir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File inDir, String name) {
						return name.contains("part");
					}
				})) {

					try (BufferedReader reader = new BufferedReader(new FileReader(f));) {
						String line;
						try {
							while ((line = reader.readLine()) != null) {
								hashesPerSecond.incrementAndGet();
								String[] re = line.split("\t");
								if (hashSet.contains(Hex.decode(re[re.length - 1]))) {
									System.out.println(line);
									lock.lock();
									ECKey key = new ECKey(new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(re[0].getBytes())));
									writer.append(line + " " + key.getPrivateKeyEncoded(NetworkParameters.prodNet()).toString() + "\n");
									writer.flush();
									lock.unlock();
								}
							}
						} catch (IOException e) {
							throw new RuntimeException(e.getMessage(), e);
						} catch (NoSuchAlgorithmException e) {
							throw new RuntimeException(e.getMessage(), e);
						}
					}
				}
			}
		}

	}

	
	//This method was shamelessly lifted from BitcoinJ by Mike Hern. Thanks to him for figuring out how to read the code.
	public static Set<byte[]> allTractionHashes(NetworkParameters someParams) throws IOException, ProtocolException, VerificationException {
		Set<byte[]> hashSet = new TreeSet<byte[]>(new Comparator<byte[]>() {
			@Override
			public int compare(byte[] left, byte[] right) {
				for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
					int a = (left[i] & 0xff);
					int b = (right[j] & 0xff);
					if (a != b) {
						return a - b;
					}
				}
				return left.length - right.length;
			}
		});

		String defaultDataDir;
		if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
			defaultDataDir = System.getenv("APPDATA") + "\\Bitcoin\\blocks\\";
		} else {
			defaultDataDir = System.getProperty("user.home") + "/Bitcoin/blocks/";
		}

		int i = 0;
		for (int j = 0; true; j++) {
			FileInputStream stream;
			System.out.println("Opening " + defaultDataDir + String.format("blk%05d.dat", j));
			try {
				stream = new FileInputStream(new File(defaultDataDir + String.format("blk%05d.dat", j)));
			} catch (FileNotFoundException e1) {
				System.out.println(defaultDataDir + String.format("blk%05d.dat", j));
				break;
			}
			while (stream.available() > 0) {
				try {
					int nextChar = stream.read();
					while (nextChar != -1) {
						if (nextChar != ((someParams.packetMagic >>> 24) & 0xff)) {
							nextChar = stream.read();
							continue;
						}
						nextChar = stream.read();
						if (nextChar != ((someParams.packetMagic >>> 16) & 0xff))
							continue;
						nextChar = stream.read();
						if (nextChar != ((someParams.packetMagic >>> 8) & 0xff))
							continue;
						nextChar = stream.read();
						if (nextChar == (someParams.packetMagic & 0xff))
							break;
					}
				} catch (IOException e) {
					break;
				}
				byte[] bytes = new byte[4];
				stream.read(bytes, 0, 4);
				long size = Utils.readUint32BE(Utils.reverseBytes(bytes), 0);
				if (size > Block.MAX_BLOCK_SIZE || size <= 0)
					continue;
				bytes = new byte[(int) size];
				stream.read(bytes, 0, (int) size);
				Block block = new Block(someParams, bytes);
				for (Transaction t : block.getTransactions()) {
					for (TransactionOutput output : t.getOutputs()) {
						Script script = output.getScriptPubKey();
						if (script.isSentToRawPubKey()) {
							hashSet.add(script.getPubKey());
						} else {
							try {
								hashSet.add(script.getPubKeyHash());
							} catch (Exception e) {

							}
						}
					}
				}

				if (i % 1000 == 0)
					System.out.println(i);
				i++;
			}
			stream.close();
		}
		return hashSet;
	}
}
