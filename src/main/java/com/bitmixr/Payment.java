package com.bitmixr;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
@Entity
public class Payment implements Serializable {

	@Id
	private String id = UUID.randomUUID().toString();
	private String sourceAddress = null;
	private String destinationAddress = null;
	private Double recievedAmount = null;
	private Double sentAmount = null;
	private boolean visible = true;

	public void setSentAmount(Double sentAmount) {
		this.sentAmount = sentAmount;
	}

	public Double getSentAmount() {
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

	public Double getRecievedAmount() {
		return recievedAmount;
	}

	public void setRecievedAmount(Double amount) {
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
}
