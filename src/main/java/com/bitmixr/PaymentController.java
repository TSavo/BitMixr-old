package com.bitmixr;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javassist.NotFoundException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.MemoryFullPrunedBlockStore;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

@Controller
@RequestMapping(value = "/")
public class PaymentController extends DefaultExceptionHandler {

	@PersistenceContext
	protected EntityManager entityManager;

	BlockStore blockStore;
	BlockChain chain;
	PeerGroup peerGroup;
	NetworkParameters params = NetworkParameters.prodNet();

	Map<String, WalletActor> actors = new HashMap<>();
	ReentrantReadWriteLock actorsLock = new ReentrantReadWriteLock();
	ReentrantLock lock = new ReentrantLock();

	AtomicBoolean started = new AtomicBoolean(false);

	List<NetworkRecieve> networkRecieves = new ArrayList<>();
	ReentrantLock networkRecieveslock = new ReentrantLock();

	ExecutorService executor = Executors.newCachedThreadPool();

	public void init() throws BlockStoreException {
		blockStore = new MemoryFullPrunedBlockStore(params, Integer.MAX_VALUE);
		System.out.println("Connecting ...");
		chain = new BlockChain(params, blockStore);
		peerGroup = new PeerGroup(params, chain);
		peerGroup.setUserAgent("BitMixr", "1.0");
		peerGroup.addPeerDiscovery(new DnsDiscovery(params));
	}

	@Transactional
	public void load() {
		actorsLock.writeLock().lock();
		Iterables.transform(entityManager.createQuery("from Payment", Payment.class).getResultList(), new Function<Payment, WalletActor>() {
			@Override
			public WalletActor apply(Payment input) {
				Wallet wallet = new Wallet(params);
				ECKey key = input.getECKey();
				System.out.println("Address is " + key.toAddress(params).toString() + ", Private Key is: " + key.getPrivateKeyEncoded(params).toString());
				wallet.addKey(key);
				peerGroup.addWallet(wallet);
				chain.addWallet(wallet);
				WalletActor actor = new WalletActor(wallet, input.getId());
				actors.put(input.getId(), actor);
				return actor;
			}
		});
		actorsLock.writeLock().unlock();

	}

	public void startUp() {
		peerGroup.startAndWait();
		peerGroup.waitForPeers(2);
		peerGroup.downloadBlockChain();
	}

	@Scheduled(fixedDelay = 20000)
	@Transactional
	public void checkSeenTransactions() throws BlockStoreException {
		lock.lock();
		if (!started.get()) {
			init();
			load();
			startUp();
			started.set(true);
		}
		actorsLock.readLock().lock();
		for (WalletActor actor : actors.values()) {
			actor.seenTransactionLock.writeLock().lock();
			for (Iterator<SeenTransaction> i = actor.seenTransactions.iterator(); i.hasNext();) {
				SeenTransaction seenTransaction = i.next();
				if (seenTransaction.dirty.get()) {
					Payment payment = entityManager.find(Payment.class, actor.paymentId);
					try {
						entityManager.createQuery("from SeenTransaction where transactionHash = ? and payment = ?", SeenTransaction.class).setParameter(1, seenTransaction.getTransactionHash()).setParameter(2, payment).getSingleResult();
					} catch (Exception e) {
						seenTransaction.setPayment(payment);
						entityManager.persist(seenTransaction);
						payment.seenTransactions.add(seenTransaction);
						payment.setRecievedAmount(payment.getRecievedAmount().add(seenTransaction.amount));
						entityManager.merge(payment);
					}
					i.remove();
				}
			}
			actor.seenTransactionLock.writeLock().unlock();
		}
		actorsLock.readLock().unlock();
		lock.unlock();
	}

