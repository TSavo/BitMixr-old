package com.bitmixr;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.google.bitcoin.core.AbstractBlockChain;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.HeadersMessage;
import com.google.bitcoin.core.MemoryPool;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.core.WrongNetworkException;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.MemoryFullPrunedBlockStore;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "Bitmixr-servlet.xml" })
public class MainTest {

	@PersistenceContext
	EntityManager entityManager;
	final NetworkParameters params = NetworkParameters.prodNet();
	BlockStore blockStore;
	BlockChain chain;
	PeerGroup peerGroup;

	@Test
	@Transactional
	public void testMe() throws BlockStoreException, WrongNetworkException, AddressFormatException, InterruptedException, ScriptException {
		// Try to read the wallet from storage, create a new one if not
		// possible.
		Logger.getRootLogger().setLevel(Level.DEBUG);
		Logger.getLogger(com.google.bitcoin.core.BitcoinSerializer.class).setLevel(Level.WARN);
		Logger.getLogger(Wallet.class).setLevel(Level.INFO);
		Logger.getLogger(MemoryPool.class).setLevel(Level.INFO);
		Logger.getLogger(AbstractBlockChain.class).setLevel(Level.INFO);
		Logger.getLogger(HeadersMessage.class).setLevel(Level.INFO);

		blockStore = new MemoryFullPrunedBlockStore(params, Integer.MAX_VALUE);
		// Connect to the localhost node. One minute timeout since we won't try
		// any other peers
		System.out.println("Connecting ...");
		chain = new BlockChain(params, blockStore);
		peerGroup = new PeerGroup(params, chain);
		peerGroup.setUserAgent("PingService", "1.0");
		peerGroup.addPeerDiscovery(new DnsDiscovery(params));
		Iterable<Wallet> wallets = Iterables.transform(entityManager.createQuery("from Payment", Payment.class).getResultList(), new Function<Payment, Wallet>() {
			@Override
			public Wallet apply(Payment input) {
				try {
					return input.getWallet();
				} catch (IOException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
		});

		final Set<WalletActor> actors = new HashSet<>();
		for (Wallet w : wallets) {
			w.clearTransactions(0);
			List<ECKey> keys = new ArrayList<>(w.keychain);
			w.keychain.removeAll(keys);
			for (ECKey key : keys) {
				w.addKey(key);
				System.out.println(key.getPrivateKeyEncoded(params));
			}
			chain.addWallet(w);
			peerGroup.addWallet(w);
			final WalletActor actor = new WalletActor();
			actor.wallet = w;

			w.addEventListener(new AbstractWalletEventListener() {
				@Override
				public void onCoinsReceived(final Wallet w, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
					// MUST BE THREAD SAFE
					assert !newBalance.equals(BigInteger.ZERO);

					// It was broadcast, but we can't really verify it's valid
					// until it appears in a block.
					final BigInteger value = tx.getValueSentToMe(w);
					actor.addTotal(value);
					System.out.println("Received pending tx for " + Utils.bitcoinValueToFriendlyString(value) + ": " + tx);
					tx.getConfidence().addEventListener(new TransactionConfidence.Listener() {
						@Override
						public void onConfidenceChanged(final Transaction tx2) {
							// Must be thread safe.
							if (tx2.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
								// Coins were confirmed (appeared in a block).
								tx2.getConfidence().removeEventListener(this);

								try {
									System.out.println("Received " + value.toString() + " from " + tx2.getInputs().iterator().next().getFromAddress().toString());
								} catch (ScriptException e) {
									throw new RuntimeException(e.getMessage(), e);
								}
								if (!actors.contains(actor)) {
									actors.add(actor);
								}
								System.out.println(String.format("Confidence of %s is confirmed, is now: %s", tx2.getHashAsString(), tx2.getConfidence().toString()));

							}
						}
					});
				}
			});

		}

		// We want to know when the balance changes.

		peerGroup.startAndWait();
		// Now make sure that we shut down cleanly!
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
				while (true) {

					for (Iterator<WalletActor> i = actors.iterator(); i.hasNext();) {
						WalletActor w = i.next();

						// Now send the coins back!
						final Address from;
						try {
							from = new Address(params, "14rksGEhDzYUiRuYDAeS94huLn4f3Kxg2o");
						} catch (AddressFormatException e1) {
							throw new RuntimeException(e1.getMessage(), e1);
						}

						SendRequest request = Wallet.SendRequest.to(from, w.total);
						if (!w.wallet.completeTx(request))
							return; // Insufficient funds.
						// Ensure these funds won't be spent
						// again.
						try {
							w.wallet.commitTx(request.tx);
						} catch (VerificationException e) {
							throw new RuntimeException(e.getMessage(), e);
						}
						// wallet.saveToFile(...);
						// A proposed transaction is now sitting
						// in request.tx - send it in the
						// background.

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
						i.remove();
					}

				}
			}

		}.start();

		Thread.sleep(Integer.MAX_VALUE);
	}
}
