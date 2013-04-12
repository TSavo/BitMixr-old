package com.bitmixr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.spongycastle.asn1.sec.SECNamedCurves;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.params.ECDomainParameters;

import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.PrunedException;
import com.google.bitcoin.core.Script;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.store.BlockStoreException;

public class HashSweeper {

	public static List<String> allWords() throws IOException {
		List<String> words = new ArrayList<>();
		String line;
		BufferedReader br;
		int x = 0;
		try {
			br = new BufferedReader(new FileReader(new File("c:\\textlist\\rockyou.txt")));
		} catch (FileNotFoundException e1) {
			throw new RuntimeException(e1.getMessage(), e1);
		}
		while ((line = br.readLine()) != null) {
			words.add(line);
		}
		System.out.println(words.size());
		return words;
	}

	static Random random = new Random();

	public static String randomWord(List<String> words) {
		return words.get(random.nextInt(words.size()));
	}

	public static String fourRandomWords(List<String> words) {
		return randomWord(words) + " " + randomWord(words) + " " + randomWord(words) + " " + randomWord(words);
	}

	public static void main(String[] args) throws IOException, ProtocolException, VerificationException, PrunedException, BlockStoreException {
		final Set<byte[]> hashSet = allTractionHashes(NetworkParameters.prodNet());
		// final Set<byte[]> hashSet = new TreeSet<byte[]>(new
		// Comparator<byte[]>() {
		// @Override
		// public int compare(byte[] left, byte[] right) {
		// for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++)
		// {
		// int a = (left[i] & 0xff);
		// int b = (right[j] & 0xff);
		// if (a != b) {
		// return a - b;
		// }
		// }
		// return left.length - right.length;
		// }
		// });
		// FileInputStream reader = new FileInputStream("outputHashes");
		// byte[] hash = new byte[63];
		// while(reader.read(hash) == 63){
		// hashSet.add(hash);
		// }
		// reader.close();
		// final List<String> words = allWords();

		final AtomicInteger hashesPerSecond = new AtomicInteger(0);

		new Thread() {
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
		}.start();
		final FileWriter writer = new FileWriter("goodwords", true);
		final ReentrantLock lock = new ReentrantLock();
		new Thread() {
			@Override
			public void run() {
				final MessageDigest md;
				BufferedReader reader;
				try {
					reader = new BufferedReader(new FileReader("c:\\textlist\\rockyou.txt"));
				} catch (FileNotFoundException e1) {
					throw new RuntimeException(e1.getMessage(), e1);
				}
				X9ECParameters params = SECNamedCurves.getByName("secp256k1");
				ECDomainParameters ecParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
				int checkpoint = getCheckpoint();
				try {
					md = MessageDigest.getInstance("SHA-256");
				} catch (NoSuchAlgorithmException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
				int x = 0;
				String line;
				try {
					while ((line = reader.readLine()) != null) {
						hashesPerSecond.incrementAndGet();
						x++;
						if (x < checkpoint) {
							continue;
						}

						md.update(line.getBytes());
						if (hashSet.contains(Utils.sha256hash160(ecParams.getG().multiply(new BigInteger(1, md.digest())).getEncoded()))) {
							System.out.println(line);
							lock.lock();
							try {
								writer.append(line + "\n");
								writer.flush();
							} catch (IOException e) {
								throw new RuntimeException(e.getMessage(), e);
							}
							lock.unlock();
						}
						if (x % 1000 == 0) {
							setCheckpoint(x);
						}
					}
				} catch (IOException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
		}.start();

	}

	public static int getCheckpoint() {
		File checkpointFile = new File("checkpoint");
		final int checkpoint;
		if (checkpointFile.exists()) {
			BufferedReader checkpointReader;
			try {
				checkpointReader = new BufferedReader(new FileReader(checkpointFile));
			} catch (FileNotFoundException e1) {
				throw new RuntimeException(e1.getMessage(), e1);
			}
			try {
				checkpoint = Integer.parseInt(checkpointReader.readLine());
			} catch (NumberFormatException e1) {
				throw new RuntimeException(e1.getMessage(), e1);
			} catch (IOException e1) {
				throw new RuntimeException(e1.getMessage(), e1);
			}
			try {
				checkpointReader.close();
			} catch (IOException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		} else {
			checkpoint = 0;
		}
		return checkpoint;
	}

	public static void setCheckpoint(int aCheckpoint) {
		File checkpointFile = new File("checkpoint");
		if (checkpointFile.exists()) {
			try {
				checkpointFile.createNewFile();
			} catch (IOException e) {

			}
		}
		FileWriter writer;
		try {
			writer = new FileWriter(checkpointFile);
			writer.write(aCheckpoint + "\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {

		}

	}

	public static Set<byte[]> allTractionHashes(NetworkParameters someParams) throws IOException, ProtocolException, VerificationException, PrunedException, BlockStoreException {
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

		// TODO: Move this to a library function
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