	@Transactional
	@Scheduled(fixedDelay = 20000)
	public void payOut() {
		lock.lock();
		List<Payment> paymentsNeedingSending = entityManager.createQuery("from Payment where sentAmount < recievedAmount", Payment.class).getResultList();
		for (final Payment payee : paymentsNeedingSending) {
			if (payee.getUpdatedOn() != null && payee.getUpdatedOn().getTime() > System.currentTimeMillis() - 30000) {
				continue;
			}
			// (sent - spent) = available
			// available > 0.01 && ! this payment

			List<Payment> paymentsAvailableForSending = entityManager.createQuery("from Payment where recievedAmount - spentAmount < 10000000 and id != ?", Payment.class).setParameter(1, payee).getResultList();
			if (paymentsAvailableForSending.size() < 1) {
				continue;
			}
			Collections.shuffle(paymentsAvailableForSending);
			Payment tempPayer = null;
			for (Payment p : paymentsAvailableForSending) {
				if (p.getUpdatedOn() != null && p.getUpdatedOn().getTime() > System.currentTimeMillis() - 30000) {
					continue;
				}
				tempPayer = p;
				break;
			}
			if (tempPayer == null) {
				continue;
			}
			final Payment payer = tempPayer;
			BigInteger amountAvailableForSending = payer.getRecievedAmount().subtract(payer.getSpentAmount());
			BigInteger amountNeedingToBeSent = payee.getRecievedAmount().subtract(payee.getSentAmount());
			final BigInteger amountToSend;

			if (amountAvailableForSending.compareTo(amountNeedingToBeSent) == 0) {
				amountToSend = amountAvailableForSending;
			} else if (amountAvailableForSending.compareTo(amountNeedingToBeSent) > 0) {
				amountToSend = amountNeedingToBeSent;
			} else {
				amountToSend = amountAvailableForSending;
			}

			// Now send the coins back!
			final Address from;
			try {
				from = new Address(params, payee.getDestinationAddress());
			} catch (AddressFormatException e1) {
				throw new RuntimeException(e1.getMessage(), e1);
			}

			SendRequest request = Wallet.SendRequest.to(from, amountToSend);
			actorsLock.readLock().lock();
			Wallet wallet = actors.get(payer.getId()).wallet;
			actorsLock.readLock().unlock();
			if (!wallet.completeTx(request))
				return; // Insufficient funds.
			// Ensure these funds won't be spent
			// again.
			try {
				wallet.commitTx(request.tx);
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
					networkRecieveslock.lock();
					networkRecieves.add(new NetworkRecieve(payer.getId(), payee.getId(), amountToSend));
					networkRecieveslock.unlock();
				}
			}, executor);
			payer.setUpdatedOn(new Date());
			payee.setUpdatedOn(new Date());
			entityManager.merge(payer);
			entityManager.merge(payee);
		}
		lock.unlock();
	}

	@Scheduled(fixedDelay = 1000)
	@Transactional
	public void commitSends() {
		lock.lock();
		networkRecieveslock.lock();
		for (NetworkRecieve send : networkRecieves) {
			Payment payer = entityManager.find(Payment.class, send.getPayerId());
			Payment payee = entityManager.find(Payment.class, send.getPayeeId());
			payer.setSpentAmount(payer.getSpentAmount().add(send.getAmountSpent()));
			payee.setSentAmount(payee.getSentAmount().add(send.getAmountSpent()));
			payer.setUpdatedOn(null);
			payee.setUpdatedOn(null);
			payer = entityManager.merge(payer);
			payee = entityManager.merge(payee);
		}
		networkRecieves.clear();
		networkRecieveslock.unlock();
		lock.unlock();
	}

	@RequestMapping(method = RequestMethod.POST)
	@Transactional
	public @ResponseBody
	Payment add(@RequestBody final Payment aPayment) {
		if (!started.get()) {
			// take the easy road home jack
			return null;
		}
		aPayment.setSentAmount(BigInteger.ZERO);
		aPayment.setRecievedAmount(BigInteger.ZERO);
		ECKey key = new ECKey();
		aPayment.setECKey(key);
		aPayment.setSourceAddress(key.toAddress(params).toString());
		Wallet wallet = new Wallet(params);
		wallet.addKey(key);
		WalletActor actor = new WalletActor(wallet, aPayment.getId());
		actorsLock.writeLock().lock();
		actors.put(aPayment.getId(), actor);
		actorsLock.writeLock().unlock();
		chain.addWallet(wallet);
		peerGroup.addWallet(wallet);
		System.out.println("Address is " + key.toAddress(params).toString() + ", Private Key is: " + key.getPrivateKeyEncoded(params).toString());
		entityManager.persist(aPayment);
		return aPayment;
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@Transactional
	public @ResponseBody
	Payment show(@PathVariable final String id) throws NotFoundException {
		Payment payment;
		try {
			payment = entityManager.find(Payment.class, id);
		} catch (Exception e) {
			throw new NotFoundException("ID not found.");
		}
		if (!payment.isVisible()) {
			throw new NotFoundException("ID not found.");
		}
		return payment;
	}
}
