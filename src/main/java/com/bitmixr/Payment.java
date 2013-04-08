package com.bitmixr;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.bitcoin.core.ECKey;

@JsonInclude(Include.NON_NULL)
@Entity
public class Payment implements Serializable {

	@Id
	private String id = UUID.randomUUID().toString();
	private String sourceAddress = null;
	private String destinationAddress = null;
	private BigInteger recievedAmount = BigInteger.ZERO;
	private BigInteger sentAmount = BigInteger.ZERO;
	private BigInteger spentAmount = BigInteger.ZERO;
	@Temporal(TemporalType.TIMESTAMP)
	private Date createdOn = new Date();
	@Temporal(TemporalType.TIMESTAMP)
	private Date updatedOn;
	public Date getUpdatedOn() {
		return updatedOn;
	}

	private boolean visible = true;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "payment", fetch = FetchType.EAGER, orphanRemoval = true)
	Set<SeenTransaction> seenTransactions = new HashSet<>();

	@JsonIgnore
	@Lob
	@Basic(fetch = FetchType.EAGER)
	@Column(name = "privateKey", columnDefinition = "LONGBLOB")
	byte[] privateKey;

	@JsonIgnore
	@Lob
	@Basic(fetch = FetchType.EAGER)
	@Column(name = "publicKey", columnDefinition = "LONGBLOB")
	byte[] publicKey;

	public void setSentAmount(BigInteger sentAmount) {
		this.sentAmount = sentAmount;
	}

	public BigInteger getSentAmount() {
		return sentAmount;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean isVisible) {
		this.visible = isVisible;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public BigInteger getRecievedAmount() {
		return recievedAmount;
	}

	public void setRecievedAmount(BigInteger amount) {
		this.recievedAmount = amount;
	}

	public String getSourceAddress() {
		return sourceAddress;
	}

	public void setSourceAddress(String address) {
		this.sourceAddress = address;
	}

	public String getDestinationAddress() {
		return destinationAddress;
	}

	public void setDestinationAddress(String destinationAddress) {
		this.destinationAddress = destinationAddress;
	}

	public void setECKey(ECKey aKey) {
		privateKey = Arrays.copyOf(aKey.getPrivKeyBytes(), aKey.getPrivKeyBytes().length);
		publicKey = Arrays.copyOf(aKey.getPubKey(), aKey.getPubKey().length);
	}

	public ECKey getECKey() {
		return new ECKey(Arrays.copyOf(privateKey, privateKey.length), Arrays.copyOf(publicKey, publicKey.length));
	}

	public Set<SeenTransaction> getSeenTransactions() {
		return seenTransactions;
	}

	public void setSeenTransactions(Set<SeenTransaction> seenTransactions) {
		this.seenTransactions = seenTransactions;
	}

	public byte[] getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(byte[] privateKey) {
		this.privateKey = privateKey;
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(byte[] publicKey) {
		this.publicKey = publicKey;
	}

	public Date getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	public BigInteger getSpentAmount() {
		return spentAmount;
	}

	public void setSpentAmount(BigInteger spentAmount) {
		this.spentAmount = spentAmount;
	}

	public void setUpdatedOn(Date date) {
		updatedOn = date;
	}
}
