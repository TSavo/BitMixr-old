package com.bitmixr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.UUID;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Lob;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletStorage;

@JsonInclude(Include.NON_NULL)
@Entity
public class Payment implements Serializable {

	@Id
	private String id = UUID.randomUUID().toString();
	private String sourceAddress = null;
	private String destinationAddress = null;
	private BigInteger recievedAmount = null;
	private BigInteger sentAmount = null;
	private boolean visible = true;
	@JsonIgnore
	@Lob
	@Basic(fetch=FetchType.EAGER)
	@Column(name="wallet", columnDefinition="LONGBLOB")
	private byte[] walletBytes;

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

	public byte[] getWalletBytes() {
		return walletBytes;
	}

	public void setWalletBytes(byte[] walletBytes) {
		this.walletBytes = walletBytes;
	}
	
	public Wallet getWallet() throws IOException{
		return WalletStorage.loadFromStream(new ByteArrayInputStream(walletBytes));
	}
	
	public void setWallet(Wallet aWallet) throws IOException{
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		WalletStorage.saveToStream(aWallet, stream);
		setWalletBytes(stream.toByteArray());
	}
}
