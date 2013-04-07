CREATE TABLE Payment (
  id varchar(255) NOT NULL,
  wallet longblob,
  sourceAddress varchar(255) DEFAULT NULL,
  destinationAddress varchar(255) DEFAULT NULL,
  recievedAmount decimal(19,2) DEFAULT NULL,
  sentAmount decimal(19,2) DEFAULT NULL,
  visible bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 |