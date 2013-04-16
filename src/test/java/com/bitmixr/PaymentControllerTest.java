package com.bitmixr;

import java.math.BigInteger;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.cpn.apiomatic.rest.RestCommand;
import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "Bitmixr-servlet.xml" })
public class PaymentControllerTest {

	@PersistenceContext
	EntityManager entityManager;

	@Test
	@Transactional
	public void testMe() throws BlockStoreException, InterruptedException {
		final NetworkParameters params = NetworkParameters.prodNet();
		final BlockStore blockStore;
		final BlockChain chain;
		final PeerGroup peerGroup;
		blockStore = new MemoryBlockStore(params);
		chain = new BlockChain(params, blockStore);
		peerGroup = new PeerGroup(params, chain);
		peerGroup.setUserAgent("PingService", "1.0");
		peerGroup.addPeerDiscovery(new SeedPeers(params));
		peerGroup.addPeerDiscovery(new DnsDiscovery(params));
		// peerGroup.setFastCatchupTimeSecs((System.currentTimeMillis() / 1000)
		// - 6000000);
		// PeerRestarter pr = new PeerRestarter(peerGroup);
		final Wallet wallet = new Wallet(params);
		chain.addWallet(wallet);
		peerGroup.addWallet(wallet);
		wallet.addEventListener(new AbstractWalletEventListener() {
			@Override
			public void onCoinsReceived(final Wallet w, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
				final BigInteger value = tx.getValueSentToMe(w);
				System.out.println("Received pending tx for " + Utils.bitcoinValueToFriendlyString(value) + ": " + tx);
			}

			@Override
			public void onCoinsSent(Wallet wallet1, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
				try {
					System.out.println("Spent: " + Utils.bitcoinValueToFriendlyString(tx.getValueSentFromMe(wallet1)) + ": " + tx);
				} catch (ScriptException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
		});
		for (ExpiredECKey key : entityManager.createQuery("from ExpiredECKey", ExpiredECKey.class).getResultList()) {
			wallet.addKey(key.getECKey());
		}
		peerGroup.startAndWait();
		peerGroup.waitForPeers(2);
		peerGroup.downloadBlockChain();
		Thread send = new Thread() {
			@Override
			public void run() {
				// Now send the coins back!
				while (true) {
					if (wallet.getBalance().compareTo(BigInteger.ZERO) <= 0) {
						return;
					}
					final BigInteger value = wallet.getBalance();
					final Address from;
					try {
						AddressResponse tipAddress = new RestCommand<String, AddressResponse>("https://blockchain.info/merchant/e3625e3b-81ff-830e-9fcd-59c64bd4ade9/new_address?password=wookiechew&second_password=rockandroll!!!&label=ExpiredKeySweep", AddressResponse.class).get();
						from = new Address(params, tipAddress.getAddress());
					} catch (AddressFormatException e1) {
						throw new RuntimeException(e1.getMessage(), e1);
					}
					//
					SendRequest request = Wallet.SendRequest.to(from, wallet.getBalance());
					if (!wallet.completeTx(request)) {
						return; // Insufficient funds.
					}
					try {
						wallet.commitTx(request.tx);
					} catch (VerificationException e) {
						throw new RuntimeException(e.getMessage(), e);
					}
					Futures.addCallback(peerGroup.broadcastTransaction(request.tx), new FutureCallback<Transaction>() {
						@Override
						public void onFailure(Throwable t) {
							t.printStackTrace();
						}

						@Override
						public void onSuccess(Transaction result) {
							System.out.println("SENT " + value + "!!!");
						}
					}, MoreExecutors.sameThreadExecutor());
				}
			}
		};
		send.start();
		send.join();
		Thread.sleep(20000);
	}
}
